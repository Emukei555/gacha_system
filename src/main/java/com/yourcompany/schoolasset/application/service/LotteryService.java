package com.yourcompany.schoolasset.application.service;

import com.sqlcanvas.sharedkernel.shared.result.Result;
import com.yourcompany.domain.model.gacha.WeightedItem;
import com.yourcompany.domain.shared.exception.GachaErrorCode;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.random.RandomGenerator;

@Service
public class LotteryService {

    private final RandomGenerator random = new SecureRandom();

    /**
     * 重みに基づいてアイテムを1つ抽選する
     */
    public <T extends WeightedItem> Result<T> draw(List<T> items) {
        if (items == null || items.isEmpty()) {
            // 修正: INTERNAL_ERROR (廃止) -> INVALID_WEIGHT_CONFIG (設定ミス) に変更
            // かつ Result.failure ファクトリメソッドを使用
            return Result.failure(GachaErrorCode.INVALID_WEIGHT_CONFIG, "排出対象リストが空です");
        }

        // 1. 重みの合計を計算
        long totalWeight = 0;
        for (T item : items) {
            if (item.getWeight() <= 0) {
                // 修正: Result.failure を使用
                return Result.failure(GachaErrorCode.INVALID_WEIGHT_CONFIG, "重みが0以下のアイテムが含まれています");
            }
            totalWeight += item.getWeight();
        }

        if (totalWeight == 0) {
            // 修正: Result.failure を使用
            return Result.failure(GachaErrorCode.INVALID_WEIGHT_CONFIG, "合計ウェイトが0です");
        }

        // 2. 0 〜 (totalWeight - 1) の乱数を生成
        long randomValue = random.nextLong(totalWeight);

        // 3. 累積減算方式で当選判定
        for (T item : items) {
            if (randomValue < item.getWeight()) {
                // 当選！
                return Result.success(item);
            }
            // 次のアイテムへ枠をずらす
            randomValue -= item.getWeight();
        }

        // ここに来ることは論理的にありえない
        // 修正: Result.failure を使用
        return Result.failure(GachaErrorCode.UNEXPECTED_ERROR, "抽選ロジックが破綻しました");
    }
}