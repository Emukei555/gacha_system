package com.yourcompany.domain.model.gacha;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.result.Result;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.util.Objects;

/**
 * ユーザーごとのガチャ進捗状態 (Aggregate Root)
 * 責務：天井カウント、確定枠カウントの正確な遷移
 */
@Entity
@Table(name = "user_gacha_states")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GachaState {

    @Id
    @Column(name = "user_id") // 複合キーの場合は @IdClass 等が必要ですが、ここでは簡易的に
    private UUID userId;

    @Column(name = "gacha_pool_id")
    private UUID gachaPoolId;

    @Column(name = "current_pity_count", nullable = false)
    private int currentPityCount;

    @Column(name = "current_guaranteed_count", nullable = false)
    private int currentGuaranteedCount;

    @Version
    private long version;

    // コンストラクタ（Factory用）
    private GachaState(UUID userId, UUID gachaPoolId, int currentPityCount, int currentGuaranteedCount) {
        this.userId = userId;
        this.gachaPoolId = gachaPoolId;
        this.currentPityCount = currentPityCount;
        this.currentGuaranteedCount = currentGuaranteedCount;
    }

    // コンストラクタ（内部コピー用: immutable風に新インスタンス作成時）
    private GachaState(GachaState original, int newPityCount, int newGuaranteedCount) {
        this.userId = original.userId;
        this.gachaPoolId = original.gachaPoolId;
        this.currentPityCount = newPityCount;
        this.currentGuaranteedCount = newGuaranteedCount;
        this.version = original.version + 1;  // 楽観的ロック意識
    }

    /**
     * 新規状態の作成
     */
    public static GachaState create(UUID userId, UUID gachaPoolId) {
        return new GachaState(userId, gachaPoolId, 0, 0);
    }

    /**
     * ガチャ結果に基づいて状態を遷移させる
     * @param isSsrEmitted 排出されたのが最高レアリティかどうか
     * @param poolSetting プールの設定値（天井回数など）
     * @return 更新されたGachaState (Resultで包む)
     */
    public Result<GachaState> updateState(boolean isSsrEmitted, GachaPool poolSetting) {
        // 1. ガード: poolSetting が null なら例外 (System Error)
        Objects.requireNonNull(poolSetting, "Pool setting is required");

        // 2. ガード: poolSetting.pityCeilingCount が負の値なら
        //    → GachaErrorCode.INVALID_PARAMETER を返す
        //    (RPN対策: 設定値異常によるロジック破綻防止)
        int pityCeiling = poolSetting.getPityCeilingCount();
        if (pityCeiling < 0) {
            return GachaErrorCode.INVALID_PARAMETER.toFailure("Pity ceiling must be non-negative");
        }

        // 3. 次の状態変数を準備 (現在の値をコピー)
        int nextPity = this.currentPityCount;
        int nextGuaranteed = this.currentGuaranteedCount;

        // 4. 分岐ロジック:
        //    Case A: SSRが排出された場合 (isSsrEmitted == true)
        //       - nextPity を 0 にリセット
        //       - nextGuaranteed を 0 にリセット (仕様による)
        //    Case B: SSR以外の場合
        //       - nextPity を +1 加算
        //       - nextGuaranteed を +1 加算
        if (isSsrEmitted) {
            nextPity = 0;
            nextGuaranteed = 0;  // 仕様に応じて（通常リセット）
        } else {
            nextPity += 1;
            nextGuaranteed += 1;
        }

        // 5. 安全装置 (Safety Guard):
        //    - nextPity が poolSetting.pityCeilingCount を超えないように Math.min でキャップする
        //    (RPN対策: 万が一のカウント超過を防ぎ、DB制約違反エラーを回避する)
        nextPity = Math.min(nextPity, pityCeiling);

        // 6. 状態更新 (JPA Entityは可変とするのが一般的だが、不変っぽく扱うなら値をセットしてthisを返す)
        //    → ここでは新インスタンスを作成 (immutable風)
        GachaState updated = new GachaState(this, nextPity, nextGuaranteed);

        // 7. Success(this) を返す
        return Result.success(updated);
    }

    /**
     * 次の1回が「天井確定」かどうかを判定する
     * UI表示や、ガチャ実行時の確率テーブル切り替えに使用
     */
    public boolean isPityReached(int ceilingCount) {
        // 1. ガード: ceilingCount <= 0 (天井なし設定) なら false
        if (ceilingCount <= 0) {
            return false;
        }

        // 2. 判定: (現在のカウント + 1) >= ceilingCount なら true
        //    ※「次引いたら到達」なのか「既に到達している」なのか仕様を明確にする。
        //      一般的には「カウントが299で、次の1回(300回目)で確定」なので (current + 1 >= ceiling)
        return (this.currentPityCount + 1) >= ceilingCount;
    }
}