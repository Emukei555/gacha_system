package com.yourcompany.domain.model.wallet;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GemsTest {

    @Nested
    @DisplayName("Factory (ofメソッド) の異常系テスト")
    class FactoryTest {

        @Test
        @DisplayName("【異常系】負の値で生成しようとした場合、INVALID_PARAMETER エラー")
        void shouldFailWhenCreatingWithNegativeValue() {
            Result<Gems> result = Gems.of(-1, 100);

            assertThat(result).isInstanceOf(Result.Failure.class);
            Result.Failure<Gems> failure = (Result.Failure<Gems>) result;
            assertThat(failure.errorCode()).isEqualTo(GachaErrorCode.INVALID_PARAMETER);
            assertThat(failure.message()).contains("残高は負の値にできません");
        }

        @Test
        @DisplayName("【異常系】合計がInteger.MAX_VALUEを超える場合、INVENTORY_OVERFLOW エラー")
        void shouldFailWhenTotalExceedsLimit() {
            // Integer.MAX_VALUE - 10 に 20 を足す -> オーバーフロー
            Result<Gems> result = Gems.of(Integer.MAX_VALUE - 10, 20);

            assertThat(result).isInstanceOf(Result.Failure.class);
            Result.Failure<Gems> failure = (Result.Failure<Gems>) result;
            assertThat(failure.errorCode()).isEqualTo(GachaErrorCode.INVENTORY_OVERFLOW);
        }
    }

    @Nested
    @DisplayName("subtract（消費）のテスト")
    class SubtractTest {

        @Test
        @DisplayName("【異常系】消費量が0以下の場合、INVALID_PARAMETER エラー")
        void shouldFailWhenAmountIsNonPositive() {
            // unwrap() を使用して安全に Gems インスタンスを取得
            Gems gems = Gems.of(100, 100).unwrap();

            Result<Gems> result = gems.subtract(0);

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(GachaErrorCode.INVALID_PARAMETER);
        }

        @Test
        @DisplayName("【異常系】残高不足の場合、INSUFFICIENT_BALANCE エラー")
        void shouldFailWhenInsufficientBalance() {
            // unwrap() を使用
            Gems gems = Gems.of(50, 50).unwrap(); // 合計100

            Result<Gems> result = gems.subtract(101); // 101消費したい

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(GachaErrorCode.INSUFFICIENT_BALANCE);
        }

        @Test
        @DisplayName("【正常系】有償石が優先して消費されること（Paid > Free）")
        void shouldConsumePaidStonesFirst() {
            // Given: 有償500, 無償500
            Gems gems = Gems.of(500, 500).unwrap();

            // When: 600消費
            Result<Gems> result = gems.subtract(600);

            // Then: 有償500全部 + 無償100消費 = 残り 有償0, 無償400
            assertThat(result).isInstanceOf(Result.Success.class);

            // 検証時も unwrap() を使うことでキャスト不要でスッキリ記述
            Gems newGems = result.unwrap();

            assertThat(newGems.paid()).isEqualTo(0);
            assertThat(newGems.free()).isEqualTo(400);
            assertThat(newGems.totalAmount()).isEqualTo(400);
        }
    }
}