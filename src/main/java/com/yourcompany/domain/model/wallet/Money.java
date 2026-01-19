package com.yourcompany.domain.model.wallet;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.result.Result;
import jakarta.persistence.Embeddable;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;

/**
 * 通貨（石）の量を表す Value Object
 * - 不変条件: 値は常に0以上であること
 * - 副作用: なし（計算は常に新しいインスタンスを返す）
 */
@Embeddable
@Slf4j
public record Money(int amount) implements Serializable {

    // 1. コンパクトコンストラクタ (不変条件の最終防衛ライン)
    public Money {
        if (amount < 0) {
            // ここは開発時のバグ検知用なので例外で落とす
            throw new IllegalArgumentException("Money cannot be negative: " + amount);
        }
    }

    // --- ファクトリメソッド ---

    /**
     * 安全にMoneyを生成する。負の値が渡された場合はFailureを返す。
     */
    public static Result<Money> of(int amount) {
        if (amount < 0) {
            log.warn("Money creation failed. Negative amount: {}", amount);
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
        // オーバーフローチェック (longで計算して比較)
        if ((long) this.amount + other.amount > Integer.MAX_VALUE) {
            log.warn("Money overflow detected: {} + {}", this.amount, other.amount);
            // WalletTestに合わせて INVENTORY_OVERFLOW を返す
            return Result.failure(GachaErrorCode.INVENTORY_OVERFLOW, "所持上限を超えています (Overflow)");
        }
        return Result.success(new Money(this.amount + other.amount));
    }

    /**
     * 減算処理 (Result版)
     * マイナスになる場合は Failure を返す（残高不足）
     */
    public Result<Money> subtract(Money other) {
        if (this.amount < other.amount) {
            log.warn("Money subtraction failed (Insufficient). Current: {}, Reduce: {}", this.amount, other.amount);
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

    // toStringはRecord標準のもので十分ですが、必要ならログ用にオーバーライドしても可
}