package com.yourcompany.domain.model.wallet;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.result.Result;

/**
 * 通貨（石）を表す Value Object
 * 責務：
 * 1. 数量が負にならないことを保証する (RPN 300対策)
 * 2. 有償・無償の消費優先順位ロジックを持つ (有償 > 無償)
 * 3. 加算時のオーバーフローを防ぐ
 */
public record Gems(int paid, int free) {

    public static Gems of(int paid, int free) {
        // 【不変条件・ガード】
        // 1. paid < 0 または free < 0 なら、INVALID_PARAMETER (C001) を返す
        //    (RPN対策: 負の残高の発生を物理的に阻止)

        // 2. paid + free が Integer.MAX_VALUE を超える場合、INVENTORY_OVERFLOW (G003) を返す
        //    (RPN対策: オーバーフローによる残高崩壊防止)

        // TODO: 実装
        return null; // ダミー
    }

    public long totalAmount() {
        return (long) paid + free;
    }

    /**
     * 石を消費する（減算ロジック）
     */
    public Result<Gems> subtract(int amount) {
        // 【日本語ロジック】
        // 1. ガード: amount <= 0 ならエラー (消費量は正の数のみ)
        // 2. ガード: totalAmount() < amount なら INSUFFICIENT_BALANCE (G001)

        // 3. 計算ロジック（優先順位の実装）:
        //    - 基本方針: 「有償石」を優先して消費する (User defined policy: Paid > Free)
        //    - int paidConsume = Math.min(this.paid, amount);
        //    - int remaining = amount - paidConsume;
        //    - int freeConsume = remaining;

        // 4. 新しい Gems オブジェクトを作成して Result.success で返す

        // TODO: 実装
        return null;
    }
}