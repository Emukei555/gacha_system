package com.yourcompany.domain.shared.value;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdTest {

    @Test
    @DisplayName("【正常系】generate() が有効なUUIDを生成すること")
    void shouldGenerateValidUuid() {
        RequestId id1 = RequestId.generate();
        RequestId id2 = RequestId.generate();

        assertThat(id1).isNotNull();
        assertThat(id1.value()).isNotNull();
        // 連続生成しても異なるIDであること
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("【異常系】from() に不正な文字列を渡すと INVALID_PARAMETER エラーになること")
    void shouldFailFromInvalidString() {
        String invalidUuid = "not-a-uuid-string";

        Result<RequestId> result = RequestId.from(invalidUuid);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(GachaErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("【異常系】from() に空文字を渡すと INVALID_PARAMETER エラーになること")
    void shouldFailFromEmptyString() {
        Result<RequestId> result = RequestId.from("");

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(GachaErrorCode.INVALID_PARAMETER);
    }
}