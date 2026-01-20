package com.yourcompany.domain.model.gacha.event;

import com.yourcompany.domain.model.gacha.EmissionResult;
import com.yourcompany.domain.shared.value.RequestId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ドメインイベント: ガチャ実行完了
 * 責務: ガチャの結果（誰が、何を、どれだけ消費して引いたか）を保持する。
 * これを受け取ったリスナーが、インベントリ付与や履歴保存を行う。
 */
public record GachaDrawnEvent(
        RequestId requestId,
        UUID userId,
        UUID poolId,
        int consumedPaid,
        int consumedFree,
        List<EmissionResult> results,
        Instant occurredAt
) {
    public GachaDrawnEvent(RequestId requestId, UUID userId, UUID poolId, int consumedPaid, int consumedFree, List<EmissionResult> results) {
        this(requestId, userId, poolId, consumedPaid, consumedFree, results, Instant.now());
    }
}