-- ==========================================================
-- Gacha System Schema (Refactored for DDD & High Integrity)
-- ==========================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. UUID v7 生成関数
CREATE OR REPLACE FUNCTION gen_uuid_v7() RETURNS uuid AS $$
DECLARE
    v_time timestamp with time zone := clock_timestamp();
    v_secs bigint := EXTRACT(EPOCH FROM v_time);
    v_msec bigint := mod(EXTRACT(MILLISECONDS FROM v_time)::numeric * 10^3, 10^3)::bigint;
    v_uuid uuid := gen_random_uuid();
    v_hex text;
BEGIN
    v_hex := lpad(to_hex((v_secs * 1000 + v_msec)::bigint), 12, '0');
    return (v_hex || substr(v_uuid::text, 14))::uuid;
END;
$$ LANGUAGE plpgsql;

-- ==========================================================
-- 2. 認証・ユーザー基盤
-- ==========================================================

CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT gen_uuid_v7(),
                       email VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       role VARCHAR(50) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ==========================================================
-- 3. マスタデータ
-- ==========================================================

-- アイテムマスタ
CREATE TABLE items (
                       id UUID PRIMARY KEY DEFAULT gen_uuid_v7(),
                       name VARCHAR(50) NOT NULL CHECK (length(name) > 0),
                       rarity VARCHAR(20) NOT NULL CHECK (rarity IN ('COMMON', 'RARE', 'SR', 'SSR', 'UR', 'LR')),
                       max_capacity INTEGER NOT NULL DEFAULT 9999 CHECK (max_capacity BETWEEN 1 AND 999999),
                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ガチャプール定義
CREATE TABLE gacha_pools (
                             id UUID PRIMARY KEY DEFAULT gen_uuid_v7(),
                             name VARCHAR(50) NOT NULL CHECK (length(name) > 0),
                             start_at TIMESTAMP WITH TIME ZONE NOT NULL,
                             end_at TIMESTAMP WITH TIME ZONE NOT NULL,
                             cost_amount INTEGER NOT NULL CHECK (cost_amount BETWEEN 1 AND 10000),
                             pity_ceiling_count INTEGER NOT NULL DEFAULT 0 CHECK (pity_ceiling_count >= 0),
                             guaranteed_trigger_count INTEGER NOT NULL DEFAULT 0 CHECK (guaranteed_trigger_count >= 0),
                             version BIGINT NOT NULL DEFAULT 0,
                             CONSTRAINT chk_gacha_period CHECK (end_at > start_at)
);

CREATE INDEX idx_gacha_pools_active ON gacha_pools (start_at, end_at);

-- ガチャ排出テーブル
CREATE TABLE gacha_emissions (
                                 id UUID PRIMARY KEY DEFAULT gen_uuid_v7(),
                                 gacha_pool_id UUID NOT NULL REFERENCES gacha_pools(id) ON DELETE CASCADE,
                                 item_id UUID NOT NULL REFERENCES items(id) ON DELETE RESTRICT,
                                 weight INTEGER NOT NULL CHECK (weight > 0),
                                 is_pickup BOOLEAN NOT NULL DEFAULT FALSE,
                                 UNIQUE (gacha_pool_id, item_id)
);

-- [Trigger] Weight合計チェック
CREATE OR REPLACE FUNCTION check_emission_weight_sum() RETURNS TRIGGER AS $$
DECLARE
    total_weight INTEGER;
BEGIN
    SELECT SUM(weight) INTO total_weight FROM gacha_emissions WHERE gacha_pool_id = NEW.gacha_pool_id;
    IF total_weight IS NULL OR total_weight <= 0 THEN
        RAISE EXCEPTION 'INVALID_WEIGHT_SUM: Total weight must be positive';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_weight
    AFTER INSERT OR UPDATE ON gacha_emissions
    FOR EACH ROW EXECUTE FUNCTION check_emission_weight_sum();


-- ==========================================================
-- 4. ユーザー資産・状態
-- ==========================================================

-- ウォレット
CREATE TABLE wallets (
                         user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
                         paid_stones INTEGER NOT NULL DEFAULT 0 CHECK (paid_stones BETWEEN 0 AND 99999999),
                         free_stones INTEGER NOT NULL DEFAULT 0 CHECK (free_stones BETWEEN 0 AND 99999999),
                         version BIGINT NOT NULL DEFAULT 0,
                         updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ユーザー所持アイテム
CREATE TABLE user_items (
                            user_id UUID NOT NULL REFERENCES wallets(user_id) ON DELETE CASCADE,
                            item_id UUID NOT NULL REFERENCES items(id) ON DELETE RESTRICT,
                            quantity INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
                            version BIGINT NOT NULL DEFAULT 0,
                            updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (user_id, item_id)
);

-- [Trigger] 在庫上限チェック
CREATE OR REPLACE FUNCTION check_user_item_capacity() RETURNS TRIGGER AS $$
DECLARE
    limit_val INTEGER;
BEGIN
    SELECT max_capacity INTO limit_val FROM items WHERE id = NEW.item_id;
    IF NEW.quantity > limit_val THEN
        RAISE EXCEPTION 'INVENTORY_OVERFLOW: Item % quantity % exceeds limit %', NEW.item_id, NEW.quantity, limit_val;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_user_item_capacity
    BEFORE INSERT OR UPDATE ON user_items
    FOR EACH ROW EXECUTE FUNCTION check_user_item_capacity();

-- ユーザーガチャ状態
CREATE TABLE user_gacha_states (
                                   user_id UUID NOT NULL REFERENCES wallets(user_id) ON DELETE CASCADE,
                                   gacha_pool_id UUID NOT NULL REFERENCES gacha_pools(id) ON DELETE RESTRICT,
                                   current_pity_count INTEGER NOT NULL DEFAULT 0 CHECK (current_pity_count BETWEEN 0 AND 9999),
                                   current_guaranteed_count INTEGER NOT NULL DEFAULT 0 CHECK (current_guaranteed_count BETWEEN 0 AND 9999),
                                   version BIGINT NOT NULL DEFAULT 0,
                                   updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                   PRIMARY KEY (user_id, gacha_pool_id)
);

-- ==========================================================
-- 5. ガチャ履歴 (Partitioning)
-- ==========================================================
CREATE TABLE gacha_transactions (
                                    request_id VARCHAR(255) NOT NULL,
                                    user_id UUID NOT NULL,
                                    pool_id UUID NOT NULL,  -- ★ここを gacha_pool_id から pool_id に変更
                                    consumed_paid INTEGER NOT NULL CHECK (consumed_paid >= 0),
                                    consumed_free INTEGER NOT NULL CHECK (consumed_free >= 0),
                                    result_json TEXT,
                                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    PRIMARY KEY (created_at, request_id)
) PARTITION BY RANGE (created_at);

-- 初期パーティション
CREATE TABLE gacha_transactions_2026_01 PARTITION OF gacha_transactions FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE gacha_transactions_2026_02 PARTITION OF gacha_transactions FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE gacha_transactions_2026_03 PARTITION OF gacha_transactions FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');

-- ==========================================================
-- 6. 監査ログ
-- ==========================================================
CREATE TABLE audit_logs (
                            id UUID PRIMARY KEY DEFAULT gen_uuid_v7(),
                            target_table VARCHAR(50) NOT NULL,
                            record_id TEXT NOT NULL,
                            operation VARCHAR(10) NOT NULL,
                            old_data JSONB,
                            new_data JSONB,
                            changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE FUNCTION audit_trigger_func() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO audit_logs (target_table, record_id, operation, old_data, new_data)
    VALUES (
               TG_TABLE_NAME,
               COALESCE(NEW.user_id::text, OLD.user_id::text, 'unknown'),
               TG_OP,
               to_jsonb(OLD),
               to_jsonb(NEW)
           );
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_wallets AFTER INSERT OR UPDATE OR DELETE ON wallets FOR EACH ROW EXECUTE FUNCTION audit_trigger_func();
CREATE TRIGGER trg_audit_user_items AFTER INSERT OR UPDATE OR DELETE ON user_items FOR EACH ROW EXECUTE FUNCTION audit_trigger_func();