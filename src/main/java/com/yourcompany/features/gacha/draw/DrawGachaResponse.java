package com.yourcompany.features.gacha.draw;

import com.sqlcanvas.sharedkernel.shared.util.RequestId;


import java.util.List;
import java.util.UUID;

public record DrawGachaResponse(
        String transactionId,
        long totalPaidConsumed,
        long totalFreeConsumed,
        List<EmissionItem> results
) {
    public record EmissionItem(
            UUID itemId,
            String name,
            String rarity,
            boolean isNew,
            int quantity
    ) {}
}