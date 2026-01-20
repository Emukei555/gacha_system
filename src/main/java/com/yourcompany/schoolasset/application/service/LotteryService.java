package com.yourcompany.schoolasset.application.service;

import com.yourcompany.domain.model.gacha.WeightedItem;
import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.result.Result;
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
            return GachaErrorCode.INTERNAL_ERROR.toFailure("排出対象リストが空です");
        }

        // 1. 重みの合計を計算 (オーバーフロー対策でlong推奨だが、仕様によりintでも可)
        long totalWeight = 0;
        for (T item : items) {
            if (item.getWeight() <= 0) {
                return GachaErrorCode.INVALID_WEIGHT_CONFIG.toFailure("重みが0以下のアイテムが含まれています");
            }
            totalWeight += item.getWeight();
        }

        if (totalWeight == 0) {
            return GachaErrorCode.INVALID_WEIGHT_CONFIG.toFailure("合計ウェイトが0です");
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

        // ここに来ることは論理的にありえない（TotalWeightの範囲内で抽選しているため）
        // しかし、堅牢性のためにエラーを返す
        return GachaErrorCode.UNEXPECTED_ERROR.toFailure("抽選ロジックが破綻しました");
    }
}