package com.yourcompany.domain.model.gacha;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.result.Result;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ガチャプール集約ルート (Aggregate Root)
 * 責務：ガチャの開催期間、コスト、および排出確率の整合性を保証する
 */
@Entity
@Table(name = "gacha_pools")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA用
@Slf4j
public class GachaPool {
    // 確率計算の分母 (100.00% = 10000)
    private static final int TOTAL_WEIGHT_LIMIT = 10000;

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "cost_amount", nullable = false)
    private int costAmount;

    // 天井設定（0なら天井なし）
    @Column(name = "pity_ceiling_count", nullable = false)
    private int pityCeilingCount;

    // 排出設定リスト (CascadeType.ALLでライフサイクルを共にする)
    @OneToMany(mappedBy = "gachaPool", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<GachaEmission> emissions = new ArrayList<>();

    @Version
    private long version;

    // プライベートコンストラクタ（不変条件チェック済みのものだけを生成するため）
    private GachaPool(UUID id, String name, Instant startAt, Instant endAt, int costAmount, int pityCeilingCount) {
        this.id = id;
        this.name = name;
        this.startAt = startAt;
        this.endAt = endAt;
        this.costAmount = costAmount;
        this.pityCeilingCount = pityCeilingCount;
    }

    /**
     * ガチャプールの新規作成（ファクトリメソッド）
     * ここで基本的な不変条件（期間逆転など）をチェックする
     */
    public static Result<GachaPool> create(String name, Instant startAt, Instant endAt, int costAmount, int pityCeilingCount) {
        if (costAmount <= 0) {
            return GachaErrorCode.INVALID_PARAMETER.toFailure("コストは1以上である必要があります");
        }
        if (pityCeilingCount <= 0) {
            return GachaErrorCode.INVALID_PARAMETER.toFailure("天井回数は0以上である必要があります");
        }
        // 2. 期間チェック (Start < End)
        if (!startAt.isBefore(endAt)) {
            return GachaErrorCode.INVALID_PARAMETER.toFailure("終了日時は開始日時より後である必要があります");
        }

        return Result.success(new GachaPool(
                UUID.randomUUID(), name, startAt, endAt, costAmount, pityCeilingCount
        ));
    }

    /**
     * 排出アイテムを追加する
     * ※追加時点では合計100%チェックはしない（構築中なので）。確定時にチェックする。
     */
    public void addEmission(GachaEmission emission) {
        // 双方向リレーションの整合性を保つ
        emission.assignToPool(this);
        this.emissions.add(emission);
    }

    /**
     * ガチャ構成の完全性検証（不変条件：確率合計が100%であること）
     * ※ガチャ公開前や実行前に必ず呼ぶ
     */
    public Result<GachaPool> validateConfiguration() {
        if (emissions.isEmpty()) {
            return GachaErrorCode.INVALID_WEIGHT_CONFIG.toFailure("排出設定が空です");
        }
        long totalWeight = emissions.stream()
                .mapToInt(GachaEmission::getWeight)
                .sum();

        if (totalWeight != TOTAL_WEIGHT_LIMIT) {
            return GachaErrorCode.INVALID_WEIGHT_CONFIG
                    .withArgs(totalWeight, TOTAL_WEIGHT_LIMIT); // "現在の合計: 9900, 期待値: 10000"
        }

        return Result.success(this);
    }
    /**
     * 現在、このガチャが開催中かどうか判定する
     */
    public boolean isOpen() {
        Instant now = Instant.now();
        return !now.isBefore(startAt) && now.isBefore(endAt);
    }

    /**
     * 指定した時刻において開催中か判定する（テスト容易性のため）
     */
    public boolean isOpenAt(Instant time) {
        return !time.isBefore(startAt) && time.isBefore(endAt);
    }
}
