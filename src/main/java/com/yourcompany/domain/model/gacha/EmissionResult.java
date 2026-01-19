package com.yourcompany.domain.model.gacha;

import java.util.UUID;

/**
 * ガチャ排出結果詳細 (Value Object / DTO)
 * DBの transaction.emission_results (JSONB) にリストとして保存される
 */
public record EmissionResult(
        UUID itemId,
        String itemName,
        String rarity,
        boolean isPickup,
        EmissionType emissionType // NORMAL, GUARANTEED, PITY
) {
    public enum EmissionType {
        NORMAL,     // 通常枠
        GUARANTEED, // 確定枠（SR以上確定など）
        PITY        // 天井枠
    }
}
