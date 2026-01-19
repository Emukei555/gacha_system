package com.yourcompany.domain.shared.exception;

import com.yourcompany.domain.shared.result.Result;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum GachaErrorCode {
    // --- 共通・バリデーション ---
    INVALID_PARAMETER("C001", HttpStatus.BAD_REQUEST, "不正なパラメータです。"),

    // --- ユーザー・資産関連 (G) ---
    INSUFFICIENT_BALANCE("G001", HttpStatus.PAYMENT_REQUIRED, "石が不足しています。"),
    WALLET_NOT_FOUND("G002", HttpStatus.NOT_FOUND, "ウォレットが見つかりません。"),
    INVENTORY_OVERFLOW("G003", HttpStatus.UNPROCESSABLE_ENTITY, "所持上限を超えています。"),

    // --- ガチャ仕様・期間関連 (P) ---
    GACHA_POOL_EXPIRED("P001", HttpStatus.GONE, "開催期間外、または存在しないガチャです。"),
    INVALID_WEIGHT_CONFIG("P002", HttpStatus.INTERNAL_SERVER_ERROR, "確率設定に誤りがあります。"),

    // --- システム・整合性 (SYS) ---
    CONCURRENT_UPDATE_FAILURE("SYS-001", HttpStatus.CONFLICT, "他端末での操作と競合しました。再度お試しください。"),
    DUPLICATE_REQUEST("SYS-002", HttpStatus.CONFLICT, "リクエストが重複しています。"),
    INTERNAL_ERROR("SYS-500", HttpStatus.INTERNAL_SERVER_ERROR, "サーバー内部でエラーが発生しました。"),
    UNEXPECTED_ERROR("SYS-999", HttpStatus.INTERNAL_SERVER_ERROR, "予期せぬエラーが発生しました。");

    private final String code;
    private final HttpStatus status;
    private final String defaultMessage;

    GachaErrorCode(String code, HttpStatus status, String defaultMessage) {
        this.code = code;
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    /**
     * Failureを生成（デフォルトメッセージ）
     */
    public <T> Result.Failure<T> toFailure() {
        return new Result.Failure<>(this, this.defaultMessage);
    }

    /**
     * ★今回必要なやつ：カスタムメッセージを受け取って Failure を返す
     * 例: GachaErrorCode.INSUFFICIENT_BALANCE.toFailure("有償石が不足しています（必要: 150, 所持: 100）")
     */
    public <T> Result.Failure<T> toFailure(String customMessage) {
        return new Result.Failure<>(this, customMessage);
    }

    /**
     * 動的な値を埋め込む (例: "残り: %d")
     */
    public <T> Result.Failure<T> withArgs(Object... args) {
        return new Result.Failure<>(this, String.format(this.defaultMessage, args));
    }
}