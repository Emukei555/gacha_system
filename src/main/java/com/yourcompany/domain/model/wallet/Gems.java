package com.yourcompany.domain.model.wallet;

import com.sqlcanvas.sharedkernel.shared.error.CommonErrorCode; // ★CommonErrorCode追加
import com.sqlcanvas.sharedkernel.shared.result.Result;
import com.sqlcanvas.sharedkernel.shared.vo.ValueObject;
import com.yourcompany.domain.shared.exception.GachaErrorCode;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class Gems implements ValueObject {

    private int amount;

    private Gems(int amount) {
        this.amount = amount;
    }

    public static Result<Gems> of(int amount) {
        if (amount < 0) {
            // ★修正: Result.failure & CommonErrorCode
            return Result.failure(CommonErrorCode.INVALID_PARAMETER, "残高は負の値にできません");
        }
        return Result.success(new Gems(amount));
    }

    public Result<Gems> add(int value) {
        if (value < 0) {
            return Result.failure(CommonErrorCode.INVALID_PARAMETER, "追加量は正の値である必要があります");
        }
        long newAmount = (long) this.amount + value;
        if (newAmount > Integer.MAX_VALUE) {
            // ★修正: Result.failure & GachaErrorCode
            return Result.failure(GachaErrorCode.INVENTORY_OVERFLOW, "残高が上限値を超えています");
        }
        return Result.success(new Gems((int) newAmount));
    }

    public Result<Gems> subtract(int value) {
        if (value < 0) {
            return Result.failure(CommonErrorCode.INVALID_PARAMETER, "消費量は正の値である必要があります");
        }
        if (this.amount < value) {
            // ★修正: Result.failure & GachaErrorCode
            return Result.failure(GachaErrorCode.INSUFFICIENT_BALANCE, "石が不足しています");
        }
        return Result.success(new Gems(this.amount - value));
    }
}