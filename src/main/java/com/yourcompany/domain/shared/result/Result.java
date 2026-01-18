package com.yourcompany.domain.shared.result;

import com.yourcompany.domain.shared.exception.GachaErrorCode;

public sealed interface Result<T> {

    record Success<T>(T value) implements Result<T> {}

    record Failure<T>(GachaErrorCode errorCode, String message) implements Result<T> {}

    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    /**
     * デフォルトメッセージを使用して失敗を返す
     */
    static <T> Result<T> failure(GachaErrorCode errorCode) {
        return new Failure<>(errorCode, errorCode.getDefaultMessage());
    }

    /**
     * カスタムメッセージ（詳細情報）を添えて失敗を返す
     */
    static <T> Result<T> failure(GachaErrorCode errorCode, String customMessage) {
        return new Failure<>(errorCode, customMessage);
    }
}