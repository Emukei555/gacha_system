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

    public static Wallet create(UUID userId) {
        return new Wallet(userId, 0, 0);
    }

    /**
     * 石を消費する（Resultスタイル）
     */
    public Result<Wallet> consume(int amount) {
        if (amount < 0) {
            // デフォルトだと「石が不足しています」だけだが...
            // 詳細を添えて「石が不足しています。残り: 150」のように返せる
            return Result.failure(GachaErrorCode.INVALID_PARAMETER, "消費量は正の数である必要があります");
        }

        if (getTotalStones() < (long) amount) {
            return Result.failure(GachaErrorCode.INSUFFICIENT_BALANCE);
        }
        // 変更前のスナップショット
        log.debug("Consuming stones. userId={}, currentPaid={}, currentFree={}, amount={}",
                userId, paidStones, freeStones, amount);

        int remaining = amount;

        // 有償石から先に消費
        int paidConsume = Math.min(this.paidStones, remaining);
        this.paidStones -= paidConsume;
        remaining -= paidConsume;

        // 残りを無償石から消費
        if (remaining > 0) {
            this.freeStones -= remaining;
        }

        // 変更確定ログ
        log.info("Wallet updated. userId={}, consumed={}, newPaid={}, newFree={}",
                userId, amount, this.paidStones, this.freeStones);

        return Result.success(this);
    }

    /**
     * 石を付与する（Resultスタイル）
     */
    public Result<Wallet> deposit(int paidAmount, int freeAmount) {
        if (paidAmount < 0 || freeAmount < 0) {
            return Result.failure(GachaErrorCode.INTERNAL_ERROR, "付与量は0以上である必要があります");
        }

        // オーバーフロー防止のガード
        if ((long)this.paidStones + paidAmount > Integer.MAX_VALUE ||
                (long)this.freeStones + freeAmount > Integer.MAX_VALUE) {
            return Result.failure(GachaErrorCode.INTERNAL_ERROR, "所持上限を超えます");
        }

        this.paidStones += paidAmount;
        this.freeStones += freeAmount;

        return Result.success(this);
    }

    public long getTotalStones() {
        return (long) paidStones + freeStones;
    }
}