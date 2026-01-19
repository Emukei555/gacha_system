package com.yourcompany.domain.shared.result;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.exception.GachaException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 処理の成功または失敗を表す結果型 (Railway Oriented Programming の基盤)。
 * <p>
 * 成功時は {@link Success}、失敗時は {@link Failure} を返します。
 * メソッドチェーンを使用して、エラー処理を分岐させずに記述可能です。
 * </p>
 *
 * @param <T> 成功時に保持する値の型
 */
public sealed interface Result<T> {

    /**
     * 成功を表すレコード。
     *
     * @param value 成功時の戻り値
     * @param <T>   値の型
     */
    record Success<T>(T value) implements Result<T> {}

    /**
     * 失敗を表すレコード。
     *
     * @param errorCode エラーコード
     * @param message   エラーメッセージ
     * @param <T>       値の型（失敗時は値を持たないため、型合わせ用）
     */
    record Failure<T>(GachaErrorCode errorCode, String message) implements Result<T> {}

    /**
     * 成功結果を作成します。
     *
     * @param value 成功時の値
     * @param <T>   値の型
     * @return {@link Success} インスタンス
     */
    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    /**
     * デフォルトメッセージを使用して失敗結果を作成します。
     *
     * @param errorCode エラーコード
     * @param <T>       値の型
     * @return {@link Failure} インスタンス
     */
    static <T> Result<T> failure(GachaErrorCode errorCode) {
        return new Failure<>(errorCode, errorCode.getDefaultMessage());
    }

    /**
     * カスタムメッセージ（詳細情報）を添えて失敗結果を作成します。
     *
     * @param errorCode     エラーコード
     * @param customMessage 詳細なエラーメッセージ
     * @param <T>           値の型
     * @return {@link Failure} インスタンス
     */
    static <T> Result<T> failure(GachaErrorCode errorCode, String customMessage) {
        return new Failure<>(errorCode, customMessage);
    }

    // --- Railway Oriented Methods ---

    /**
     * 成功時のみ、値を変換します。
     * <p>
     * 失敗時は変換を行わず、エラー情報をそのまま伝播します。
     * </p>
     *
     * @param mapper 値を変換する関数 (T -> U)
     * @param <U>    変換後の型
     * @return 変換後の {@link Result}
     */
    default <U> Result<U> map(Function<? super T, ? extends U> mapper) {
        if (this instanceof Success<T> s) {
            return success(mapper.apply(s.value()));
        } else if (this instanceof Failure<T> f) {
            return failure(f.errorCode(), f.message());
        }
        throw new IllegalStateException("Unknown Result type");
    }

    /**
     * 成功時のみ、次の処理（Resultを返す処理）を連結します。
     * <p>
     * バリデーションチェーンなど、失敗する可能性のある処理を繋ぐ場合に使用します。
     * </p>
     *
     * @param mapper 次の処理を実行する関数 (T -> Result<U>)
     * @param <U>    次の処理の成功時の型
     * @return チェーン実行後の {@link Result}
     */
    default <U> Result<U> flatMap(Function<? super T, ? extends Result<U>> mapper) {
        if (this instanceof Success<T> s) {
            return mapper.apply(s.value());
        } else if (this instanceof Failure<T> f) {
            return failure(f.errorCode(), f.message());
        }
        throw new IllegalStateException("Unknown Result type");
    }

    /**
     * 成功時のみ、副作用（ログ出力など）を実行します。
     * <p>
     * 値は変更せず、そのまま次の処理に渡します。
     * </p>
     *
     * @param action 実行する副作用
     * @return 元の {@link Result} (this)
     */
    default Result<T> tap(Consumer<? super T> action) {
        if (this instanceof Success<T> s) {
            action.accept(s.value());
        }
        return this;
    }

    /**
     * 失敗時のみ、副作用（エラーログ出力など）を実行します。
     * <p>
     * 処理の結果には影響を与えません。
     * </p>
     *
     * @param action 実行する副作用
     * @return 元の {@link Result} (this)
     */
    default Result<T> tapFailure(Consumer<Failure<T>> action) {
        if (this instanceof Failure<T> f) {
            action.accept(f);
        }
        return this;
    }

    /**
     * 成功時は値を返し、失敗時は例外をスローします。
     * <p>
     * 主にテストコードや、トランザクションロールバックのためにService層で強制的に値を
     * 取り出す場合に使用します。
     * </p>
     *
     * @return 成功時の値
     * @throws GachaException 失敗時にスローされる例外
     */
    default T unwrap() {
        if (this instanceof Success<T> s) {
            return s.value();
        } else if (this instanceof Failure<T> f) {
            throw new GachaException(f.errorCode());
        }
        throw new IllegalStateException("Unknown Result type");
    }
}