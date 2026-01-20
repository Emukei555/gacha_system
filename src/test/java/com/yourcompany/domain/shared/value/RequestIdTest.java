package com.yourcompany.domain.shared.value;

import com.sqlcanvas.sharedkernel.shared.error.CommonErrorCode; // 追加
import com.sqlcanvas.sharedkernel.shared.result.Result; // 追加
import com.sqlcanvas.sharedkernel.shared.util.RequestId; // パッケージ変更
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdTest {

    @Test
    @DisplayName("正常系: 有効なUUID文字列からRequestIdを生成できる")
    void testValid() {
        String uuidStr = UUID.randomUUID().toString();
        Result<RequestId> result = RequestId.from(uuidStr);

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.orElseThrow(failure -> new RuntimeException(failure.message())).toString()).isEqualTo(uuidStr);
    }

    @Test
    @DisplayName("異常系: 不正なフォーマットの場合は失敗する")
    void testInvalidFormat() {
        String invalidUuid = "invalid-uuid-string";
        Result<RequestId> result = RequestId.from(invalidUuid);

        assertThat(result).isInstanceOf(Result.Failure.class);
        // GachaErrorCode -> CommonErrorCode に変更
        assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("異常系: 空文字は許可しない")
    void testEmpty() {
        Result<RequestId> result = RequestId.from("");

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("正常系: 新規生成できる")
    void testGenerate() {
        RequestId requestId = RequestId.generate();
        assertThat(requestId).isNotNull();
        // UUIDとしてパースできるか確認
        UUID.fromString(requestId.toString());
    }
}