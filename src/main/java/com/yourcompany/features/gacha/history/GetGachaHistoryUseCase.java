package com.yourcompany.features.gacha.history;

import com.yourcompany.domain.model.history.GachaTransaction;
import com.yourcompany.schoolasset.infrastructure.persistence.repository.GachaTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetGachaHistoryUseCase {

    private final GachaTransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public Page<GachaHistoryResponse> execute(UUID userId, Pageable pageable) {
        // ※本来はfindByUserIdなどが必要
        Page<GachaTransaction> entities = transactionRepository.findAll(pageable);

        return entities.map(tx -> new GachaHistoryResponse(
                tx.getRequestId(),      // getId() -> getRequestId()
                tx.getPoolId(),         // getGachaPoolId() -> getPoolId()
                tx.getConsumedPaid(),
                tx.getConsumedFree(),
                tx.getResultJson(),     // getEmissionResults() -> getResultJson()
                tx.getCreatedAt()       // getExecutedAt() -> getCreatedAt()
        ));
    }
}