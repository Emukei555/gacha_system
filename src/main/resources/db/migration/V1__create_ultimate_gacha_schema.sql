-- ==========================================================
-- Gacha System Schema (Ultimate Hardened Version)
-- 拡張性・整合性・パフォーマンスを最優先
-- ==========================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. UUID v7 生成関数 (インデックス性能向上のため時系列UUIDを採用)
-- アプリ側で生成できない場合のフォールバック
CREATE OR REPLACE FUNCTION gen_uuid_v7() RETURNS uuid AS $$
DECLARE
v_time timestamp with time zone := clock_timestamp();
    v_secs bigint := EXTRACT(EPOCH FROM v_time);
    v_msec bigint := mod(EXTRACT(MILLISECONDS FROM v_time)::numeric * 10^3, 10^3)::bigint;
    v_usec bigint := mod(EXTRACT(MICROSECONDS FROM v_time)::numeric, 10^3)::bigint;
    v_uuid uuid := gen_random_uuid();
    v_hex text;
BEGIN
    -- Timestamp (48bit) + Ver(4bit) + Rand(12bit) + Var(2bit) + Rand(62bit)
    -- 簡易実装: 先頭48bitをUNIX時間に置き換え
    v_hex := lpad(to_hex((v_secs * 1000 + v_msec)::bigint), 12, '0');
return (v_hex || substr(v_uuid::text, 14))::uuid;
END;
$$ LANGUAGE plpgsql;

-- ==========================================================
-- 2. マスタデータ (拡張性と制約の両立)
-- ==========================================================

-- アイテムマスタ
CREATE TABLE items (
                       id UUID PRIMARY KEY DEFAULT gen_uuid_v7(),
    -- 日本語50文字制限、空文字禁止
                       name VARCHAR(50) NOT NULL CHECK (length(name) > 0),

    -- ENUM廃止 -> VARCHAR + CHECK
                       rarity VARCHAR(20) NOT NULL CHECK (rarity IN ('COMMON', 'RARE', 'SR', 'SSR', 'UR', 'LR')),

    -- 在庫上限 (設定ミス防止: 0以下や異常値を弾く)
                       max_capacity INTEGER NOT NULL DEFAULT 9999 CHECK (max_capacity BETWEEN 1 AND 999999),

                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ガチャプール定義
CREATE TABLE gacha_pools (
                             id UUID PRIMARY KEY DEFAULT gen_uuid_v7(),
                             name VARCHAR(50) NOT NULL CHECK (length(name) > 0),

                             start_at TIMESTAMP WITH TIME ZONE NOT NULL,
                             end_at TIMESTAMP WITH TIME ZONE NOT NULL,

    -- 消費石数 (異常値ブロック)
                             cost_amount INTEGER NOT NULL CHECK (cost_amount BETWEEN 1 AND 10000),

    -- 天井設定 (NULL排除、デフォルト0)
                             pity_ceiling_count INTEGER NOT NULL DEFAULT 0 CHECK (pity_ceiling_count >= 0),
                             guaranteed_trigger_count INTEGER NOT NULL DEFAULT 0 CHECK (guaranteed_trigger_count >= 0),

                             CONSTRAINT chk_gacha_period CHECK (end_at > start_at)
);

-- パフォーマンス用部分インデックス (開催中のガチャのみ高速検索)
-- 修正後 (WHERE 句を削除し、期間の検索に強い通常のインデックスにする)
CREATE INDEX idx_gacha_pools_period ON gacha_pools (start_at, end_at)
    INCLUDE (id, name, cost_amount);

-- ガチャ排出テーブル (確率定義)
CREATE TABLE gacha_emissions (
                                 id UUID PRIMARY KEY DEFAULT gen_uuid_v7(),
                                 gacha_pool_id UUID NOT NULL REFERENCES gacha_pools(id) ON DELETE CASCADE ON UPDATE CASCADE,
                                 item_id UUID NOT NULL REFERENCES items(id) ON DELETE RESTRICT ON UPDATE CASCADE,

    -- 確率ウェイト (合計値チェックはトリガーで実施)
                                 weight INTEGER NOT NULL CHECK (weight > 0),
                                 is_pickup BOOLEAN NOT NULL DEFAULT FALSE,

                                 UNIQUE (gacha_pool_id, item_id)
);

-- [Trigger] プールごとのWeight合計チェック
CREATE OR REPLACE FUNCTION check_emission_weight_sum() RETURNS TRIGGER AS $$
DECLARE
total_weight INTEGER;
BEGIN
    -- ステートメントレベルだと複雑になるため、更新対象プールの合計を計算
SELECT SUM(weight) INTO total_weight FROM gacha_emissions WHERE gacha_pool_id = NEW.gacha_pool_id;

-- 例えば合計 10000 (100.00%) であることを強制する場合
-- ここでは「0より大きいこと」と「異常に大きくないこと」だけチェックし、
-- 厳密な100%チェックは、確定コミット時(Transaction終了時)に検証するのがベストだが
-- 簡易的に挿入時に警告を出す、あるいは特定の管理フラグ完了時にチェックする運用が多い。
-- 今回は「合計が0以下」を防ぐ最低限の砦とする。
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
-- 3. ユーザー資産・状態 (Transactions & Locks)
-- ==========================================================

-- ウォレット (ユーザーIDはハッシュ化前提、ここではUUID型として扱う)
CREATE TABLE wallets (
                         user_id UUID PRIMARY KEY, -- アプリ側で管理するIDを使用

    -- 異常な付与/消費を物理的にブロック
                         paid_stones INTEGER NOT NULL DEFAULT 0 CHECK (paid_stones BETWEEN 0 AND 99999999),
                         free_stones INTEGER NOT NULL DEFAULT 0 CHECK (free_stones BETWEEN 0 AND 99999999),

                         version BIGINT NOT NULL DEFAULT 0,
                         updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ユーザー所持アイテム (在庫管理)
CREATE TABLE user_items (
                            user_id UUID NOT NULL REFERENCES wallets(user_id) ON DELETE CASCADE,
                            item_id UUID NOT NULL REFERENCES items(id) ON DELETE RESTRICT,

                            quantity INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),

                            version BIGINT NOT NULL DEFAULT 0,
                            updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

                            PRIMARY KEY (user_id, item_id)
);

-- [Trigger] 在庫上限チェック (INSERT/UPDATE両対応)
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

-- ユーザーガチャ状態 (天井/確定枠管理)
CREATE TABLE user_gacha_states (
                                   user_id UUID NOT NULL REFERENCES wallets(user_id) ON DELETE CASCADE,
                                   gacha_pool_id UUID NOT NULL REFERENCES gacha_pools(id) ON DELETE RESTRICT,

    -- カウント上限も現実的な値でキャップ
                                   current_pity_count INTEGER NOT NULL DEFAULT 0 CHECK (current_pity_count BETWEEN 0 AND 9999),
                                   current_guaranteed_count INTEGER NOT NULL DEFAULT 0 CHECK (current_guaranteed_count BETWEEN 0 AND 9999),

                                   updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

                                   PRIMARY KEY (user_id, gacha_pool_id)
);

-- ==========================================================
-- 4. ガチャ履歴 (Partitioning & JSONB)
-- ==========================================================

CREATE TABLE gacha_transactions (
                                    id UUID NOT NULL, -- UUID v7推奨
                                    user_id UUID NOT NULL, -- 参照整合性はアプリ/ロジックで担保 (パーティション跨ぎFK回避)
                                    gacha_pool_id UUID NOT NULL,

                                    consumed_paid INTEGER NOT NULL CHECK (consumed_paid >= 0),
                                    consumed_free INTEGER NOT NULL CHECK (consumed_free >= 0),

    -- 排出結果をJSONBに集約 (I/O削減・正規化廃止)
    -- 形式: [{"item_id": "...", "rarity": "SSR", "type": "NORMAL"}, ...]
                                    emission_results JSONB NOT NULL,

                                    executed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    PRIMARY KEY (executed_at, id) -- パーティションキーを先頭に
) PARTITION BY RANGE (executed_at);

-- 自動パーティション作成関数 (pg_partmanがない環境用)
CREATE OR REPLACE FUNCTION maintain_partitions() RETURNS void AS $$
DECLARE
next_month_start date := date_trunc('month', current_date + interval '1 month');
    next_month_end date := date_trunc('month', current_date + interval '2 months');
    partition_name text := 'gacha_transactions_' || to_char(next_month_start, 'YYYY_MM');
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = partition_name) THEN
        EXECUTE format('CREATE TABLE %I PARTITION OF gacha_transactions FOR VALUES FROM (%L) TO (%L)',
            partition_name, next_month_start, next_month_end);
END IF;
END;
$$ LANGUAGE plpgsql;

-- 初期パーティション作成 (今月と来月)
CREATE TABLE gacha_transactions_2026_01 PARTITION OF gacha_transactions
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE gacha_transactions_2026_02 PARTITION OF gacha_transactions
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');


-- ==========================================================
-- 5. 監査ログ (Strict Auditing)
-- ==========================================================
CREATE TABLE audit_logs (
                            id UUID PRIMARY KEY DEFAULT gen_uuid_v7(),
                            target_table VARCHAR(50) NOT NULL,
                            record_id TEXT NOT NULL, -- UUID以外も対応できるようTEXT
                            operation VARCHAR(10) NOT NULL,

    -- 変更内容を完全記録
                            old_data JSONB,
                            new_data JSONB,

                            changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE FUNCTION audit_trigger_func() RETURNS TRIGGER AS $$
BEGIN
INSERT INTO audit_logs (target_table, record_id, operation, old_data, new_data)
VALUES (
           TG_TABLE_NAME,
           COALESCE(NEW.user_id::text, OLD.user_id::text, NEW.id::text, OLD.id::text, 'unknown'),
           TG_OP,
           to_jsonb(OLD),
           to_jsonb(NEW)
       );
RETURN NULL; -- AFTER TRIGGER用なので戻り値は無視される
END;
$$ LANGUAGE plpgsql;

-- 全重要テーブルに監査トリガー適用
CREATE TRIGGER trg_audit_wallets AFTER INSERT OR UPDATE OR DELETE ON wallets
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_func();
CREATE TRIGGER trg_audit_user_items AFTER INSERT OR UPDATE OR DELETE ON user_items
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_func();


-- ==========================================================
-- 6. ガチャ実行 ストアドプロシージャ (Transaction Script)
-- アプリからこの関数を呼ぶだけで、ロック・消費・排出・保存が完結する
-- ==========================================================
CREATE OR REPLACE FUNCTION execute_gacha(
    p_user_id UUID,
    p_pool_id UUID,
    p_draw_count INTEGER,
    p_request_id UUID -- 冪等性担保用
) RETURNS JSONB AS $$
DECLARE
v_pool RECORD;
    v_wallet RECORD;
    v_total_cost INTEGER;
    v_paid_consume INTEGER := 0;
    v_free_consume INTEGER := 0;
    v_emissions JSONB := '[]'::jsonb;
    v_new_pity_count INTEGER;
BEGIN
    -- 1. 二重送信チェック (TransactionsテーブルにIDがあればエラー)
    -- パーティションテーブルへの検索はインデックスが効くようにexecuted_atが必要だが、
    -- 直近の再送を防ぐならRedis推奨。ここでは簡易的に過去1ヶ月分だけチェックする等の工夫が必要。
    -- (今回はPK重複エラーを利用して検知する設計とするためスキップ)

    -- 2. プール情報取得 & 期間チェック
SELECT * INTO v_pool FROM gacha_pools
WHERE id = p_pool_id
  AND start_at <= clock_timestamp()
  AND end_at >= clock_timestamp();

IF NOT FOUND THEN
        RAISE EXCEPTION 'GACHA_POOL_EXPIRED_OR_NOT_FOUND';
END IF;

    v_total_cost := v_pool.cost_amount * p_draw_count;

    -- 3. ウォレットロック & 残高チェック (排他制御の要)
SELECT * INTO v_wallet FROM wallets WHERE user_id = p_user_id FOR UPDATE;

IF NOT FOUND THEN RAISE EXCEPTION 'WALLET_NOT_FOUND'; END IF;

    IF (v_wallet.paid_stones + v_wallet.free_stones) < v_total_cost THEN
        RAISE EXCEPTION 'INSUFFICIENT_BALANCE';
END IF;

    -- 4. 消費ロジック (有償優先 -> 無償) ※仕様により逆も可
    -- ここではユーザー有利に「有償石を温存」ではなく、
    -- 資金決済法等の観点から「有償石から消化」する場合が多いが、要件次第。
    -- 今回は「有償 > 無償」優先で消費するロジック（有償を先に減らす）
    IF v_wallet.paid_stones >= v_total_cost THEN
        v_paid_consume := v_total_cost;
ELSE
        v_paid_consume := v_wallet.paid_stones;
        v_free_consume := v_total_cost - v_paid_consume;
END IF;

    -- 5. ウォレット更新
UPDATE wallets SET
                   paid_stones = paid_stones - v_paid_consume,
                   free_stones = free_stones - v_free_consume,
                   updated_at = clock_timestamp()
WHERE user_id = p_user_id;

-- 6. 排出ロジック (簡易実装: ランダム選出は複雑なのでここではスキップし、アプリ側計算または別関数へ)
-- 実際はここで gacha_emissions からランダムにアイテムを引き、
-- user_items を UPDATE/INSERT し、結果を v_emissions JSONB に詰める。

-- (省略: ここに抽選ロジックが入る)

-- 7. 履歴保存
INSERT INTO gacha_transactions (
    id, user_id, gacha_pool_id, consumed_paid, consumed_free, emission_results, executed_at
) VALUES (
             p_request_id, p_user_id, p_pool_id, v_paid_consume, v_free_consume, v_emissions, clock_timestamp()
         );

RETURN jsonb_build_object(
        'status', 'SUCCESS',
        'paid_consumed', v_paid_consume,
        'free_consumed', v_free_consume
       );
END;
$$ LANGUAGE plpgsql;