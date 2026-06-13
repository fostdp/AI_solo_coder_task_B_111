-- =============================================
-- 古代石拱桥结构健康监测系统 - 数据库初始化脚本
-- Database: PostgreSQL 15+ with TimescaleDB 2.13+
-- =============================================

-- 1. 创建扩展
CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS postgis;

-- =============================================
-- 2. 基础数据表
-- =============================================

-- 桥梁基础信息表
CREATE TABLE IF NOT EXISTS bridge (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    location VARCHAR(200) NOT NULL,
    built_year INTEGER,
    span_length DECIMAL(10, 2) NOT NULL,
    rise_span_ratio DECIMAL(8, 4) NOT NULL,
    pier_thickness DECIMAL(8, 2) NOT NULL,
    arch_count INTEGER NOT NULL DEFAULT 1,
    stone_modulus DECIMAL(10, 4) NOT NULL,
    stone_poisson DECIMAL(8, 4) NOT NULL,
    stone_strength DECIMAL(10, 4) NOT NULL,
    health_score INTEGER DEFAULT 100,
    status VARCHAR(20) DEFAULT 'normal',
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 传感器信息表
CREATE TABLE IF NOT EXISTS sensor (
    id BIGSERIAL PRIMARY KEY,
    bridge_id BIGINT NOT NULL REFERENCES bridge(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100),
    type VARCHAR(30) NOT NULL,
    loc_x DECIMAL(12, 6) NOT NULL,
    loc_y DECIMAL(12, 6) NOT NULL,
    loc_z DECIMAL(12, 6) NOT NULL,
    position VARCHAR(200),
    threshold DECIMAL(15, 6) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(bridge_id, code)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_sensor_bridge_id ON sensor(bridge_id);
CREATE INDEX IF NOT EXISTS idx_sensor_type ON sensor(type);

-- =============================================
-- 3. 时序数据表 (TimescaleDB 超表)
-- =============================================

-- 传感器时序数据表
CREATE TABLE IF NOT EXISTS sensor_data (
    id BIGSERIAL,
    sensor_id BIGINT NOT NULL REFERENCES sensor(id) ON DELETE CASCADE,
    bridge_id BIGINT NOT NULL REFERENCES bridge(id) ON DELETE CASCADE,
    value DECIMAL(18, 8) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    temperature DECIMAL(8, 2),
    PRIMARY KEY(id, timestamp)
);

-- 创建超表 - 按时间分区，每周一个分片
SELECT create_hypertable(
    'sensor_data',
    'timestamp',
    chunk_time_interval => INTERVAL '1 week',
    if_not_exists => TRUE
);

-- 设置数据保留策略 - 保留5年数据
SELECT add_retention_policy(
    'sensor_data',
    INTERVAL '5 years',
    if_not_exists => TRUE
);

-- =============================================
-- 3.1 原生压缩策略 (Native Compression)
-- =============================================

-- 启用超表原生压缩
ALTER TABLE sensor_data SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'bridge_id, sensor_id',
    timescaledb.compress_orderby = 'timestamp DESC'
);

-- 添加自动压缩策略：超过3个月的数据自动压缩
SELECT add_compression_policy(
    'sensor_data',
    INTERVAL '3 months',
    if_not_exists => TRUE
);

-- 为连续聚合视图启用压缩
ALTER MATERIALIZED VIEW sensor_data_hourly SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'bridge_id, sensor_id',
    timescaledb.compress_orderby = 'bucket DESC'
);

ALTER MATERIALIZED VIEW sensor_data_daily SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'bridge_id, sensor_id',
    timescaledb.compress_orderby = 'bucket DESC'
);

ALTER MATERIALIZED VIEW sensor_data_monthly SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'bridge_id, sensor_id',
    timescaledb.compress_orderby = 'bucket DESC'
);

-- 添加连续聚合压缩策略：超过1个月的聚合数据自动压缩
SELECT add_compression_policy(
    'sensor_data_hourly',
    INTERVAL '1 month',
    if_not_exists => TRUE
);

SELECT add_compression_policy(
    'sensor_data_daily',
    INTERVAL '1 month',
    if_not_exists => TRUE
);

SELECT add_compression_policy(
    'sensor_data_monthly',
    INTERVAL '1 month',
    if_not_exists => TRUE
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_sensor_data_bridge_sensor_time 
    ON sensor_data(bridge_id, sensor_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_sensor_data_sensor_time 
    ON sensor_data(sensor_id, timestamp DESC);

-- =============================================
-- 4. 连续聚合视图 (Continuous Aggregates)
-- =============================================

-- 每小时聚合视图
CREATE MATERIALIZED VIEW sensor_data_hourly
WITH (timescaledb.continuous) AS
SELECT
    bridge_id,
    sensor_id,
    time_bucket('1 hour', timestamp) AS bucket,
    AVG(value) AS avg_value,
    MIN(value) AS min_value,
    MAX(value) AS max_value,
    COUNT(*) AS sample_count
FROM sensor_data
GROUP BY bridge_id, sensor_id, time_bucket('1 hour', timestamp)
WITH NO DATA;

-- 每日聚合视图
CREATE MATERIALIZED VIEW sensor_data_daily
WITH (timescaledb.continuous) AS
SELECT
    bridge_id,
    sensor_id,
    time_bucket('1 day', timestamp) AS bucket,
    AVG(value) AS avg_value,
    MIN(value) AS min_value,
    MAX(value) AS max_value,
    COUNT(*) AS sample_count
FROM sensor_data
GROUP BY bridge_id, sensor_id, time_bucket('1 day', timestamp)
WITH NO DATA;

-- 每月聚合视图
CREATE MATERIALIZED VIEW sensor_data_monthly
WITH (timescaledb.continuous) AS
SELECT
    bridge_id,
    sensor_id,
    time_bucket('1 month', timestamp) AS bucket,
    AVG(value) AS avg_value,
    MIN(value) AS min_value,
    MAX(value) AS max_value,
    COUNT(*) AS sample_count
FROM sensor_data
GROUP BY bridge_id, sensor_id, time_bucket('1 month', timestamp)
WITH NO DATA;

-- 添加连续聚合刷新策略
SELECT add_continuous_aggregate_policy(
    'sensor_data_hourly',
    start_offset => INTERVAL '3 hours',
    end_offset => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour',
    if_not_exists => TRUE
);

SELECT add_continuous_aggregate_policy(
    'sensor_data_daily',
    start_offset => INTERVAL '2 days',
    end_offset => INTERVAL '1 day',
    schedule_interval => INTERVAL '1 day',
    if_not_exists => TRUE
);

SELECT add_continuous_aggregate_policy(
    'sensor_data_monthly',
    start_offset => INTERVAL '2 months',
    end_offset => INTERVAL '1 month',
    schedule_interval => INTERVAL '1 month',
    if_not_exists => TRUE
);

-- =============================================
-- 5. 仿真分析结果表
-- =============================================

-- FEM有限元分析结果表
CREATE TABLE IF NOT EXISTS fem_result (
    id BIGSERIAL PRIMARY KEY,
    bridge_id BIGINT NOT NULL REFERENCES bridge(id) ON DELETE CASCADE,
    load_type VARCHAR(20) NOT NULL,
    node_data JSONB NOT NULL,
    max_stress DECIMAL(18, 8) NOT NULL,
    max_strain DECIMAL(18, 8) NOT NULL,
    safety_factor DECIMAL(10, 4) NOT NULL,
    mc_samples INTEGER,
    stress_p95 DECIMAL(18, 8),
    stress_p99 DECIMAL(18, 8),
    pf_failure DECIMAL(12, 10),
    modulus_cov DECIMAL(8, 4),
    is_stochastic BOOLEAN DEFAULT FALSE,
    calculated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fem_result_bridge_id ON fem_result(bridge_id, calculated_at DESC);

-- 损伤预测结果表
CREATE TABLE IF NOT EXISTS damage_prediction (
    id BIGSERIAL PRIMARY KEY,
    bridge_id BIGINT NOT NULL REFERENCES bridge(id) ON DELETE CASCADE,
    crack_sensor_id BIGINT NOT NULL REFERENCES sensor(id),
    initial_length DECIMAL(12, 6) NOT NULL,
    paris_c DECIMAL(18, 12) NOT NULL,
    paris_m DECIMAL(10, 4) NOT NULL,
    paris_c_posterior_mean DECIMAL(18, 12),
    paris_c_posterior_std DECIMAL(18, 12),
    paris_m_posterior_mean DECIMAL(10, 6),
    paris_m_posterior_std DECIMAL(10, 6),
    mcmc_samples INTEGER,
    is_bayesian BOOLEAN DEFAULT FALSE,
    prediction_data JSONB NOT NULL,
    maintenance_year INTEGER,
    recommendation TEXT,
    predicted_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_damage_prediction_bridge_id ON damage_prediction(bridge_id, predicted_at DESC);

-- =============================================
-- 6. 告警表
-- =============================================

CREATE TABLE IF NOT EXISTS alert (
    id BIGSERIAL PRIMARY KEY,
    bridge_id BIGINT NOT NULL REFERENCES bridge(id) ON DELETE CASCADE,
    sensor_id BIGINT REFERENCES sensor(id),
    type VARCHAR(30) NOT NULL,
    level VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    value DECIMAL(18, 8) NOT NULL,
    threshold DECIMAL(18, 8) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_at TIMESTAMPTZ,
    acknowledged_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_alert_bridge_id ON alert(bridge_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_alert_level ON alert(level, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_alert_acknowledged ON alert(acknowledged, timestamp DESC);

-- =============================================
-- 7. 告警阈值配置表
-- =============================================

CREATE TABLE IF NOT EXISTS alert_threshold (
    id BIGSERIAL PRIMARY KEY,
    alert_type VARCHAR(30) NOT NULL UNIQUE,
    warning_value DECIMAL(18, 8) NOT NULL,
    danger_value DECIMAL(18, 8) NOT NULL,
    description TEXT,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 初始化默认告警阈值
INSERT INTO alert_threshold (alert_type, warning_value, danger_value, description) VALUES
    ('settlement', 5.0, 10.0, '桥墩沉降阈值，单位：mm'),
    ('crack_rate_monthly', 0.5, 1.0, '裂缝月扩展速率阈值，单位：mm/月'),
    ('strain', 100.0, 150.0, '应变阈值，单位：微应变'),
    ('temperature_rate', 5.0, 8.0, '温度变化速率阈值，单位：℃/小时')
ON CONFLICT (alert_type) DO NOTHING;
