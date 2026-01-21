package com.yourcompany.features.gacha.draw;

import java.util.UUID;

public record DrawGachaRequest(
        UUID poolId,
        int drawCount
) {}