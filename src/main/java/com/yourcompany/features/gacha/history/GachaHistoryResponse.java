package com.yourcompany.features.gacha.history;

import java.time.Instant;
import java.util.UUID;

public record GachaHistoryResponse(
        String transactionId,
        UUID poolId,
        int consumedPaid,
        int consumedFree,
        String emissionResultsJson, // 簡易的にJSON文字列のまま返す（必要に応じてパース済みオブジェクトへ）
        Instant executedAt
) {}