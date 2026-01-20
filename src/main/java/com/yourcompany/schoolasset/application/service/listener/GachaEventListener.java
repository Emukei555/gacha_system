package com.yourcompany.schoolasset.application.service.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.domain.model.gacha.EmissionResult;
import com.yourcompany.domain.model.gacha.event.GachaDrawnEvent;
import com.yourcompany.domain.model.history.GachaTransaction;
import com.yourcompany.domain.model.inventory.InventoryItem;
import com.yourcompany.domain.shared.exception.GachaErrorCode;
import com.yourcompany.domain.shared.exception.GachaException;
import com.yourcompany.domain.shared.result.Result;
import com.yourcompany.schoolasset.infrastructure.persistence.repository.GachaTransactionRepository;
import com.yourcompany.schoolasset.infrastructure.persistence.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class GachaEventListener {

    private final InventoryItemRepository inventoryRepository;
    private final GachaTransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    /**
     * インベントリへのアイテム付与
     * トランザクションコミット直前 (BEFORE_COMMIT) に実行
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onGachaDrawn_GrantItems(GachaDrawnEvent event) {
        log.debug("Event received: Granting items for request={}", event.requestId());

        // TODO: マスタから最大所持数を取得するロジックが必要
        int maxCapacity = 9999;

        for (EmissionResult result : event.results()) {
            InventoryItem item = inventoryRepository.findByUserAndItem(event.userId(), result.itemId())
                    .orElseGet(() -> InventoryItem.create(event.userId(), result.itemId()));

            Result<InventoryItem> addResult = item.addQuantity(1, maxCapacity);

            if (addResult instanceof Result.Failure<InventoryItem> f) {
                // ここで例外を投げると、大元の drawGacha トランザクション全体がロールバックされる
                log.error("Failed to grant item. userId={}, itemId={}, error={}", event.userId(), result.itemId(), f.message());
                throw new GachaException(f.errorCode());
            }

            inventoryRepository.save(item);
        }
    }

    /**
     * ガチャ履歴の保存
     * トランザクションコミット直前 (BEFORE_COMMIT) に実行
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onGachaDrawn_RecordHistory(GachaDrawnEvent event) {
        log.debug("Event received: Recording history for request={}", event.requestId());

        try {
            String jsonResult = objectMapper.writeValueAsString(event.results());

            GachaTransaction transaction = GachaTransaction.record(
                    event.requestId(),
                    event.userId(),
                    event.poolId(),
                    event.consumedPaid(),
                    event.consumedFree(),
                    jsonResult
            );

            transactionRepository.save(transaction);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize emission results", e);
            throw new GachaException(GachaErrorCode.INTERNAL_ERROR);
        }
    }
}