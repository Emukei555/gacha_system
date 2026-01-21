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

@Entity
@Table(name = "gacha_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GachaTransaction {

    @Id
    @Column(name = "request_id") // DB: request_id (VARCHAR)
    private String requestId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "pool_id", nullable = false) // DB: pool_id
    private UUID poolId;

    @Column(name = "consumed_paid", nullable = false)
    private int consumedPaid;

    @Column(name = "consumed_free", nullable = false)
    private int consumedFree;

    @Column(name = "result_json", columnDefinition = "TEXT") // DB: result_json
    private String resultJson;

    @Column(name = "created_at", nullable = false) // DB: created_at
    private Instant createdAt;

    // コンストラクタ
    private GachaTransaction(RequestId requestId, UUID userId, UUID poolId, int consumedPaid, int consumedFree, String resultJson) {
        this.requestId = requestId.toString(); // UUID -> String変換
        this.userId = userId;
        this.poolId = poolId;
        this.consumedPaid = consumedPaid;
        this.consumedFree = consumedFree;
        this.resultJson = resultJson;
        this.createdAt = Instant.now();
    }

    /**
     * ファクトリメソッド
     */
    public static GachaTransaction record(RequestId requestId, UUID userId, UUID poolId, int consumedPaid, int consumedFree, String resultJson) {
        return new GachaTransaction(requestId, userId, poolId, consumedPaid, consumedFree, resultJson);
    }
}