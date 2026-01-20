package com.yourcompany.domain.shared.exception;

// ★ライブラリのErrorCodeをインポート
import com.sqlcanvas.sharedkernel.shared.error.ErrorCode;
import lombok.Getter;

@Getter
public class GachaException extends RuntimeException {

    private final ErrorCode errorCode;

    // ★引数を GachaErrorCode から ErrorCode (インターフェース) に変更
    public GachaException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    // 必要に応じてメッセージを上書きするコンストラクタも追加可能
    public GachaException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}