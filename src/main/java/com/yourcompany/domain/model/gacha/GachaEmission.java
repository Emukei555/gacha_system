package com.yourcompany.domain.model.gacha;

import com.yourcompany.domain.model.gacha.WeightedItem;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "gacha_emissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GachaEmission implements WeightedItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gacha_pool_id", nullable = false)
    private GachaPool gachaPool;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(nullable = false)
    private int weight;

    @Column(name = "is_pickup")
    private boolean isPickup;

    // コンストラクタの作成
    public GachaEmission(UUID itemId, int weight, boolean isPickup) {
        if (weight <= 0) throw new IllegalArgumentException("Weight must be positive");
        this.id = UUID.randomUUID();
        this.itemId = itemId;
        this.weight = weight;
        this.isPickup = isPickup;
    }

    // Poolとの紐付け（GachaPool側から呼ばれる）
    void assignToPool(GachaPool pool) {
        this.gachaPool = pool;
    }

    @Override
    public int getWeight() {
        return weight;
    }
}