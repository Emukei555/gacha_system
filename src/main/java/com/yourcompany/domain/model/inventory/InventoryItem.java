package com.yourcompany.domain.model.inventory;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.result.Result;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * ユーザーの所持アイテム (Entity)
 * 責務：
 * 1. アイテム所持数の管理（加算・減算）
 * 2. 所持上限（MaxCapacity）のチェック
 * 3. データの不整合防止（負の数やオーバーフロー）
 */
@Entity
@Table(name = "user_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Slf4j
// 複合キー (userId + itemId) を扱う場合は本来 @IdClass が必要ですが、
// ここでは簡易的に userId に @Id を付与するか、または個別に @IdClass を定義する想定とします
public class InventoryItem {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "item_id") // 実際は複合主キーの一部
    private UUID itemId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Version
    private long version;

    // Factory用コンストラクタ
    private InventoryItem(UUID userId, UUID itemId, int quantity) {
        this.userId = userId;
        this.itemId = itemId;
        this.quantity = quantity;
    }

    /**
     * 新規アイテム所持レコードの作成 (所持数0)
     */
    public static InventoryItem create(UUID userId, UUID itemId) {
        return new InventoryItem(userId, itemId, 0);
    }

    /**
     * アイテム数を加算する
     *
     * @param amount      追加する量
     * @param maxCapacity アイテムごとの所持上限（Masterデータから取得）
     * @return 更新された InventoryItem (Resultで包む)
     */
    public Result<InventoryItem> addQuantity(int amount, int maxCapacity) {
        // 1. ガード: amount <= 0 なら INVALID_PARAMETER
        if (amount <= 0) {
            log.warn("Invalid addition amount. userId={}, itemId={}, amount={}", userId, itemId, amount);
            return GachaErrorCode.INVALID_PARAMETER.toFailure("付与量は正の値である必要があります");
        }

        // 2. ガード: オーバーフローチェック (longキャストで計算して比較)
        long projectedQuantity = (long) this.quantity + amount;

        if (projectedQuantity > maxCapacity) {
            log.warn("Inventory overflow detected. userId={}, itemId={}, current={}, add={}, max={}",
                    userId, itemId, this.quantity, amount, maxCapacity);

            return GachaErrorCode.INVENTORY_OVERFLOW.toFailure(
                    String.format("所持上限を超えています。最大: %d, 現在: %d, 追加: %d", maxCapacity, this.quantity, amount)
            );
        }

        // ログ: 正常更新前のデバッグ情報
        log.debug("Adding inventory quantity. userId={}, itemId={}, before={}, add={}",
                userId, itemId, this.quantity, amount);

        // 3. 加算実行 (Entityなので自身のフィールドを書き換える)
        this.quantity += amount;

        // 4. Success(this) を返す
        return Result.success(this);
    }
}