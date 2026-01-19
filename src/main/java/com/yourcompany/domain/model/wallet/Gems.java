package com.yourcompany.domain.model.wallet;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.result.Result;
import lombok.extern.slf4j.Slf4j;

/**
 * 通貨（石）を表す Value Object
 * 責務：
 * 1. 数量が負にならないことを保証する (RPN 300対策)
 * 2. 有償・無償の消費優先順位ロジックを持つ (有償 > 無償)
 * 3. 加算時のオーバーフローを防ぐ
 */
@Slf4j
public record Gems(int paid, int free) {

    /**
     * Gemsを安全に生成する (Factory Method)
     */
    public static Result<Gems> of(int paid, int free) {
        // ガード: 負の残高の発生を物理的に阻止
        if (paid < 0 || free < 0) {
            log.warn("Invalid Gems creation attempt. paid={}, free={}", paid, free);
            return GachaErrorCode.INVALID_PARAMETER.toFailure("残高は負の値にできません");
        }

        // ガード: オーバーフローによる残高崩壊防止
        if ((long) paid + free > Integer.MAX_VALUE) {
            log.warn("Gems overflow detected. paid={}, free={}", paid, free);
            return GachaErrorCode.INVENTORY_OVERFLOW.toFailure("残高が上限値を超えています");
        }

        return Result.success(new Gems(paid, free));
    }

    /**
     * 合計所持数を取得（longで返すことで計算時のオーバーフローを回避）
     */
    public long totalAmount() {
        return (long) paid + free;
    }

    /**
     * 石を消費する（減算ロジック）
     * 優先順位: 有償石 > 無償石
     *
     * @param amount 消費量
     * @return 計算後の新しいGemsインスタンス
     */
    public Result<Gems> subtract(int amount) {
        if (amount <= 0) {
            log.warn("Invalid subtraction amount: {}", amount);
            return GachaErrorCode.INVALID_PARAMETER.toFailure("消費量は正の値である必要があります");
        }

        long currentTotal = totalAmount();
        if (currentTotal < amount) {
            log.warn("Insufficient gems. currentTotal={}, required={}", currentTotal, amount);
            return GachaErrorCode.INSUFFICIENT_BALANCE.toFailure("石が不足しています");
        }

        // 計算ロジック: 有償優先で消費
        int paidConsume = Math.min(this.paid, amount);
        int freeConsume = amount - paidConsume; // 残りを無償から引く

        // ログ: 計算の証跡（DEBUGレベル推奨だが、重要ロジックなのでINFOでも可）
        if (log.isDebugEnabled()) {
            log.debug("Gems subtraction calc: req={}, paidConsume={}, freeConsume={}",
                    amount, paidConsume, freeConsume);
        }

        // Value Object なので、状態を変えずに新しいインスタンスを返す
        return Result.success(new Gems(this.paid - paidConsume, this.free - freeConsume));
    }
}