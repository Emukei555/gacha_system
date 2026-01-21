package com.yourcompany.domain.model.wallet;

// ★CommonErrorCodeを追加
import com.sqlcanvas.sharedkernel.shared.error.CommonErrorCode;
import com.sqlcanvas.sharedkernel.shared.result.Result;
import com.sqlcanvas.sharedkernel.shared.vo.ValueObject;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class Money implements ValueObject {

    private int amount;

    private Money(int amount) {
        this.amount = amount;
    }

    public static Result<Money> of(int amount) {
        if (amount < 0) {
            // ★修正: GachaErrorCode.INVALID_PARAMETER ではなく CommonErrorCode.INVALID_PARAMETER を使用
            return Result.failure(CommonErrorCode.INVALID_PARAMETER, "金額は0以上である必要があります");
        }
        return Result.success(new Money(amount));
    }

    public Result<Money> add(Money other) {
        long newAmount = (long) this.amount + other.amount;
        if (newAmount > Integer.MAX_VALUE) {
            // オーバーフロー時のエラー処理（必要に応じてCommonErrorCode.SYSTEM_ERRORなど）
            return Result.failure(CommonErrorCode.INVALID_PARAMETER, "金額が上限を超えました");
        }
        return Result.success(new Money((int) newAmount));
    }
}