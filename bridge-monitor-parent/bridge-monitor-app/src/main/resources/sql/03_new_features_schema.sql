-- =============================================
-- 新增功能模块 - 数据库扩展脚本
-- 1. 石材风化深度评估
-- 2. 交通振动影响分析
-- 3. 砌筑工艺数字化复原
-- 4. 多桥对比与保护优先级排序
-- =============================================

-- =============================================
-- 8. 石材风化数据表
-- =============================================
CREATE TABLE IF NOT EXISTS weathering_data (
    id BIGSERIAL PRIMARY KEY,
    bridge_id BIGINT NOT NULL REFERENCES bridge(id) ON DELETE CASCADE,
    sensor_id BIGINT REFERENCES sensor(id),
    location VARCHAR(200) NOT NULL,
    loc_x DECIMAL(12, 6) NOT NULL,
    loc_y DECIMAL(12, 6) NOT NULL,
    loc_z DECIMAL(12, 6) NOT NULL,
    surface_hardness DECIMAL(10, 4) NOT NULL,
    ultrasonic_velocity DECIMAL(10, 4) NOT NULL,
    estimated_depth DECIMAL(10, 4) NOT NULL,
    weathering_grade VARCHAR(20) NOT NULL,
    regression_r_squared DECIMAL(8, 6),
    measurement_data JSONB,
    measured_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_weathering_bridge_id ON weathering_data(bridge_id, measured_at DESC);
CREATE INDEX IF NOT EXISTS idx_weathering_grade ON weathering_data(weathering_grade);

-- =============================================
-- 9. 交通流量数据表
-- =============================================
CREATE TABLE IF NOT EXISTS traffic_flow_data (
    id BIGSERIAL PRIMARY KEY,
    bridge_id BIGINT NOT NULL REFERENCES bridge(id) ON DELETE CASCADE,
    record_date DATE NOT NULL,
    hour_of_day INTEGER NOT NULL,
    vehicle_type VARCHAR(30) NOT NULL,
    vehicle_count INTEGER NOT NULL,
    avg_speed DECIMAL(10, 4),
    avg_weight DECIMAL(10, 4),
    total_weight DECIMAL(15, 4),
    source VARCHAR(50) DEFAULT 'simulated',
    recorded_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_traffic_bridge_date ON traffic_flow_data(bridge_id, record_date DESC);
CREATE INDEX IF NOT EXISTS idx_traffic_vehicle_type ON traffic_flow_data(vehicle_type);

-- =============================================
-- 10. 车桥耦合振动分析结果表
-- =============================================
CREATE TABLE IF NOT EXISTS traffic_vibration_analysis (
    id BIGSERIAL PRIMARY KEY,
    bridge_id BIGINT NOT NULL REFERENCES bridge(id) ON DELETE CASCADE,
    vehicle_type VARCHAR(30) NOT NULL,
    vehicle_weight DECIMAL(10, 4) NOT NULL,
    vehicle_speed DECIMAL(10, 4) NOT NULL,
    natural_frequency DECIMAL(10, 6) NOT NULL,
    damping_ratio DECIMAL(10, 6) NOT NULL,
    max_acceleration DECIMAL(15, 10) NOT NULL,
    max_dynamic_displacement DECIMAL(15, 10) NOT NULL,
    dynamic_amplification_factor DECIMAL(10, 6) NOT NULL,
    safety_margin DECIMAL(10, 6) NOT NULL,
    allowable_weight_limit DECIMAL(10, 4),
    allowable_speed_limit DECIMAL(10, 4),
    vibration_response_data JSONB,
    recommendation TEXT,
    calculated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vibration_bridge_id ON traffic_vibration_analysis(bridge_id, calculated_at DESC);
CREATE INDEX IF NOT EXISTS idx_vibration_vehicle_type ON traffic_vibration_analysis(vehicle_type);

-- =============================================
-- 11. 砌筑工艺参数表
-- =============================================
CREATE TABLE IF NOT EXISTS masonry_params (
    id BIGSERIAL PRIMARY KEY,
    bridge_id BIGINT NOT NULL REFERENCES bridge(id) ON DELETE CASCADE,
    stone_shape VARCHAR(50) NOT NULL,
    stone_arrangement VARCHAR(50) NOT NULL,
    mortar_type VARCHAR(50) NOT NULL,
    mortar_compressive_strength DECIMAL(10, 4) NOT NULL,
    mortar_tensile_strength DECIMAL(10, 4) NOT NULL,
    joint_thickness DECIMAL(8, 4) NOT NULL,
    stone_friction_coefficient DECIMAL(8, 4) NOT NULL,
    cohesion DECIMAL(10, 4),
    measured_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_masonry_bridge_id ON masonry_params(bridge_id, measured_at DESC);

-- =============================================
-- 12. 离散元仿真结果表
-- =============================================
CREATE TABLE IF NOT EXISTS masonry_dem_result (
    id BIGSERIAL PRIMARY KEY,
    bridge_id BIGINT NOT NULL REFERENCES bridge(id) ON DELETE CASCADE,
    analysis_type VARCHAR(50) NOT NULL,
    element_count INTEGER NOT NULL,
    contact_count INTEGER NOT NULL,
    max_contact_force DECIMAL(18, 8) NOT NULL,
    avg_contact_force DECIMAL(18, 8) NOT NULL,
    force_chain_data JSONB NOT NULL,
    stone_displacements JSONB,
    joint_stresses JSONB,
    structural_integrity_index DECIMAL(10, 6) NOT NULL,
    load_transfer_efficiency DECIMAL(10, 6) NOT NULL,
    recommendation TEXT,
    calculated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dem_bridge_id ON masonry_dem_result(bridge_id, calculated_at DESC);
CREATE INDEX IF NOT EXISTS idx_dem_analysis_type ON masonry_dem_result(analysis_type);

-- =============================================
-- 13. 保护优先级排序结果表
-- =============================================
CREATE TABLE IF NOT EXISTS bridge_priority_result (
    id BIGSERIAL PRIMARY KEY,
    bridge_id BIGINT NOT NULL REFERENCES bridge(id) ON DELETE CASCADE,
    ranking INTEGER NOT NULL,
    topsis_score DECIMAL(15, 10) NOT NULL,
    structure_safety_score DECIMAL(10, 6) NOT NULL,
    damage_trend_score DECIMAL(10, 6) NOT NULL,
    weathering_score DECIMAL(10, 6) NOT NULL,
    traffic_impact_score DECIMAL(10, 6) NOT NULL,
    historical_value_score DECIMAL(10, 6) NOT NULL,
    maintenance_urgency VARCHAR(20) NOT NULL,
    estimated_cost DECIMAL(15, 4),
    priority_level VARCHAR(20) NOT NULL,
    action_recommendation TEXT,
    weights JSONB,
    criteria_data JSONB,
    calculated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_priority_ranking ON bridge_priority_result(ranking);
CREATE INDEX IF NOT EXISTS idx_priority_bridge_id ON bridge_priority_result(bridge_id, calculated_at DESC);
CREATE INDEX IF NOT EXISTS idx_priority_level ON bridge_priority_result(priority_level, calculated_at DESC);

-- =============================================
-- 14. 年度保护计划表
-- =============================================
CREATE TABLE IF NOT EXISTS annual_protection_plan (
    id BIGSERIAL PRIMARY KEY,
    plan_year INTEGER NOT NULL,
    bridge_id BIGINT NOT NULL REFERENCES bridge(id) ON DELETE CASCADE,
    priority_ranking INTEGER NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    project_type VARCHAR(50) NOT NULL,
    estimated_budget DECIMAL(15, 4) NOT NULL,
    timeline VARCHAR(100),
    status VARCHAR(20) DEFAULT 'pending',
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plan_year ON annual_protection_plan(plan_year);
CREATE INDEX IF NOT EXISTS idx_plan_bridge_id ON annual_protection_plan(bridge_id);
CREATE INDEX IF NOT EXISTS idx_plan_priority ON annual_protection_plan(priority_ranking);

-- =============================================
-- 新增传感器类型支持
-- =============================================
INSERT INTO sensor (bridge_id, code, name, type, loc_x, loc_y, loc_z, position, threshold, unit)
SELECT 
    b.id,
    b.id || '_' || st.suffix,
    b.name || ' - ' || st.name,
    st.type,
    CASE st.position 
        WHEN 'top' THEN 0
        WHEN 'mid' THEN b.span_length * 0.3
        WHEN 'side' THEN b.span_length * 0.7
    END,
    CASE st.position
        WHEN 'top' THEN b.span_length * 0.25 * b.rise_span_ratio
        WHEN 'mid' THEN b.span_length * 0.25 * b.rise_span_ratio * 0.9
        WHEN 'side' THEN b.span_length * 0.25 * b.rise_span_ratio * 0.8
    END,
    0.5,
    st.position_desc,
    st.threshold,
    st.unit
FROM bridge b
CROSS JOIN (
    VALUES 
        ('hardness', '表面硬度计', 'top', '拱顶表面', 40.0, 'HRA'),
        ('ultrasonic', '超声波速传感器', 'mid', '拱腰位置', 3.5, 'km/s'),
        ('traffic_flow', '交通流量监测', 'side', '桥侧道路', 100.0, '辆/小时')
) AS st(type, name, position, position_desc, threshold, unit)
ON CONFLICT DO NOTHING;
