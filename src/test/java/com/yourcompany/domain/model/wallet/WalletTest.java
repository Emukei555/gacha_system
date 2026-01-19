package com.yourcompany.domain.model.wallet;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WalletTest {

    private final UUID userId = UUID.randomUUID();

    @Nested
    @DisplayName("consume（石の消費）のテスト")
    class ConsumeTest {

        @Test
        @DisplayName("【異常系】残高不足の場合、INSUFFICIENT_BALANCE エラーを返すこと")
        void shouldReturnErrorWhenBalanceIsInsufficient() {
            // Given: 0石のウォレット
            Wallet wallet = Wallet.create(userId);

            // When: 300石消費しようとする
            Result<Wallet> result = wallet.consume(300);

            // Then: Failure であり、エラーコードが一致すること
            assertThat(result).isInstanceOf(Result.Failure.class);
            Result.Failure<Wallet> failure = (Result.Failure<Wallet>) result;
            assertThat(failure.errorCode()).isEqualTo(GachaErrorCode.INSUFFICIENT_BALANCE);
        }

        @Test
        @DisplayName("【異常系】マイナスの値を消費しようとした場合、INVALID_PARAMETER を返すこと")
        void shouldReturnErrorWhenAmountIsNegative() {
            // Given
            Wallet wallet = Wallet.create(userId);
            // 初期残高セット（失敗したら即落ちるようunwrap使用）
            wallet.deposit(1000, 0).unwrap();

            // When: -1石消費しようとする
            Result<Wallet> result = wallet.consume(-1);

            // Then: Failure であり、INVALID_PARAMETER であること
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(GachaErrorCode.INVALID_PARAMETER);
        }

        @Test
        @DisplayName("【正常系】無償石が足りない場合、有償石を優先して消費し、残高が正しく更新されること")
        void shouldConsumePaidStonesFirst() {
            // Given: 有償1000石、無償500石
            Wallet wallet = Wallet.create(userId);
            wallet.deposit(1000, 500).unwrap();

            // When: 1200石消費 (有償1000全額 + 無償200 消費)
            Result<Wallet> result = wallet.consume(1200);

            // Then: Success であり、残高が正確であること
            assertThat(result).isInstanceOf(Result.Success.class);

            Wallet updatedWallet = result.unwrap();

            // Money型を経由して値を確認 (Money.amount()を使用)
            assertThat(updatedWallet.getPaidMoney().amount()).isEqualTo(0);
            assertThat(updatedWallet.getFreeMoney().amount()).isEqualTo(300);

            // 合計値のヘルパーメソッドがある場合はそちらも確認
            assertThat(updatedWallet.getTotalStones()).isEqualTo(300L);
        }
    }

    @Nested
    @DisplayName("deposit（石の付与）のテスト")
    class DepositTest {

        @Test
        @DisplayName("【異常系】付与後の合計が int 上限を超える場合、INVENTORY_OVERFLOW を返すこと")
        void shouldReturnErrorWhenOverflow() {
            // Given: ほぼ上限に近い石を持つウォレット (Integer.MAX_VALUE - 100)
            Wallet wallet = Wallet.create(userId);
            wallet.deposit(Integer.MAX_VALUE - 100, 0).unwrap();

            // When: さらに200石付与しようとする -> オーバーフロー
            Result<Wallet> result = wallet.deposit(200, 0);

            // Then: Failure であり、エラーコードは INVENTORY_OVERFLOW
            assertThat(result).isInstanceOf(Result.Failure.class);
            Result.Failure<Wallet> failure = (Result.Failure<Wallet>) result;

            // Moneyクラスの実装に合わせて INVENTORY_OVERFLOW を期待する
            assertThat(failure.errorCode()).isEqualTo(GachaErrorCode.INVENTORY_OVERFLOW);
        }

        @Test
        @DisplayName("【正常系】正の値を付与した場合、残高が加算されること")
        void shouldIncreaseBalance() {
            Wallet wallet = Wallet.create(userId);

            Result<Wallet> result = wallet.deposit(100, 200);

            assertThat(result).isInstanceOf(Result.Success.class);

            Wallet w = result.unwrap();
            // Money経由で確認
            assertThat(w.getPaidMoney().amount()).isEqualTo(100);
            assertThat(w.getFreeMoney().amount()).isEqualTo(200);
            assertThat(w.getTotalStones()).isEqualTo(300L);
        }
    }
}