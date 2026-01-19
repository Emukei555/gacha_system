package com.yourcompany.domain.model.wallet;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.result.Result;

/**
 * 通貨（石）の量を表す Value Object
 * - 不変条件: 値は常に0以上であること
 * - 副作用: なし（計算は常に新しいインスタンスを返す）
 */
public record Money(int amount) {

    // 1. コンパクトコンストラクタ (不変条件の最終防衛ライン)
    // ここは「バグ」レベルの不正を防ぐために例外のままで良い（通常はFactory経由で呼ばれるため）
    public Money {
        if (amount < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + amount);
        }
    }

    // --- ファクトリメソッド ---

    /**
     * 安全にMoneyを生成する。負の値が渡された場合はFailureを返す。
     */
    public static Result<Money> of(int amount) {
        if (amount < 0) {
            return Result.failure(GachaErrorCode.INVALID_PARAMETER, "金額は0以上である必要があります");
        }
        return Result.success(new Money(amount));
    }

    public static Money zero() {
        return new Money(0);
    }

    // --- ドメインロジック（計算） ---

    /**
     * 加算処理 (Result版)
     * オーバーフロー時は Failure を返す
     */
    public Result<Money> add(Money other) {
        // オーバーフローチェック
        if ((long) this.amount + other.amount > Integer.MAX_VALUE) {
            // システムエラー、またはビジネス的な上限エラーとして返す
            return Result.failure(GachaErrorCode.INTERNAL_ERROR, "所持上限を超えています (Overflow)");
        }
        return Result.success(new Money(this.amount + other.amount));
    }

    /**
     * 減算処理 (Result版)
     * マイナスになる場合は Failure を返す（残高不足）
     */
    public Result<Money> subtract(Money other) {
        if (this.amount < other.amount) {
            // 明示的に「残高不足」のエラーを返す
            return Result.failure(GachaErrorCode.INSUFFICIENT_BALANCE);
        }
        return Result.success(new Money(this.amount - other.amount));
    }

    // --- 判定メソッド (これらは失敗しないので boolean / Money のままでOK) ---

    public boolean isGreaterThanOrEqual(Money other) {
        return this.amount >= other.amount;
    }

    public boolean isZero() {
        return this.amount == 0;
    }

    public boolean isLessThan(Money other) {
        return this.amount < other.amount;
    }

    public Money min(Money other) {
        return this.amount <= other.amount ? this : other;
    }
}