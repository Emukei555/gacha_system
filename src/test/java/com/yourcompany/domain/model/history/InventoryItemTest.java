package com.yourcompany.domain.model.history;

import com.sqlcanvas.sharedkernel.shared.error.CommonErrorCode;
import com.sqlcanvas.sharedkernel.shared.result.Result;
import com.yourcompany.domain.model.inventory.InventoryItem;
import com.yourcompany.domain.shared.exception.GachaErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryItemTest {

    @Test
    @DisplayName("正常系: アイテムを加算できる")
    void testAddQuantity() {
        InventoryItem item = InventoryItem.create(UUID.randomUUID(), UUID.randomUUID());
        Result<InventoryItem> result = item.addQuantity(20, 1000);

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.orElseThrow(failure -> new RuntimeException(failure.message())).getQuantity()).isEqualTo(20);
    }

    @Test
    @DisplayName("異常系: 上限を超えるとエラー")
    void testOverflow() {
        InventoryItem item = InventoryItem.create(UUID.randomUUID(), UUID.randomUUID());
        // 既に MAX 近い状態にするなど、テスト容易性のためにコンストラクタや状態変更が必要ですが、
        // ここでは addQuantity で一気に上限超えを狙います
        Result<InventoryItem> result = item.addQuantity(10, 5); // 上限5に対して10追加

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(GachaErrorCode.INVENTORY_OVERFLOW);
    }

    @Test
    @DisplayName("異常系: 負の数は加算できない")
    void testNegativeAdd() {
        InventoryItem item = InventoryItem.create(UUID.randomUUID(), UUID.randomUUID());
        Result<InventoryItem> result = item.addQuantity(-1, 100);

        assertThat(result).isInstanceOf(Result.Failure.class);
        // CommonErrorCode に変更
        assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER);
    }
}