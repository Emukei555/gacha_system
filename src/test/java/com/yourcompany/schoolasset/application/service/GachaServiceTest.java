package com.yourcompany.schoolasset.application.service;

import com.yourcompany.domain.model.gacha.*;
import com.yourcompany.domain.model.wallet.Wallet;
import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.exception.GachaException;
import com.yourcompany.domain.shared.result.Result;
import com.yourcompany.schoolasset.infrastructure.persistence.repository.GachaPoolRepository;
import com.yourcompany.schoolasset.infrastructure.persistence.repository.GachaStateRepository;
import com.yourcompany.schoolasset.infrastructure.persistence.repository.WalletRepository;
import com.yourcompany.web.dto.GachaDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GachaServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private GachaPoolRepository poolRepository;
    @Mock
    private GachaStateRepository stateRepository;
    @Mock
    private LotteryService lotteryService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private GachaService gachaService;

    private MockedStatic<TransactionAspectSupport> mockedTransaction;
    private TransactionStatus transactionStatus;

    private final UUID userId = UUID.randomUUID();
    private final UUID poolId = UUID.randomUUID();
    private GachaDto.DrawRequest request;
    private GachaPool pool;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        request = new GachaDto.DrawRequest(poolId, 1);

        pool = mock(GachaPool.class);
        // ★修正点: lenient() を追加して、異常系で呼ばれなくてもエラーにしない
        lenient().when(pool.getId()).thenReturn(poolId);
        lenient().when(pool.getCostAmount()).thenReturn(300);

        wallet = mock(Wallet.class);
        // ★修正点: lenient() を追加
        lenient().when(wallet.getPaidStones()).thenReturn(1000);
        lenient().when(wallet.getFreeStones()).thenReturn(0);

        transactionStatus = mock(TransactionStatus.class);

        // ★修正点: StaticモックもLenient設定にする
        // (早期リターン時に rollbackOnly が呼ばれないケースがあるため)
        mockedTransaction = mockStatic(TransactionAspectSupport.class, withSettings().lenient());
        mockedTransaction.when(TransactionAspectSupport::currentTransactionStatus).thenReturn(transactionStatus);
    }

    @AfterEach
    void tearDown() {
        mockedTransaction.close();
    }

    @Test
    @DisplayName("【異常系】プールが存在しない、または期間外の場合、GACHA_POOL_EXPIRED エラーを返す")
    void shouldReturnErrorWhenPoolNotFound() {
        // Given
        when(poolRepository.findByIdWithEmissions(poolId)).thenReturn(Optional.empty());

        // When
        Result<GachaDto.DrawResponse> result = gachaService.drawGacha(userId, request);

        // Then
        assertThat(result).isInstanceOf(Result.Failure.class);
        Result.Failure<?> failure = (Result.Failure<?>) result;
        assertThat(failure.errorCode()).isEqualTo(GachaErrorCode.GACHA_POOL_EXPIRED);

        verify(walletRepository, never()).findByIdWithLock(any());
    }

    @Test
    @DisplayName("【異常系】ウォレットが見つからない場合、例外をスローする")
    void shouldThrowExceptionWhenWalletNotFound() {
        // Given
        when(poolRepository.findByIdWithEmissions(poolId)).thenReturn(Optional.of(pool));
        when(pool.isOpen()).thenReturn(true);
        when(walletRepository.findByIdWithLock(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> gachaService.drawGacha(userId, request))
                .isInstanceOf(GachaException.class)
                .extracting("errorCode").isEqualTo(GachaErrorCode.WALLET_NOT_FOUND);
    }

    @Test
    @DisplayName("【異常系】残高不足の場合、INSUFFICIENT_BALANCE エラーを返し、ロールバックをマークする")
    void shouldRollbackWhenBalanceInsufficient() {
        // Given
        when(poolRepository.findByIdWithEmissions(poolId)).thenReturn(Optional.of(pool));
        when(pool.isOpen()).thenReturn(true);
        when(walletRepository.findByIdWithLock(userId)).thenReturn(Optional.of(wallet));

        Result<Wallet> failureResult = Result.failure(GachaErrorCode.INSUFFICIENT_BALANCE);
        when(wallet.consume(anyInt())).thenReturn(failureResult);

        // When
        Result<GachaDto.DrawResponse> result = gachaService.drawGacha(userId, request);

        // Then
        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(GachaErrorCode.INSUFFICIENT_BALANCE);

        // ロールバック確認
        verify(transactionStatus, times(1)).setRollbackOnly();
    }

    @Test
    @DisplayName("【異常系】抽選ロジックが失敗した場合、エラーを返し、ロールバックをマークする")
    void shouldRollbackWhenLotteryFails() {
        // Given
        when(poolRepository.findByIdWithEmissions(poolId)).thenReturn(Optional.of(pool));
        when(pool.isOpen()).thenReturn(true);
        when(pool.getEmissions()).thenReturn(Collections.emptyList());

        when(walletRepository.findByIdWithLock(userId)).thenReturn(Optional.of(wallet));
        when(wallet.consume(anyInt())).thenReturn(Result.success(wallet));

        GachaState state = mock(GachaState.class);
        when(stateRepository.findByUserAndPool(userId, poolId)).thenReturn(Optional.of(state));

        when(lotteryService.draw(any())).thenReturn(Result.failure(GachaErrorCode.INTERNAL_ERROR));

        // When
        Result<GachaDto.DrawResponse> result = gachaService.drawGacha(userId, request);

        // Then
        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(GachaErrorCode.INTERNAL_ERROR);

        verify(transactionStatus, times(1)).setRollbackOnly();
        verify(eventPublisher, never()).publishEvent(any());
    }
}