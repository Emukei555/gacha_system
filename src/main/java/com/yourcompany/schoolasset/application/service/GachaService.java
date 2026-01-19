//package com.yourcompany.schoolasset.application.service;
//
//import com.yourcompany.domain.model.gacha.GachaEmission;
//import com.yourcompany.domain.model.gacha.GachaPool;
//import com.yourcompany.domain.model.wallet.Wallet;
//import com.yourcompany.domain.service.LotteryService;
//import com.yourcompany.domain.shared.exception.GachaErrorCode;
//import com.yourcompany.domain.shared.exception.GachaException;
//import com.yourcompany.domain.shared.result.Result;
//import com.yourcompany.infrastructure.persistence.repository.WalletRepositor;
//import com.yourcompany.schoolasset.infrastructure.persistence.repository.GachaPoolRepository;
//import com.yourcompany.web.dto.GachaDto;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.UUID;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class GachaService {
//
//    private final WalletRepository walletRepository;
//    private final GachaPoolRepository poolRepository;
//    private final LotteryService lotteryService;
//    // private final UserItemRepository userItemRepository; // Phase 3で使用
//
//    /**
//     * ガチャ実行ユースケース
//     */
//    @Transactional
//    public Result<GachaDto.ExecutionResponse> executeGacha(UUID userId, GachaDto.ExecutionRequest request) {
//        log.info("START Gacha: userId={}, poolId={}", userId, request.poolId());
//
//        // 1. 集約ルートの取得 (JOIN FETCHで排出リストもメモリに乗る)
//        GachaPool pool = poolRepository.findByIdWithEmissions(request.poolId())
//                .orElseThrow(() -> new GachaException(GachaErrorCode.GACHA_POOL_EXPIRED));
//
//        // 2. 期間チェック & 構成チェック (不変条件ガード)
//        if (!pool.isOpen()) {
//            return GachaErrorCode.GACHA_POOL_EXPIRED.toFailure("開催期間外です");
//        }
//        // ここで合計100%チェックなどが走る
//        Result<GachaPool> configCheck = pool.validateConfiguration();
//        if (configCheck instanceof Result.Failure<GachaPool> f) {
//            return f.map(p -> null); // エラー変換
//        }
//
//        // 3. ウォレット取得とロック
//        Wallet wallet = walletRepository.findById(userId)
//                .orElseThrow(() -> new GachaException(GachaErrorCode.WALLET_NOT_FOUND));
//
//        // 4. 一括コスト消費
//        int totalCost = pool.getCostAmount() * request.drawCount();
//        Result<Wallet> walletResult = wallet.consume(totalCost);
//
//        // 失敗ならここで終了（DB更新なし）
//        if (walletResult instanceof Result.Failure<Wallet> f) {
//            return f.map(w -> null);
//        }
//
//        // 5. 抽選ループ (インメモリ処理)
//        List<GachaDto.EmissionDetail> dtoList = new ArrayList<>();
//
//        for (int i = 0; i < request.drawCount(); i++) {
//            // ★ Phase 2 の核心：純粋関数的な抽選ロジック
//            Result<GachaEmission> drawResult = lotteryService.draw(pool.getEmissions());
//
//            if (drawResult instanceof Result.Success<GachaEmission> s) {
//                GachaEmission emission = s.value();
//                // TODO: Phase 3でここで userItemRepository.saveAll などを呼ぶ
//                // TODO: Phase 3でここで天井カウントのリセット判定を入れる
//
//                // DTO作成 (仮実装)
//                dtoList.add(new GachaDto.EmissionDetail(
//                        emission.getItemId(),
//                        "UNKNOWN", // 後でItemMasterから引く
//                        false      // 後で所持チェックする
//                ));
//            } else if (drawResult instanceof Result.Failure<GachaEmission> f) {
//                // 万が一抽選ロジックが破綻した場合
//                throw new GachaException(f.errorCode(), f.message());
//            }
//        }
//
//        // 6. 成功結果の返却
//        return Result.success(new GachaDto.ExecutionResponse(
//                "SUCCESS", totalCost, 0, dtoList
//        ));
//    }
//}