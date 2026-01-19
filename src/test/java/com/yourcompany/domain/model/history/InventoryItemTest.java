package com.yourcompany.domain.model.history;

import com.yourcompany.domain.model.inventory.InventoryItem;
import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryItemTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();

    @Test
    @DisplayName("【異常系】所持上限を超える加算は失敗し、INVENTORY_OVERFLOW を返すこと")
    void shouldFailWhenOverflowingCapacity() {
        // Given: 所持数 990, 上限 1000
        InventoryItem item = InventoryItem.create(userId, itemId);
        item.addQuantity(990, 1000); // 正常に990個付与

        // When: さらに 20 個付与 (合計1010 > 1000)
        Result<InventoryItem> result = item.addQuantity(20, 1000);

        // Then: 失敗すること
        assertThat(result).isInstanceOf(Result.Failure.class);
        Result.Failure<InventoryItem> failure = (Result.Failure<InventoryItem>) result;
        assertThat(failure.errorCode()).isEqualTo(GachaErrorCode.INVENTORY_OVERFLOW);

        // 状態が変わっていないこと
        assertThat(item.getQuantity()).isEqualTo(990);
    }

    @Test
    @DisplayName("【異常系】intの範囲を超えるオーバーフロー判定が正しく動作すること")
    void shouldHandleIntegerOverflowCheckSafely() {
        // Given: 所持数が int最大に近い値
        InventoryItem item = InventoryItem.create(userId, itemId);
        item.addQuantity(Integer.MAX_VALUE - 5, Integer.MAX_VALUE);

        // When: 10個追加 (合計が intの範囲を超える)
        // ここで (long) キャストしていないとマイナス判定されて通過してしまう恐れがある
        Result<InventoryItem> result = item.addQuantity(10, Integer.MAX_VALUE);

        // Then: 正しく OVERFLOW エラーになること
        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(GachaErrorCode.INVENTORY_OVERFLOW);
    }

    @Test
    @DisplayName("【異常系】負の値を追加しようとすると INVALID_PARAMETER を返すこと")
    void shouldFailWhenAddingNegativeQuantity() {
        InventoryItem item = InventoryItem.create(userId, itemId);
        Result<InventoryItem> result = item.addQuantity(-1, 100);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(GachaErrorCode.INVALID_PARAMETER);
    }
}