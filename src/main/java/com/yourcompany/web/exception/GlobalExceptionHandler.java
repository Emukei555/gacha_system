package com.yourcompany.web.exception;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.exception.GachaException;
import com.yourcompany.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
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
     * Result型で処理しきれなかった、あるいは強制中断させたエラー
     */
    @ExceptionHandler(GachaException.class)
    public ResponseEntity<ErrorResponse> handleGachaException(GachaException ex, HttpServletRequest request) {
        GachaErrorCode errorCode = ex.getErrorCode();

        // 意図したエラーなので WARN レベルでログ出力
        log.warn("Business Exception: code={}, message={}, path={}",
                errorCode.getCode(), ex.getMessage(), request.getRequestURI());

        ErrorResponse body = new ErrorResponse(errorCode.getCode(), ex.getMessage());
        return ResponseEntity.status(errorCode.getStatus()).body(body);
    }

    /**
     * 2. バリデーションエラー (@Valid / @Validated)
     * 入力値がおかしい場合（400 Bad Request）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        // エラー詳細をリスト化
        List<ErrorResponse.ValidationError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ErrorResponse.ValidationError(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                ))
                .toList();

        log.warn("Validation Error: count={}", details.size());

        // バリデーションエラー用の共通コード（適宜 GachaErrorCode に追加してもOK）
        ErrorResponse body = new ErrorResponse(
                "C001",
                "入力内容に誤りがあります。",
                details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 3. JSONパースエラー (Bodyが壊れている、型が違うなど)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleJsonException(HttpMessageNotReadableException ex) {
        log.warn("JSON Parse Error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse("C002", "リクエスト形式が不正です。"));
    }

    /**
     * 4. 404 Not Found (URL間違いなど)
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handle404(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SYS-404", "指定されたリソースが見つかりません。"));
    }

    /**
     * 5. その他の予期せぬエラー (NullPointerException, DB接続切れなど)
     * ★ここが「最後の砦」です。スタックトレースはログに出し、ユーザーには汎用エラーを返します。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        // 重大なエラーなので ERROR レベルでスタックトレースごとログ出力
        log.error("Unexpected System Error: path={}", request.getRequestURI(), ex);

        // クライアントには詳細を見せず、GachaErrorCode.UNEXPECTED_ERROR の内容を返す
        GachaErrorCode internalError = GachaErrorCode.UNEXPECTED_ERROR;

        ErrorResponse body = new ErrorResponse(
                internalError.getCode(),
                internalError.getDefaultMessage()
        );
        return ResponseEntity.status(internalError.getStatus()).body(body);
    }
}