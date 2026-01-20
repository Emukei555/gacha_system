package com.yourcompany.web.exception;

// ★重要: ライブラリのインターフェースをインポート
import com.sqlcanvas.sharedkernel.shared.error.ErrorCode;

import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.exception.GachaException;
import com.yourcompany.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
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
     */
    @ExceptionHandler(GachaException.class)
    public ResponseEntity<ErrorResponse> handleGachaException(GachaException ex, HttpServletRequest request) {
        // ★修正ポイント: GachaErrorCode型 ではなく、より広い ErrorCode型 で受け取る
        ErrorCode errorCode = ex.getErrorCode();

        log.warn("Business Exception: code={}, message={}, path={}",
                errorCode.getCode(), ex.getMessage(), request.getRequestURI());

        ErrorResponse body = new ErrorResponse(errorCode.getCode(), ex.getMessage());
        return ResponseEntity.status(errorCode.getStatus()).body(body);
    }

    /**
     * 2. バリデーションエラー (@Valid / @Validated)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<ErrorResponse.ValidationError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ErrorResponse.ValidationError(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                ))
                .toList();

        log.warn("Validation Error: count={}", details.size());

        ErrorResponse body = new ErrorResponse(
                "C001",
                "入力内容に誤りがあります。",
                details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 3. JSONパースエラー
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleJsonException(HttpMessageNotReadableException ex) {
        log.warn("JSON Parse Error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse("C002", "リクエスト形式が不正です。"));
    }

    /**
     * 4. 404 Not Found
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handle404(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SYS-404", "指定されたリソースが見つかりません。"));
    }

    /**
     * 5. その他の予期せぬエラー
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected System Error: path={}", request.getRequestURI(), ex);

        // ここは具体的なEnum (GachaErrorCode) を指定してOK
        GachaErrorCode internalError = GachaErrorCode.UNEXPECTED_ERROR;

        ErrorResponse body = new ErrorResponse(
                internalError.getCode(),
                internalError.getDefaultMessage()
        );
        return ResponseEntity.status(internalError.getStatus()).body(body);
    }
}