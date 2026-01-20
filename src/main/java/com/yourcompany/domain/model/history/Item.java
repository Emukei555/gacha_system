package com.yourcompany.domain.model.history;

import com.sqlcanvas.sharedkernel.shared.error.CommonErrorCode;
import com.sqlcanvas.sharedkernel.shared.result.Result;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Entity
@Table(name = "items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Slf4j
public class Item {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    // 今回は簡易的にString。厳密にはEnum (Rarity) が推奨
    @Column(nullable = false)
    private String rarity;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    // コンストラクタ
    private Item(UUID id, String name, String rarity, int maxCapacity) {
        this.id = id;
        this.name = name;
        this.rarity = rarity;
        this.maxCapacity = maxCapacity;
    }

    /**
     * マスタデータの作成（管理画面等で使用）
     */
    public static Result<Item> create(String name, String rarity, int maxCapacity) {
        if (name == null || name.isEmpty()) {
            // 修正: メッセージを「アイテム名は必須です」に変更
            return Result.failure(CommonErrorCode.INVALID_PARAMETER, "アイテム名は必須です");
        }
        if (maxCapacity <= 0) {
            log.warn("Invalid maxCapacity: {}", maxCapacity);
            // 修正: メッセージを「所持上限は1以上〜」に変更
            return Result.failure(CommonErrorCode.INVALID_PARAMETER, "所持上限は1以上である必要があります");
        }
        return Result.success(new Item(UUID.randomUUID(), name, rarity, maxCapacity));
    }
}