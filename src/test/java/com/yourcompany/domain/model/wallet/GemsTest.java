package com.yourcompany.domain.model.wallet;

import com.sqlcanvas.sharedkernel.shared.error.CommonErrorCode;
import com.sqlcanvas.sharedkernel.shared.result.Result;
import com.yourcompany.domain.shared.exception.GachaErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GemsTest {

    @Nested
    class FactoryTest {
        @Test
        @DisplayName("異常系: 負の値では作成できない")
        void testNegative() {
            // Gems.of は int を1つ取るように変更されている前提
            Result<Gems> result = Gems.of(-1);

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER);
        }
    }

    @Nested
    class SubtractTest {
        @Test
        @DisplayName("異常系: 0未満は引けない")
        void testNegativeSubtract() {
            Gems gems = Gems.of(100).orElseThrow(failure -> new RuntimeException(failure.message()));
            Result<Gems> result = gems.subtract(-1); // 修正: 0はOKの場合があるため-1でテスト

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER);
        }

        @Test
        @DisplayName("異常系: 残高不足")
        void testInsufficient() {
            Gems gems = Gems.of(50).orElseThrow(failure -> new RuntimeException(failure.message()));
            Result<Gems> result = gems.subtract(51);

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(GachaErrorCode.INSUFFICIENT_BALANCE);
        }

        @Test
        @DisplayName("正常系: 減算できる")
        void testSuccess() {
            Gems gems = Gems.of(100).orElseThrow(failure -> new RuntimeException(failure.message()));
            Result<Gems> result = gems.subtract(40);

            assertThat(result).isInstanceOf(Result.Success.class);
            assertThat(result.orElseThrow(failure -> new RuntimeException(failure.message())).getAmount()).isEqualTo(60);
        }
    }
}