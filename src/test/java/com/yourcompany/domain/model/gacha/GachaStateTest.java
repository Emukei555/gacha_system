package com.yourcompany.domain.model.gacha;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GachaStateTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID poolId = UUID.randomUUID();

    // テスト用のダミーGachaPool作成ヘルパー
    private GachaPool createPool(int ceiling) {
        // Result.unwrap() を使用して安全に取り出す
        return GachaPool.create(
                "Test Pool",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                100,
                ceiling
        ).unwrap();
    }

    @Nested
    @DisplayName("updateState（状態遷移）のテスト")
    class UpdateStateTest {

        @Test
        @DisplayName("【異常系】Pool設定がnullの場合、NullPointerExceptionを送出すること")
        void shouldThrowExceptionWhenPoolIsNull() {
            GachaState state = GachaState.create(userId, poolId);
            assertThatThrownBy(() -> state.updateState(false, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Pool setting is required");
        }

        @Test
        @DisplayName("【異常系】天井設定が負の値の場合、INVALID_PARAMETER を返すこと")
        void shouldReturnErrorWhenCeilingIsNegative() throws Exception {
            GachaState state = GachaState.create(userId, poolId);

            // 1. 通常の手順でプールを作成（createメソッドでは負の値を弾くため）
            GachaPool pool = createPool(300);

            // 2. リフレクションを使って無理やり天井設定を負の値(-1)に書き換える
            //    これにより、GachaState側の防御ロジックをテストできる
            Field ceilingField = GachaPool.class.getDeclaredField("pityCeilingCount");
            ceilingField.setAccessible(true);
            ceilingField.setInt(pool, -1);

            // When
            Result<GachaState> result = state.updateState(false, pool);

            // Then
            assertThat(result).isInstanceOf(Result.Failure.class);
            Result.Failure<GachaState> failure = (Result.Failure<GachaState>) result;
            assertThat(failure.errorCode()).isEqualTo(GachaErrorCode.INVALID_PARAMETER);
        }

        @Test
        @DisplayName("【正常系/境界値】天井設定を超えてカウントが増加しないこと（Safety Guard）")
        void shouldCapPityCountAtCeiling() {
            // Given: 天井まであと1回の状態 (299回)
            int ceiling = 300;
            GachaState state = GachaState.create(userId, poolId);
            GachaPool pool = createPool(ceiling);

            // 299回ハズレを引いた状態にする
            for (int i = 0; i < 299; i++) {
                state.updateState(false, pool);
            }
            assertThat(state.getCurrentPityCount()).isEqualTo(299);

            // When: 300回目（天井到達）でハズレ判定
            state.updateState(false, pool);
            assertThat(state.getCurrentPityCount()).isEqualTo(300);

            // When: 301回目（システムのバグで天井発動せずハズレ扱いになった場合）
            state.updateState(false, pool);

            // Then: カウントは300で止まっていること（Safety Guard発動）
            assertThat(state.getCurrentPityCount()).isEqualTo(300);
        }

        @Test
        @DisplayName("【正常系】SSR排出時にカウントが0にリセットされること")
        void shouldResetCountersOnSsrEmission() {
            GachaState state = GachaState.create(userId, poolId);
            GachaPool pool = createPool(300);

            // 何回か引く
            state.updateState(false, pool);
            state.updateState(false, pool);
            assertThat(state.getCurrentPityCount()).isEqualTo(2);

            // SSR排出
            Result<GachaState> result = state.updateState(true, pool);

            assertThat(result).isInstanceOf(Result.Success.class);
            // Resultの中身を確認しても良いし、state変数が更新されているか確認しても良い
            // ここではEntityの変更を確認
            assertThat(state.getCurrentPityCount()).isEqualTo(0);
            assertThat(state.getCurrentGuaranteedCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("isPityReached（天井判定）のテスト")
    class IsPityReachedTest {

        @Test
        @DisplayName("【境界値】現在のカウント + 1 が天井以上なら true を返すこと")
        void shouldReturnTrueIfNextDrawReachesCeiling() {
            GachaState state = GachaState.create(userId, poolId);
            GachaPool pool = createPool(10); // 天井10回

            // 9回目まで進める
            for (int i = 0; i < 9; i++) state.updateState(false, pool);

            // 現在9回。次(10回目)で天井到達
            assertThat(state.isPityReached(10)).isTrue();
        }

        @Test
        @DisplayName("【異常系】天井設定が0（天井なし）の場合、常に false を返すこと")
        void shouldReturnFalseWhenCeilingIsZero() {
            GachaState state = GachaState.create(userId, poolId);
            // カウントを進めても...
            state.updateState(false, createPool(0));

            assertThat(state.isPityReached(0)).isFalse();
        }
    }
}