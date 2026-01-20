package com.yourcompany.domain.model.gacha;

import com.sqlcanvas.sharedkernel.shared.result.Result;
import com.sqlcanvas.sharedkernel.shared.error.CommonErrorCode;
import com.yourcompany.domain.shared.exception.GachaErrorCode;
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
 */
@Entity
@Table(name = "gacha_pools")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Slf4j
public class GachaPool {
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

    @Column(name = "pity_ceiling_count", nullable = false)
    private int pityCeilingCount;

    @OneToMany(mappedBy = "gachaPool", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<GachaEmission> emissions = new ArrayList<>();

    @Version
    private long version;

    private GachaPool(UUID id, String name, Instant startAt, Instant endAt, int costAmount, int pityCeilingCount) {
        this.id = id;
        this.name = name;
        this.startAt = startAt;
        this.endAt = endAt;
        this.costAmount = costAmount;
        this.pityCeilingCount = pityCeilingCount;
    }

    public static Result<GachaPool> create(String name, Instant startAt, Instant endAt, int costAmount, int pityCeilingCount) {
        if (costAmount <= 0) {
            // 修正: Result.failure ファクトリを使用
            return Result.failure(CommonErrorCode.INVALID_PARAMETER, "コストは1以上である必要があります");
        }
        if (pityCeilingCount < 0) {
            // 修正: Result.failure ファクトリを使用
            return Result.failure(CommonErrorCode.INVALID_PARAMETER, "天井回数は0以上である必要があります");
        }
        if (!startAt.isBefore(endAt)) {
            // 修正: Result.failure ファクトリを使用
            return Result.failure(CommonErrorCode.INVALID_PARAMETER, "終了日時は開始日時より後である必要があります");
        }

        return Result.success(new GachaPool(
                UUID.randomUUID(), name, startAt, endAt, costAmount, pityCeilingCount
        ));
    }

    public void addEmission(GachaEmission emission) {
        emission.assignToPool(this);
        this.emissions.add(emission);
    }

    public Result<GachaPool> validateConfiguration() {
        if (emissions.isEmpty()) {
            // 修正: ガチャ固有のエラーなので GachaErrorCode を使用
            return Result.failure(GachaErrorCode.INVALID_WEIGHT_CONFIG, "排出設定が空です");
        }
        long totalWeight = emissions.stream()
                .mapToInt(GachaEmission::getWeight)
                .sum();

        if (totalWeight != TOTAL_WEIGHT_LIMIT) {
            // 修正: String.format でメッセージを生成してから渡す
            String msg = String.format("現在の合計: %d, 期待値: %d", totalWeight, TOTAL_WEIGHT_LIMIT);
            return Result.failure(GachaErrorCode.INVALID_WEIGHT_CONFIG, msg);
        }

        return Result.success(this);
    }

    public boolean isOpen() {
        Instant now = Instant.now();
        return !now.isBefore(startAt) && now.isBefore(endAt);
    }

    public boolean isOpenAt(Instant time) {
        return !time.isBefore(startAt) && time.isBefore(endAt);
    }
}