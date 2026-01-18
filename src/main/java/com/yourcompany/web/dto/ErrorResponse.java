package com.yourcompany.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * 統一エラーレスポンス
 * フロントエンドは常にこの形式を期待すれば良い
 */
public record ErrorResponse(
        String code,          // エラーコード (例: "G001", "SYS-500")
        String message,       // ユーザー向けメッセージ
        Instant timestamp,    // 発生時刻
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<ValidationError> details // バリデーションエラー時の詳細（なければJSONに出ない）
) {
    // コンビニエンス・コンストラクタ
    public ErrorResponse(String code, String message) {
        this(code, message, Instant.now(), null);
    }

    public ErrorResponse(String code, String message, List<ValidationError> details) {
        this(code, message, Instant.now(), details);
    }

    // バリデーション詳細用レコード
    public record ValidationError(String field, String message) {}
}