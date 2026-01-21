package com.yourcompany.web.exception;

import com.sqlcanvas.sharedkernel.shared.error.ErrorCode;
import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.exception.GachaException;
import com.yourcompany.web.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 1. ドメイン層で意図的に投げられたビジネス例外 (GachaException)
     * MDCにリクエスト情報が入っているため、ログには「エラーコード」と「メッセージ」だけで十分です。
     */
    @ExceptionHandler(GachaException.class)
    public ResponseEntity<ErrorResponse> handleGachaException(GachaException ex) {
        ErrorCode errorCode = ex.getErrorCode();

        // ビジネス例外は想定内なのでスタックトレースは出さず、WARNレベルで留める
        log.warn("Business Error: code={}, message={}", errorCode.getCode(), ex.getMessage());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.getCode(), ex.getMessage()));
    }

    /**
     * 2. バリデーションエラー (@Valid / @Validated)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<ErrorResponse.ValidationError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorResponse.ValidationError(e.getField(), e.getDefaultMessage()))
                .toList();

        // 詳細はレスポンスボディに含まれるため、ログは件数のみでシンプルに
        log.warn("Validation Error: count={}", details.size());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("C001", "入力内容に誤りがあります。", details));
    }

    /**
     * 3. JSONパースエラー
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleJsonException(HttpMessageNotReadableException ex) {
        log.warn("JSON Parse Error: {}", ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse("C002", "リクエスト形式が不正です。"));
    }

    /**
     * 4. 404 Not Found
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handle404(NoResourceFoundException ex) {
        log.warn("Resource Not Found: {}", ex.getResourcePath());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SYS-404", "指定されたリソースが見つかりません。"));
    }

    /**
     * 5. その他の予期せぬエラー
     * ここは必ずスタックトレースを出して、原因究明できるようにします。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex) {
        // MDCがあるため、RequestURIなどを手動で出す必要はない
        // ERRORレベルでスタックトレースを出力
        log.error("Unexpected System Error", ex);

        GachaErrorCode internal = GachaErrorCode.UNEXPECTED_ERROR;
        return ResponseEntity
                .status(internal.getStatus())
                .body(new ErrorResponse(internal.getCode(), internal.getDefaultMessage()));
    }
}