package com.yourcompany.schoolasset.infrastructure.persistence.repository;

import com.yourcompany.domain.model.gacha.GachaPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GachaPoolRepository extends JpaRepository<GachaPool, UUID> {

    /**
     * ガチャ実行用：Pool情報と排出設定(Emissions)をまとめて取得する
     * N+1問題を回避し、不変条件チェックに必要なデータを一括ロードする
     */
    @Query("SELECT p FROM GachaPool p LEFT JOIN FETCH p.emissions WHERE p.id = :id")
    Optional<GachaPool> findByIdWithEmissions(@Param("id") UUID id);

    // 通常の findById は管理画面などでPool単体の情報だけ欲しい時に使う
}