package com.yourcompany.schoolasset.application.service;

import com.sqlcanvas.sharedkernel.shared.result.Result;
import com.sqlcanvas.sharedkernel.shared.util.RequestId; // ★新しいRequestId
import com.yourcompany.domain.model.gacha.*;
import com.yourcompany.domain.model.gacha.event.GachaDrawnEvent;
import com.yourcompany.domain.model.wallet.Wallet;
import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.schoolasset.infrastructure.persistence.repository.GachaPoolRepository;
import com.yourcompany.schoolasset.infrastructure.persistence.repository.GachaStateRepository;
import com.yourcompany.schoolasset.infrastructure.persistence.repository.WalletRepository;
import com.yourcompany.web.dto.GachaDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GachaService {

    private final WalletRepository walletRepository;
    private final GachaPoolRepository poolRepository;
    private final GachaStateRepository stateRepository;
    private final LotteryService lotteryService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Result<GachaDto.DrawResponse> drawGacha(UUID userId, GachaDto.DrawRequest request) {
        // 1. プール情報取得 & 期間チェック
        GachaPool pool = poolRepository.findByIdWithEmissions(request.poolId()).orElse(null);
        if (pool == null || !pool.isOpen()) {
            return Result.failure(GachaErrorCode.GACHA_POOL_EXPIRED);
        }

        // 2. ウォレット取得 (悲観ロック)
        // 見つからない場合は Failure を返す (例外ではなく結果として扱う)
        var walletOpt = walletRepository.findByIdWithLock(userId);
        if (walletOpt.isEmpty()) {
            return Result.failure(GachaErrorCode.WALLET_NOT_FOUND);
        }
        Wallet wallet = walletOpt.get();

        // 3. コスト消費
        int totalCost = pool.getCostAmount() * request.drawCount();
        long snapshotPaid = wallet.getPaidStones();
        long snapshotFree = wallet.getFreeStones();

        Result<Wallet> consumeResult = wallet.consume(totalCost);

        // Java 21 Pattern Matching for switch (Resultの中身を検査)
        if (consumeResult instanceof Result.Failure<Wallet> f) {
            markRollback(); // 業務エラーでもDB更新を防ぐためロールバックマーク
            return Result.failure(f.errorCode(), f.message());
        }

        // 4. ガチャ状態取得
        GachaState gachaState = stateRepository.findByUserAndPool(userId, pool.getId())
                .orElseGet(() -> GachaState.create(userId, pool.getId()));

        // 5. 抽選ループ
        List<GachaDto.EmissionItem> responseItems = new ArrayList<>();
        List<EmissionResult> eventDetails = new ArrayList<>();

        for (int i = 0; i < request.drawCount(); i++) {
            // A. 抽選
            Result<GachaEmission> drawResult = lotteryService.draw(pool.getEmissions());

            GachaEmission emission;
            if (drawResult instanceof Result.Success<GachaEmission>(var val)) {
                emission = val;
            } else if (drawResult instanceof Result.Failure<GachaEmission> f) {
                markRollback();
                return Result.failure(f.errorCode(), f.message());
            } else {
                throw new IllegalStateException("Unknown Result type");
            }

            // B. 状態更新 (SSR判定などの簡易ロジック)
            // TODO: マスタからRarityを取得する処理は本来CacheServiceなどに切り出す
            boolean isSsr = false;

            Result<GachaState> stateResult = gachaState.updateState(isSsr, pool);
            if (stateResult instanceof Result.Failure<GachaState> f) {
                markRollback();
                return Result.failure(f.errorCode(), f.message());
            }

            // 結果リスト構築
            responseItems.add(new GachaDto.EmissionItem(
                    emission.getItemId(), "ItemName", "R", false, 1
            ));
            eventDetails.add(new EmissionResult(
                    emission.getItemId(), "ItemName", "R", emission.isPickup(), EmissionResult.EmissionType.NORMAL
            ));
        }

        // 6. 永続化 (Aggregate Roots)
        walletRepository.save(wallet);
        stateRepository.save(gachaState);

        // 7. ドメインイベント発行 (副作用を委譲)
        RequestId requestId = RequestId.generate();
        GachaDrawnEvent event = new GachaDrawnEvent(
                requestId,
                userId,
                pool.getId(),
                (int) (snapshotPaid - wallet.getPaidStones()),
                (int) (snapshotFree - wallet.getFreeStones()),
                eventDetails
        );
        eventPublisher.publishEvent(event);

        return Result.success(new GachaDto.DrawResponse(
                requestId.toString(),
                event.consumedPaid(),
                event.consumedFree(),
                responseItems
        ));
    }

    /**
     * トランザクションをロールバック専用にマークする。
     * Result.Failureを返す場合でも、DBへの変更（もしあれば）を確定させないために呼ぶ。
     */
    private void markRollback() {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }
}