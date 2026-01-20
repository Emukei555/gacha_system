package com.yourcompany.schoolasset.infrastructure.persistence.repository;

import com.yourcompany.domain.model.history.GachaTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GachaTransactionRepository extends JpaRepository<GachaTransaction, UUID> {
}
