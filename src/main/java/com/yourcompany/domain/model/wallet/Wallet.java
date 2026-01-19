package com.yourcompany.domain.model.wallet;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.result.Result;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * ユーザーの通貨（石）を管理する集約ルート (Aggregate Root)。
 * <p>
 * 有償石・無償石の残高管理、消費優先順位（有償優先）のロジック、
 * および操作ログのトレーサビリティ（MDC）を担当します。
 * </p>
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
     *
     * @param userId ユーザーID
     * @return 初期状態（残高0）の {@link Wallet}
     */
    public static Wallet create(UUID userId) {
        return new Wallet(userId, 0, 0);
    }

    /**
     * 石を消費します (Railway Oriented Style)。
     * <p>
     * 以下のステップで処理を実行します：
     * <ol>
     * <li>引数チェック (正の数であるか)</li>
     * <li>残高チェック (不足していないか)</li>
     * <li>消費ロジック実行 (有償石 -> 無償石の順に消費)</li>
     * <li>ログ出力 (成功/失敗)</li>
     * </ol>
     * 処理中は {@link MDC} にコンテキスト情報を設定し、ログの追跡性を確保します。
     * </p>
     *
     * @param amount 消費する石の量
     * @return 処理結果。成功時は更新後の {@link Wallet}、失敗時はエラー情報を含む {@link Result}
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
                    // 失敗時のログ：エラー内容をWARNで記録
                    .tapFailure(failure -> log.warn("Consume failed. code={}, message={}",
                            failure.errorCode(), failure.message()));
        } finally {
            clearMDC();
        }
    }

    // --- Private Helper Methods for ROP ---

    /**
     * ガード節: 消費量が正の数であることを検証します。
     *
     * @param amount 検証対象の量
     * @return 検証結果
     */
    private Result<Integer> validateAmountPositive(int amount) {
        if (amount < 0) {
            log.warn("Validation failed: Amount must be positive. input={}", amount);
            return Result.failure(GachaErrorCode.INVALID_PARAMETER, "消費量は正の数である必要があります");
        }
        return Result.success(amount);
    }

    /**
     * ガード節: 残高が消費量に対して十分であることを検証します。
     *
     * @param amount 消費量
     * @return 検証結果
     */
    private Result<Integer> validateBalanceSufficient(int amount) {
        long total = getTotalStones();
        if (total < (long) amount) {
            log.warn("Validation failed: Insufficient balance. total={}, required={}", total, amount);
            return Result.failure(GachaErrorCode.INSUFFICIENT_BALANCE);
        }
        return Result.success(amount);
    }

    /**
     * 実際の消費ロジックを実行し、内部状態を更新します。
     * <p>
     * バリデーション通過後に呼び出される前提のため、エラーチェックは行いません。
     * 有償石を優先して消費し、不足分を無償石から消費します。
     * </p>
     *
     * @param amount 消費量
     * @return 更新された {@link Wallet} (this)
     */
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
     * <p>
     * 有償石と無償石を個別に指定して追加します。所持上限チェックを行い、
     * 上限を超える場合はエラーを返します。
     * </p>
     *
     * @param paidAmount 付与する有償石の量
     * @param freeAmount 付与する無償石の量
     * @return 処理結果
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
                    // 失敗時のログ
                    .tapFailure(failure -> log.warn("Deposit failed. code={}, message={}",
                            failure.errorCode(), failure.message()));
        } finally {
            clearMDC();
        }
    }

    /**
     * ガード節: 付与後の合計が所持上限（Integer.MAX_VALUE）を超えないか検証します。
     *
     * @param paidAdd 有償石の追加量
     * @param freeAdd 無償石の追加量
     * @return 検証結果
     */
    private Result<Void> validateCapacity(int paidAdd, int freeAdd) {
        if ((long)this.paidStones + paidAdd > Integer.MAX_VALUE ||
                (long)this.freeStones + freeAdd > Integer.MAX_VALUE) {
            log.error("Validation failed: Capacity overflow. currentPaid={}, addPaid={}, currentFree={}, addFree={}",
                    paidStones, paidAdd, freeStones, freeAdd);
            return Result.failure(GachaErrorCode.INVENTORY_OVERFLOW, "所持上限を超えます");
        }
        return Result.success(null);
    }

    /**
     * 現在の合計所持石数（有償＋無償）を取得します。
     *
     * @return 合計石数
     */
    public long getTotalStones() {
        return (long) paidStones + freeStones;
    }

    // --- MDC Helper Methods ---

    /**
     * MDC (Mapped Diagnostic Context) にログ追跡用の情報を設定します。
     *
     * @param operation 操作名
     */
    private void setupMDC(String operation) {
        MDC.put(MDC_KEY_USER_ID, this.userId.toString());
        MDC.put(MDC_KEY_OPERATION, operation);
    }

    /**
     * MDC から情報を削除し、スレッドプールの汚染を防ぎます。
     */
    private void clearMDC() {
        MDC.remove(MDC_KEY_USER_ID);
        MDC.remove(MDC_KEY_OPERATION);
    }
}