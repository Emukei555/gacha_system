package com.yourcompany.domain.model.gacha;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.result.Result;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.UUID;

/**
 * ユーザーごとのガチャ進捗状態 (Aggregate Root)
 * 責務：天井カウント、確定枠カウントの正確な遷移
 */
@Entity
@Table(name = "user_gacha_states")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Slf4j
public class GachaState {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "gacha_pool_id")
    private UUID gachaPoolId;

    @Column(name = "current_pity_count", nullable = false)
    private int currentPityCount;

    @Column(name = "current_guaranteed_count", nullable = false)
    private int currentGuaranteedCount;

    @Version
    private long version;

    private GachaState(UUID userId, UUID gachaPoolId, int currentPityCount, int currentGuaranteedCount) {
        this.userId = userId;
        this.gachaPoolId = gachaPoolId;
        this.currentPityCount = currentPityCount;
        this.currentGuaranteedCount = currentGuaranteedCount;
    }

    /**
     * 新規状態の作成
     */
    public static GachaState create(UUID userId, UUID gachaPoolId) {
        return new GachaState(userId, gachaPoolId, 0, 0);
    }

    /**
     * ガチャ結果に基づいて状態を遷移させる
     *
     * @param isSsrEmitted 排出されたのが最高レアリティかどうか
     * @param poolSetting  プールの設定値（天井回数など）
     * @return 更新されたGachaState (Resultで包む)
     */
    public Result<GachaState> updateState(boolean isSsrEmitted, GachaPool poolSetting) {
        Objects.requireNonNull(poolSetting, "Pool setting is required");

        int pityCeiling = poolSetting.getPityCeilingCount();
        if (pityCeiling < 0) {
            log.warn("Invalid pity ceiling detected. userId={}, poolId={}, ceiling={}",
                    userId, gachaPoolId, pityCeiling);
            return GachaErrorCode.INVALID_PARAMETER.toFailure("Pity ceiling must be non-negative");
        }

        int previousPity = this.currentPityCount;
        int previousGuaranteed = this.currentGuaranteedCount;

        int nextPity = previousPity;
        int nextGuaranteed = previousGuaranteed;

        if (isSsrEmitted) {
            nextPity = 0;
            nextGuaranteed = 0;
            log.info("SSR emitted. Resetting counters. userId={}, poolId={}", userId, gachaPoolId);
        } else {
            nextPity += 1;
            nextGuaranteed += 1;
        }

        // Safety Guard: 天井設定を超えないようキャップ
        nextPity = Math.min(nextPity, pityCeiling);

        // 状態更新 (JPA管理下のEntityのため、フィールドを直接更新する)
        this.currentPityCount = nextPity;
        this.currentGuaranteedCount = nextGuaranteed;

        log.debug("GachaState updated. userId={}, poolId={}, Pity: {}->{}, Guaranteed: {}->{}",
                userId, gachaPoolId, previousPity, this.currentPityCount, previousGuaranteed, this.currentGuaranteedCount);

        return Result.success(this);
    }

    /**
     * 次の1回が「天井確定」かどうかを判定する
     */
    public boolean isPityReached(int ceilingCount) {
        if (ceilingCount <= 0) {
            return false;
        }
        // 次の1回(current + 1)で到達するか判定
        return (this.currentPityCount + 1) >= ceilingCount;
    }
}