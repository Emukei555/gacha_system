package com.yourcompany.domain.shared.exception;

import com.sqlcanvas.sharedkernel.shared.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GachaErrorCode implements ErrorCode {

    // --- ユーザー・資産関連 (G) ---
    // 石不足: PAYMENT_REQUIRED (402) が適しています
    INSUFFICIENT_BALANCE("GACHA-G001", "石が不足しています", HttpStatus.PAYMENT_REQUIRED),

    // ウォレットが見つからない
    WALLET_NOT_FOUND("GACHA-G002", "ウォレットが見つかりません", HttpStatus.NOT_FOUND),

    // 所持上限オーバー
    INVENTORY_OVERFLOW("GACHA-G003", "所持上限を超えています", HttpStatus.UNPROCESSABLE_ENTITY),

    // 在庫切れ (排他制御などで負けたり、数に限りのあるガチャの場合)
    OUT_OF_STOCK("GACHA-G004", "在庫切れです", HttpStatus.CONFLICT),

    // --- ガチャ仕様・期間関連 (P) ---
    // 期間外・存在しない
    GACHA_POOL_EXPIRED("GACHA-P001", "開催期間外、または存在しないガチャです", HttpStatus.GONE),

    // 設定ミス (運営側のミスなので 500)
    INVALID_WEIGHT_CONFIG("GACHA-P002", "確率設定に誤りがあります", HttpStatus.INTERNAL_SERVER_ERROR),

    // --- システム・整合性 (SYS) ---
    // 楽観ロック競合など
    CONCURRENT_UPDATE_FAILURE("GACHA-SYS-001", "他端末での操作と競合しました。再度お試しください", HttpStatus.CONFLICT),

    // リクエスト重複 (冪等性チェック)
    DUPLICATE_REQUEST("GACHA-SYS-002", "リクエストが重複しています", HttpStatus.CONFLICT),

    // 予期せぬエラー
    UNEXPECTED_ERROR("GACHA-500", "予期しないエラーが発生しました", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus status;
}