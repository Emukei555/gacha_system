package com.yourcompany.schoolasset.infrastructure.persistence.repository;

import com.yourcompany.domain.model.inventory.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    @Query("SELECT i FROM InventoryItem i WHERE i.userId = :userId AND i.itemId = :itemId")
    Optional<InventoryItem> findByUserAndItem(@Param("userId") UUID userId, @Param("itemId") UUID itemId);
}
