package com.yourcompany.features.gacha.draw;

import com.yourcompany.domain.model.gacha.*;
import com.yourcompany.domain.model.gacha.event.GachaDrawnEvent;
import com.yourcompany.domain.model.wallet.Wallet;
import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.exception.GachaException;
import com.sqlcanvas.sharedkernel.shared.result.Result;
import com.yourcompany.schoolasset.infrastructure.persistence.repository.*; // パッケージ移動後は修正が必要
import com.yourcompany.schoolasset.application.service.LotteryService; // 共通サービスとして残すか、ここに移すか検討
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.yourcompany.domain.shared.value.RequestId;
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
public class DrawGachaUseCase {

    private final WalletRepository walletRepository;
    private final GachaPoolRepository poolRepository;
    private final GachaStateRepository stateRepository;
    private final LotteryService lotteryService; // 抽選ロジックはドメインサービスとして共有
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Result<DrawGachaResponse> execute(UUID userId, DrawGachaRequest request) {
        // 1. プール情報取得
        GachaPool pool = poolRepository.findByIdWithEmissions(request.poolId()).orElse(null);
        if (pool == null || !pool.isOpen()) {
            return GachaErrorCode.GACHA_POOL_EXPIRED.toFailure();
        }

        // 2. ウォレット取得 (悲観ロック)
        Wallet wallet = walletRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new GachaException(GachaErrorCode.WALLET_NOT_FOUND));

        // 3. コスト消費
        int totalCost = pool.getCostAmount() * request.drawCount();
        long snapshotPaid = wallet.getPaidStones();
        long snapshotFree = wallet.getFreeStones();

        Result<Wallet> consumeResult = wallet.consume(totalCost);
        if (consumeResult instanceof Result.Failure<Wallet> f) {
            markRollback();
            return Result.failure(f.errorCode(), f.message());
        }

        // 4. ガチャ状態取得
        GachaState gachaState = stateRepository.findByUserAndPool(userId, pool.getId())
                .orElseGet(() -> GachaState.create(userId, pool.getId()));

        // 5. 抽選ループ
        List<DrawGachaResponse.EmissionItem> responseItems = new ArrayList<>();
        List<EmissionResult> eventDetails = new ArrayList<>();

        for (int i = 0; i < request.drawCount(); i++) {
            // A. 抽選
            Result<GachaEmission> drawResult = lotteryService.draw(pool.getEmissions());
            if (drawResult instanceof Result.Failure<GachaEmission> f) {
                markRollback();
                return Result.failure(f.errorCode(), f.message());
            }
            GachaEmission emission = ((Result.Success<GachaEmission>) drawResult).value();

            // B. 状態更新
            boolean isSsr = false; // TODO: Item情報を取得して判定
            Result<GachaState> stateResult = gachaState.updateState(isSsr, pool);
            if (stateResult instanceof Result.Failure<GachaState> f) {
                markRollback();
                return Result.failure(f.errorCode(), f.message());
            }

            // DTO詰め替え
            responseItems.add(new DrawGachaResponse.EmissionItem(
                    emission.getItemId(), "ItemName", "R", false, 1
            ));
            eventDetails.add(new EmissionResult(
                    emission.getItemId(), "ItemName", "R", emission.isPickup(), EmissionResult.EmissionType.NORMAL
            ));
        }

        // 6. 永続化
        walletRepository.save(wallet);
        stateRepository.save(gachaState);

        // 7. イベント発行
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

        return Result.success(new DrawGachaResponse(
                requestId.toString(),
                event.consumedPaid(),
                event.consumedFree(),
                responseItems
        ));
    }

    private void markRollback() {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }
}