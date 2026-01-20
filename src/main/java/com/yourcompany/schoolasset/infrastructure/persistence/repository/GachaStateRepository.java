package com.yourcompany.schoolasset.infrastructure.persistence.repository;

import com.yourcompany.domain.model.gacha.GachaState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GachaStateRepository extends JpaRepository<GachaState, UUID> { // IDは複合キーだが簡易的にUUID指定
    @Query("SELECT s FROM GachaState s WHERE s.userId = :userId AND s.gachaPoolId = :poolId")
    Optional<GachaState> findByUserAndPool(@Param("userId") UUID userId, @Param("poolId") UUID poolId);
}