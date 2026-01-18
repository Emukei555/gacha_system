# Gacha System Implementation (DDD Practice)

Java 21 と Spring Boot 3.x/4.x を活用し、ドメイン駆動設計（DDD）の実践を目的とした堅牢なガチャシステムのリファレンス実装です。

## 🎯 プロジェクトの目的
「17歳、高校2年生でバックエンドエンジニアを目指す」という目標に向け、実務レベルの設計・実装スキルを証明するためのポートフォリオです。単に動くだけでなく、金融システム並みのデータ整合性と、保守性の高いコードベースを目指しています。

## 🛠 技術スタック
- **Language:** Java 21 (record, sealed interface, pattern matching)
- **Framework:** Spring Boot 4.0.1
- **Database:** PostgreSQL (with Stored Procedures, Triggers, CHECK Constraints)
- **Architecture:** Domain-Driven Design (DDD) / Hexagonal Architecture
- **Error Handling:** Result Pattern (Railway Oriented Programming)

## ✨ 設計のこだわり

### 1. 不変条件の徹底的な保護
ドメインモデル（Entity/VO）とDBレイヤー（CHECK制約・トリガー）の両面でガードを固めています。
- 「ウォレット残高が負にならない」
- 「ガチャの排出確率合計が厳密に100%（10000/10000）である」
といったビジネスルール（不変条件）をシステム全体で保証します。

### 2. Result パターンによる型安全なエラーハンドリング
例外（Exception）を投げっぱなしにするのではなく、`Result<T>` 型を戻り値として使用しています。
これにより、呼び出し側はコンパイルレベルで「成功」と「失敗」の両方のハンドリングを強制され、不当な状態のまま処理が続行される（不変条件が壊れる）ことを物理的に防ぎます。

### 3. 誤差ゼロの整数ウェイト抽選アルゴリズム
浮動小数点数（float/double）を一切使わず、整数（Weight）による累積減算方式を採用しています。
これにより、計算誤差による確率の不整合を排除した、公平で正確な抽選を実現しています。

## 📂 パッケージ構成 (DDD)
```text
src/main/java/com/yourcompany/
├── domain/                # ドメイン層 (ビジネスロジックの核)
│   ├── model/             # Entity, Value Object, Aggregate Root
│   ├── service/           # Domain Services (LotteryService等)
│   ├── shared/            # Result型, 共通ErrorCode
│   └── repository/        # Repository Interfaces
├── application/           # アプリケーション層 (ユースケース)
├── infrastructure/        # インフラストラクチャ層 (DB実装, API通信)
└── web/                   # プレゼンテーション層 (Controller, GlobalExceptionHandler)
```

## ER図
```mermaid
erDiagram
    %% ==========================================
    %% ユーザー資産・状態管理 (User Domain)
    %% ==========================================
    wallets {
        uuid user_id PK "アプリ側ID"
        integer paid_stones "CHECK(0..99999999)"
        integer free_stones "CHECK(0..99999999)"
        bigint version "楽観ロック"
        timestamptz updated_at
    }

    user_items {
        uuid user_id PK, FK
        uuid item_id PK, FK
        integer quantity "CHECK(>=0) & Trigger上限"
        bigint version
        timestamptz updated_at
    }

    user_gacha_states {
        uuid user_id PK, FK
        uuid gacha_pool_id PK, FK
        integer current_pity_count "天井カウント CHECK(0..9999)"
        integer current_guaranteed_count "確定枠カウント CHECK(0..9999)"
        timestamptz updated_at
    }

    %% ==========================================
    %% ガチャ定義・マスタ (Master Data Domain)
    %% ==========================================
    gacha_pools {
        uuid id PK "UUID v7"
        varchar_50 name "CHECK(len>0)"
        timestamptz start_at
        timestamptz end_at "CHECK(end > start)"
        integer cost_amount "CHECK(1..10000)"
        integer pity_ceiling_count "DEFAULT 0"
        integer guaranteed_trigger_count "DEFAULT 0"
    }

    items {
        uuid id PK "UUID v7"
        varchar_50 name "CHECK(len>0)"
        varchar_20 rarity "CHECK IN(SSR, SR...)"
        integer max_capacity "CHECK(1..999999)"
        timestamptz created_at
    }

    gacha_emissions {
        uuid id PK "UUID v7"
        uuid gacha_pool_id FK
        uuid item_id FK
        integer weight "CHECK(>0) & Trigger合計検証"
        boolean is_pickup
    }

    %% ==========================================
    %% 履歴・監査 (History & Audit Domain)
    %% ==========================================
    gacha_transactions {
        timestamptz executed_at PK "Partition Key"
        uuid id PK "UUID v7 / RequestID"
        uuid user_id FK "論理参照"
        uuid gacha_pool_id FK "論理参照"
        integer consumed_paid
        integer consumed_free
        jsonb emission_results "排出結果リスト(正規化廃止)"
    }

    audit_logs {
        uuid id PK "UUID v7"
        varchar target_table
        text record_id
        varchar operation
        jsonb old_data "変更前完全記録"
        jsonb new_data "変更後完全記録"
        timestamptz changed_at
    }

    %% ==========================================
    %% リレーション定義
    %% ==========================================
    
    %% User -> Inventory / State
    wallets ||--o{ user_items : "所持"
    wallets ||--o{ user_gacha_states : "状態管理"
    
    %% Master Connections
    items ||--o{ user_items : "定義参照"
    items ||--o{ gacha_emissions : "排出対象"
    gacha_pools ||--o{ gacha_emissions : "構成要素"
    gacha_pools ||--o{ user_gacha_states : "進捗対象"

    %% Transaction Connections (Logical FKs in partitioning)
    wallets ||--o{ gacha_transactions : "実行ログ"
    gacha_pools ||--o{ gacha_transactions : "実行プール"
