package com.yourcompany.domain.model.wallet;

import com.sqlcanvas.sharedkernel.shared.error.CommonErrorCode;
import com.sqlcanvas.sharedkernel.shared.result.Result;
import com.yourcompany.domain.shared.exception.GachaErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WalletTest {

    @Nested
    class ConsumeTest {
        @Test
        @DisplayName("異常系: 残高不足ならエラー")
        void testInsufficient() {
            Wallet wallet = Wallet.create(UUID.randomUUID()); // 0, 0
            Result<Wallet> result = wallet.consume(300);

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(GachaErrorCode.INSUFFICIENT_BALANCE);
        }

        @Test
        @DisplayName("異常系: 負の消費量はエラー")
        void testNegative() {
            Wallet wallet = Wallet.create(UUID.randomUUID());
            Result<Wallet> result = wallet.consume(-1);

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER);
        }
    }

    @Nested
    class DepositTest {
        @Test
        @DisplayName("異常系: 負の追加はエラー")
        void testNegative() {
            Wallet wallet = Wallet.create(UUID.randomUUID());
            Result<Wallet> result = wallet.deposit(-100, 0);

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER);
        }

        @Test
        @DisplayName("正常系: 加算される")
        void testSuccess() {
            Wallet wallet = Wallet.create(UUID.randomUUID());
            Result<Wallet> result = wallet.deposit(100, 200);

            assertThat(result).isInstanceOf(Result.Success.class);
            Wallet updated = // ラムダ式で Failure のメッセージを取り出して渡す
                    result.orElseThrow(failure -> new RuntimeException(failure.message()));
            assertThat(updated.getPaidStones()).isEqualTo(100);
            assertThat(updated.getFreeStones()).isEqualTo(200);
        }
    }
}