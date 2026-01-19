package com.yourcompany.domain.model.wallet;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.result.Result;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Entity
@Table(name = "wallets")
@Getter
@Slf4j
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    // 1. intではなくMoney型で持つ。
    // DBのカラム名(paid_stones)とMoneyのフィールド名(amount)が違うため、@AttributeOverrideで紐付ける
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "paid_stones", nullable = false))
    private Money paidMoney;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "free_stones", nullable = false))
    private Money freeMoney;

    @Version
    private long version;

    // コンストラクタ: 生成時は必ず0円で初期化
    private Wallet(UUID userId) {
        this.userId = userId;
        this.paidMoney = Money.zero();
        this.freeMoney = Money.zero();
    }

    public static Wallet create(UUID userId) {
        return new Wallet(userId);
    }

    /**
     * 石を消費する
     * ロジック：有償(Paid) -> 無償(Free) の順に消費
     */
    public Result<Wallet> consume(int amount) {
        // 1. 入力値の型変換とバリデーション
        // Money.ofがResultを返すようになったので、ここでもResultで受ける
        Result<Money> costResult = Money.of(amount);
        if (costResult instanceof Result.Failure<Money> f) {
            return Result.failure(f.errorCode(), f.message());
        }
        // 中身を取り出す（成功確定）
        Money cost = ((Result.Success<Money>) costResult).value();

        // 2. 残高チェック (有償 + 無償)
        // addもResultを返すため、オーバーフロー(Long超え)もここで検知可能
        Result<Money> totalResult = this.paidMoney.add(this.freeMoney);

        switch (totalResult) {
            case Result.Failure<Money> f -> {
                // 合算でオーバーフローなどの異常
                return Result.failure(f.errorCode(), f.message());
            }
            case Result.Success<Money> s -> {
                Money total = s.value();
                if (total.isLessThan(cost)) {
                    // 残高不足: 詳細な不足額を返すことも可能
                    return Result.failure(GachaErrorCode.INSUFFICIENT_BALANCE);
                }
            }
        }

        // ログ: 変更前のスナップショット
        log.debug("Consuming stones. userId={}, currentPaid={}, currentFree={}, amount={}",
                userId, paidMoney.amount(), freeMoney.amount(), amount);

        // 3. 消費ロジック
        // ここまでくれば計算は安全だが、MoneyがResultを返すためハンドリングが必要

        // A. 有償石からの消費分を計算 (minは失敗しないロジックなのでMoney返しでOK)
        Money paidConsumption = this.paidMoney.min(cost);

        // B. 残りの消費分を計算
        // subtractはResultを返すが、ロジック上マイナスにならないことが保証されているため
        // ここで万が一Failureが出たら「システムバグ（あり得ない状態）」として例外を投げて良い
        Money remainingConsumption = unwrapSuccess(cost.subtract(paidConsumption));

        // C. 実際の減算適用
        // 有償石を減らす
        this.paidMoney = unwrapSuccess(this.paidMoney.subtract(paidConsumption));
        // 無償石を減らす
        this.freeMoney = unwrapSuccess(this.freeMoney.subtract(remainingConsumption));

        // ログ: 変更確定
        log.info("Wallet updated. userId={}, consumed={}, newPaid={}, newFree={}",
                userId, amount, this.paidMoney.amount(), this.freeMoney.amount());

        return Result.success(this);
    }

    /**
     * 石を付与する
     */
    public Result<Wallet> deposit(int paidAmount, int freeAmount) {
        // 1. 入力値チェック (Money生成)
        Result<Money> paidInput = Money.of(paidAmount);
        Result<Money> freeInput = Money.of(freeAmount);

        if (paidInput instanceof Result.Failure<Money> f) return Result.failure(f.errorCode(), f.message());
        if (freeInput instanceof Result.Failure<Money> f) return Result.failure(f.errorCode(), f.message());

        Money addPaid = ((Result.Success<Money>) paidInput).value();
        Money addFree = ((Result.Success<Money>) freeInput).value();

        // 2. 加算処理 (Resultによるオーバーフローチェック)
        Result<Money> newPaidResult = this.paidMoney.add(addPaid);
        Result<Money> newFreeResult = this.freeMoney.add(addFree);

        // どちらかで失敗（オーバーフロー）したら失敗を返す
        if (newPaidResult instanceof Result.Failure<Money> f) {
            return Result.failure(GachaErrorCode.INVENTORY_OVERFLOW, "有償石が所持上限を超えます");
        }
        if (newFreeResult instanceof Result.Failure<Money> f) {
            return Result.failure(GachaErrorCode.INVENTORY_OVERFLOW, "無償石が所持上限を超えます");
        }

        // 3. 確定更新
        this.paidMoney = ((Result.Success<Money>) newPaidResult).value();
        this.freeMoney = ((Result.Success<Money>) newFreeResult).value();

        return Result.success(this);
    }

    public long getTotalStones() {
        // ここは計算用なのでlongで返す
        return (long) paidMoney.amount() + freeMoney.amount();
    }

    /**
     * 内部ヘルパー: 論理的に失敗しないはずのResultをアンラップする
     * 万が一失敗した場合は、ドメインの不整合（バグ）なので実行時例外を投げる
     */
    private Money unwrapSuccess(Result<Money> result) {
        return switch (result) {
            case Result.Success<Money> s -> s.value();
            case Result.Failure<Money> f -> throw new IllegalStateException("Logic Error: " + f.message());
        };
    }
}