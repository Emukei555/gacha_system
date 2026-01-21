package com.yourcompany.domain.model.wallet;

import com.sqlcanvas.sharedkernel.shared.error.CommonErrorCode;
import com.sqlcanvas.sharedkernel.shared.result.Result;
import com.yourcompany.domain.shared.exception.GachaErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * ユーザーの通貨（石）を管理する集約ルート (Aggregate Root)。
 */
@Entity
@Table(name = "wallets")
@Getter
@Slf4j
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    private static final String MDC_KEY_USER_ID = "userId";
    private static final String MDC_KEY_OPERATION = "operation";

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "paid_stones", nullable = false)
    private int paidStones;

    @Column(name = "free_stones", nullable = false)
    private int freeStones;

    @Version
    private long version;

    private Wallet(UUID userId, int paidStones, int freeStones) {
        this.userId = userId;
        this.paidStones = paidStones;
        this.freeStones = freeStones;
    }

    /**
     * 新規ウォレットを作成します。
     */
    public static Wallet create(UUID userId) {
        return new Wallet(userId, 0, 0);
    }

    /**
     * 石を消費します (Railway Oriented Style)。
     */
    public Result<Wallet> consume(int amount) {
        setupMDC("consume");

        try {
            return validateAmountPositive(amount)                    // 1. 引数チェック
                    .flatMap(this::validateBalanceSufficient)        // 2. 残高チェック
                    .tap(amt -> log.debug("Consuming stones. amount={}", amt)) // 3. 実行前ログ
                    .map(this::executeConsumeLogic)                  // 4. 計算と状態更新
                    .tap(wallet -> log.info("Wallet updated. consumed={}, newPaid={}, newFree={}",
                            amount, wallet.paidStones, wallet.freeStones)) // 5. 完了ログ
                    // 失敗時のログ
                    .tapFailure(failure -> log.warn("Consume failed. code={}, message={}",
                            failure.errorCode(), failure.message()));
        } finally {
            clearMDC();
        }
    }

    // --- Private Helper Methods for ROP ---

    private Result<Integer> validateAmountPositive(int amount) {
        if (amount < 0) {
            log.warn("Validation failed: Amount must be positive. input={}", amount);
            // 修正: CommonErrorCode を使用
            return Result.failure(CommonErrorCode.INVALID_PARAMETER, "消費量は正の数である必要があります");
        }
        return Result.success(amount);
    }

    private Result<Integer> validateBalanceSufficient(int amount) {
        long total = getTotalStones();
        if (total < (long) amount) {
            log.warn("Validation failed: Insufficient balance. total={}, required={}", total, amount);
            // 修正: GachaErrorCode を使用 (メッセージはデフォルトでOK)
            return Result.failure(GachaErrorCode.INSUFFICIENT_BALANCE);
        }
        return Result.success(amount);
    }

    private Wallet executeConsumeLogic(int amount) {
        int remaining = amount;
        int paidConsume = Math.min(this.paidStones, remaining);
        this.paidStones -= paidConsume;
        remaining -= paidConsume;

        if (remaining > 0) {
            this.freeStones -= remaining;
        }
        return this;
    }

    /**
     * 石を付与します。
     */
    public Result<Wallet> deposit(int paidAmount, int freeAmount) {
        setupMDC("deposit");

        try {
            return validateAmountPositive(paidAmount)
                    .flatMap(ok -> validateAmountPositive(freeAmount))
                    .flatMap(ok -> validateCapacity(paidAmount, freeAmount))
                    .tap(ok -> log.debug("Depositing stones. paid={}, free={}", paidAmount, freeAmount))
                    .map(ok -> {
                        this.paidStones += paidAmount;
                        this.freeStones += freeAmount;
                        return this;
                    })
                    .tap(wallet -> log.info("Wallet deposited. addPaid={}, addFree={}, total={}",
                            paidAmount, freeAmount, getTotalStones()))
                    .tapFailure(failure -> log.warn("Deposit failed. code={}, message={}",
                            failure.errorCode(), failure.message()));
        } finally {
            clearMDC();
        }
    }

    private Result<Void> validateCapacity(int paidAdd, int freeAdd) {
        if ((long)this.paidStones + paidAdd > Integer.MAX_VALUE ||
                (long)this.freeStones + freeAdd > Integer.MAX_VALUE) {
            log.error("Validation failed: Capacity overflow. currentPaid={}, addPaid={}, currentFree={}, addFree={}",
                    paidStones, paidAdd, freeStones, freeAdd);
            // 修正: GachaErrorCode を使用
            return Result.failure(GachaErrorCode.INVENTORY_OVERFLOW, "所持上限を超えます");
        }
        return Result.success(null);
    }

    public long getTotalStones() {
        return (long) paidStones + freeStones;
    }

    // --- MDC Helper Methods ---

    private void setupMDC(String operation) {
        MDC.put(MDC_KEY_USER_ID, this.userId.toString());
        MDC.put(MDC_KEY_OPERATION, operation);
    }

    private void clearMDC() {
        MDC.remove(MDC_KEY_USER_ID);
        MDC.remove(MDC_KEY_OPERATION);
    }
}