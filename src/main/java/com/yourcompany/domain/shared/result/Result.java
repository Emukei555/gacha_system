package com.yourcompany.domain.shared.result;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.exception.GachaException;

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

    /**
     * 【追加】成功時は値を返し、失敗時は例外を投げる
     * テストコードや、確実に成功しているとわかっている箇所でのみ使用推奨
     */
    default T unwrap() {
        if (this instanceof Success<T> s) {
            return s.value();
        } else if (this instanceof Failure<T> f) {
            // 失敗時は GachaException にラップして投げることで、スタックトレースから原因を追えるようにする
            throw new GachaException(f.errorCode());
        }
        throw new IllegalStateException("Unknown Result type");
    }
}