package com.yourcompany.web.dto;

import java.util.List;
import java.util.UUID;

public class GachaDto {

    // リクエスト: ガチャを引く
    public record DrawRequest(
            UUID poolId,
            int drawCount // 1 or 10
    ) {}

    // レスポンス: ガチャ実行結果
    public record DrawResponse(
            String transactionId,
            long totalPaidConsumed,
            long totalFreeConsumed,
            List<EmissionItem> results
    ) {}

    // 排出アイテム詳細
    public record EmissionItem(
            UUID itemId,
            String name,
            String rarity,
            boolean isNew, // 新規獲得かどうか (Phase 3用)
            int quantity
    ) {}
}