package com.yourcompany.domain.model.history;

import com.yourcompany.domain.shared.value.RequestId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * ガチャ実行履歴 (Entity - Immutable)
 * 責務：実行結果の永続的な記録（証跡）。一度作成したら変更不可。
 */
@Entity
@Table(name = "gacha_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GachaTransaction {
    // RequestId を主キーとして利用 (冪等性担保のため)
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "gacha_pool_id", nullable = false)
    private UUID gachaPoolId;

    @Column(name = "consumed_paid", nullable = false)
    private int consumedPaid;

    @Column(name = "consumed_free", nullable = false)
    private int consumedFree;

    // JPA標準ではJSONBはサポート外のため、StringとしてJSONを保存するか、
    // 専用ライブラリ導入が必要。ここではStringで定義。
    @Column(name = "emission_results", columnDefinition = "jsonb", nullable = false)
    private String emissionResultsJson;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    // 全フィールド初期化コンストラクタ
    private GachaTransaction(RequestId id, UUID userId, UUID poolId, int paid, int free, String resultsJson, Instant at) {
        this.id = id.value();
        this.userId = userId;
        this.gachaPoolId = poolId;
        this.consumedPaid = paid;
        this.consumedFree = free;
        this.emissionResultsJson = resultsJson;
        this.executedAt = at;
    }
    /**
     * トランザクション記録の作成
     * ※このオブジェクトは「結果」なのでガード節は最低限（nullチェック等）のみ。
     * ビジネスロジックの正当性はService層で担保済みである前提。
     */
    public static GachaTransaction record(
            RequestId requestId,
            UUID userId,
            UUID gachaPoolId,
            int consumedPaid,
            int consumedFree,
            String emissionResultsJson) {

        return new GachaTransaction(
                requestId,
                userId,
                gachaPoolId,
                consumedPaid,
                consumedFree,
                emissionResultsJson,
                Instant.now()
        );
    }
}
