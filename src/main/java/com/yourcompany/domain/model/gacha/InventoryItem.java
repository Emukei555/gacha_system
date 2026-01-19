package com.yourcompany.domain.model.gacha;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.result.Result;

public class InventoryItem {
    // ... other fields (assuming private int quantity; etc.)

    public Result<InventoryItem> addQuantity(int amount, int maxCapacity) {
        // 1. ガード: amount <= 0 なら INVALID_PARAMETER (付与数は正のみ)
        if (amount <= 0) {
            return GachaErrorCode.INVALID_PARAMETER.toFailure("付与量は正の値である必要があります");
        }

        // 2. ガード: オーバーフローチェック
        //    - (long) this.quantity + amount > maxCapacity
        //      → INVENTORY_OVERFLOW (G003) を返す
        //      (RPN 200対策: アイテムあふれの防止)
        if ((long) this.quantity + amount > maxCapacity) {
            return GachaErrorCode.INVENTORY_OVERFLOW.toFailure(
                    "所持上限を超えています。最大: " + maxCapacity + ", 現在の量: " + this.quantity + ", 追加量: " + amount
            );
        }

        // 3. 加算実行
        //    - this.quantity += amount
        this.quantity += amount;

        // 4. Success(this) を返す
        return Result.success(this);
    }
}