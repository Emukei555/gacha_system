package com.yourcompany.domain.shared.value;

import java.util.UUID;

/**
 * 冪等性（Idempotency）キーとなるリクエストID
 */
public record RequestId(UUID value) {

    public static RequestId generate() {
        // 【日本語ロジック】
        // 1. UUID v7 (時系列ソート可能なUUID) を生成して返す
        //    (RPN対策: DBインデックス性能向上と、ログの時系列追跡の容易化)
        // TODO: 実装
        return null;
    }

    public static RequestId from(String uuidString) {
        // 【日本語ロジック】
        // 1. 文字列がUUID形式かチェック
        // 2. 不正なら IllegalArgumentException または 専用のエラーを返す
        // TODO: 実装
        return null;
    }
}