package com.yourcompany.domain.shared.value;

import com.sqlcanvas.sharedkernel.shared.error.CommonErrorCode;
import com.sqlcanvas.sharedkernel.shared.result.Result;
import com.yourcompany.domain.shared.exception.GachaErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * リクエストID (Value Object)
 * 責務：
 * 1. 冪等性（Idempotency）キーとしての役割
 * 2. 時系列ソート可能なUUID v7（相当）の生成によるDBインデックス性能の向上
 * 3. ログの追跡容易性の確保
 */
@Slf4j
public record RequestId(UUID value) {

    // 暗号論的に強い乱数生成器 (ThreadSafe)
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * UUID v7 (Unix Epoch Time based) を生成して返す
     * Java標準でv7がサポートされるまでのカスタム実装
     */
    public static RequestId generate() {
        // 1. タイムスタンプ (48bit)
        long timestamp = System.currentTimeMillis();

        // 2. ランダム値の生成 (SecureRandomを使用)
        // v7構造: ver(4bit) + rand_a(12bit) + var(2bit) + rand_b(62bit)
        // ここでは簡易実装として、必要なビット分を乱数で埋める
        long randA = secureRandom.nextInt(0xFFF); // 12bit (0 ~ 4095)
        long randB = secureRandom.nextLong() & 0x3FFFFFFFFFFFFFFFL; // 62bit mask

        // 3. 文字列フォーマットで構成 (RFC 9562準拠の形式へ)
        // 8-4-4-4-12 (32 hex digits)
        // time_high_and_version:  timestamp(48bit) の上位32bit - 下位16bit
        // time_low_and_version:   ver(7) + randA(12bit)
        // clock_seq_and_reserved: var(2) + randB上位
        // node:                   randB下位

        String uuidStr = String.format("%08x-%04x-7%03x-%04x-%012x",
                (timestamp >> 16) & 0xFFFFFFFFL,      // 上位32bit
                timestamp & 0xFFFFL,                  // 下位16bit
                randA,                                // ver(7)はフォーマット文字列に埋め込み済み
                (randB >> 48) & 0x3FFF | 0x8000,      // variant(10xx) = 0x8000
                randB & 0xFFFFFFFFFFFFL               // Node相当 (48bit)
        );

        UUID uuid = UUID.fromString(uuidStr);

        // 生成ログ (大量に出るのでTRACEレベル推奨)
        log.trace("Generated RequestId(v7-like): {}", uuid);

        return new RequestId(uuid);
    }

    /**
     * 文字列からRequestIdを生成する
     */
    public static Result<RequestId> from(String uuidString) {
        if (uuidString == null || uuidString.isBlank()) {
            log.warn("RequestId is null or empty.");
            return Result.failure(CommonErrorCode.INVALID_PARAMETER,"IDが空です");
        }

        try {
            UUID uuid = UUID.fromString(uuidString);
            return Result.success(new RequestId(uuid));
        } catch (IllegalArgumentException e) {
            // クライアントからの入力ミスなどが想定されるため WARN
            log.warn("Invalid UUID format received: {}", uuidString);
            return Result.failure(CommonErrorCode.INVALID_PARAMETER,"無効なID形式です: " + uuidString);
        }
    }

    // 文字列表現を返す便宜メソッド
    @Override
    public String toString() {
        return value.toString();
    }
}