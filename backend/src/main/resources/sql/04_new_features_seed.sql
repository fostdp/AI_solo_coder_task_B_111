-- =============================================
-- 新增功能模块 - 种子数据初始化
-- =============================================

-- =============================================
-- 砌筑工艺参数 - 根据不同古桥特点设置
-- =============================================
INSERT INTO masonry_params (bridge_id, stone_shape, stone_arrangement, mortar_type, 
    mortar_compressive_strength, mortar_tensile_strength, joint_thickness, 
    stone_friction_coefficient, cohesion)
SELECT 
    b.id,
    CASE b.name
        WHEN '赵州桥' THEN '弧形拱石'
        WHEN '卢沟桥' THEN '矩形条石'
        WHEN '广济桥' THEN '楔形拱石'
        WHEN '洛阳桥' THEN '方整条石'
        WHEN '宝带桥' THEN '薄拱石'
        WHEN '安平桥' THEN '花岗岩条石'
        WHEN '五亭桥' THEN '青石拱石'
        WHEN '泸定桥' THEN '铁索+石砌桥台'
        WHEN '程阳风雨桥' THEN '杉木+青石'
        ELSE '矩形条石'
    END,
    CASE b.name
        WHEN '赵州桥' THEN '联拱并列砌筑'
        WHEN '卢沟桥' THEN '错缝平砌'
        WHEN '广济桥' THEN '拱券纵向并列'
        WHEN '洛阳桥' THEN '纵横交错'
        WHEN '宝带桥' THEN '薄拱密砌'
        WHEN '安平桥' THEN '顺砌'
        WHEN '五亭桥' THEN '三折拱'
        ELSE '常规砌筑'
    END,
    CASE b.name
        WHEN '赵州桥' THEN '石灰糯米浆'
        WHEN '卢沟桥' THEN '传统石灰砂浆'
        WHEN '广济桥' THEN '牡蛎灰砂浆'
        WHEN '洛阳桥' THEN '牡蛎灰砂浆'
        WHEN '宝带桥' THEN '石灰砂浆'
        WHEN '安平桥' THEN '糯米灰浆'
        ELSE '石灰砂浆'
    END,
    4.0 + (RANDOM() * 2.0),
    0.3 + (RANDOM() * 0.2),
    0.015 + (RANDOM() * 0.01),
    0.6 + (RANDOM() * 0.2),
    0.5 + (RANDOM() * 0.3)
FROM bridge b
ON CONFLICT DO NOTHING;

-- =============================================
-- 初始化风化数据 - 为每座桥生成8个测量点
-- =============================================
INSERT INTO weathering_data (bridge_id, location, loc_x, loc_y, loc_z,
    surface_hardness, ultrasonic_velocity, estimated_depth, weathering_grade,
    regression_r_squared)
SELECT 
    b.id,
    '测量点' || w.pt_num,
    w.x,
    w.y,
    0.5,
    30.0 + (RANDOM() * 30.0),
    2.5 + (RANDOM() * 2.0),
    0,
    'none',
    0.85 + (RANDOM() * 0.1)
FROM bridge b
CROSS JOIN (
    SELECT 1 as pt_num, -2.0 as x, 0.2 as y UNION
    SELECT 2, -1.0, 0.4 UNION
    SELECT 3, 0.0, 0.5 UNION
    SELECT 4, 1.0, 0.4 UNION
    SELECT 5, 2.0, 0.2 UNION
    SELECT 6, -1.5, 0.35 UNION
    SELECT 7, 1.5, 0.35 UNION
    SELECT 8, 0.0, 0.45
) w
ON CONFLICT DO NOTHING;

-- =============================================
-- 初始化交通流量数据 - 为每座桥生成24小时数据
-- =============================================
INSERT INTO traffic_flow_data (bridge_id, record_date, hour_of_day, vehicle_type,
    vehicle_count, avg_speed, avg_weight, total_weight)
SELECT 
    b.id,
    CURRENT_DATE,
    h.hour,
    CASE (RANDOM() * 4)::INT
        WHEN 0 THEN 'passenger'
        WHEN 1 THEN 'truck_light'
        WHEN 2 THEN 'truck_medium'
        WHEN 3 THEN 'truck_heavy'
        ELSE 'bus'
    END,
    CASE
        WHEN h.hour BETWEEN 7 AND 9 OR h.hour BETWEEN 17 AND 19 THEN 30 + (RANDOM() * 50)::INT
        WHEN h.hour BETWEEN 10 AND 16 THEN 20 + (RANDOM() * 30)::INT
        WHEN h.hour BETWEEN 6 AND 22 THEN 10 + (RANDOM() * 20)::INT
        ELSE 2 + (RANDOM() * 8)::INT
    END,
    40.0 + (RANDOM() * 40.0),
    CASE (RANDOM() * 4)::INT
        WHEN 0 THEN 1.5
        WHEN 1 THEN 7.5
        WHEN 2 THEN 18.0
        WHEN 3 THEN 35.0
        ELSE 12.0
    END,
    0
FROM bridge b
CROSS JOIN generate_series(0, 23) as h(hour)
ON CONFLICT DO NOTHING;

-- =============================================
-- 新增传感器类型：硬度计、超声波速传感器、振动传感器
-- 每座桥各增加4个硬度计、4个超声波速传感器、2个振动传感器
-- =============================================

DO $$
DECLARE
    b_id BIGINT;
    bridge_record RECORD;
BEGIN
    FOR bridge_record IN SELECT id, name, span_length, rise_span_ratio FROM bridge ORDER BY id LOOP
        b_id := bridge_record.id;

        -- 硬度计：4个测点（拱顶、左右拱腹、拱脚）
        FOR i IN 1..4 LOOP
            DECLARE
                h_x DECIMAL;
                h_y DECIMAL;
                h_name VARCHAR;
            BEGIN
                IF i = 1 THEN h_x := 0; h_y := bridge_record.span_length * bridge_record.rise_span_ratio; h_name := '拱顶硬度计';
                ELSIF i = 2 THEN h_x := -bridge_record.span_length * 0.3; h_y := bridge_record.span_length * bridge_record.rise_span_ratio * 0.7; h_name := '左拱腹硬度计';
                ELSIF i = 3 THEN h_x := bridge_record.span_length * 0.3; h_y := bridge_record.span_length * bridge_record.rise_span_ratio * 0.7; h_name := '右拱腹硬度计';
                ELSE h_x := 0; h_y := 0.3; h_name := '拱脚硬度计';
                END IF;

                INSERT INTO sensor (bridge_id, code, name, type, loc_x, loc_y, loc_z, position, threshold, unit)
                VALUES (b_id, 'HARD-' || b_id || '-0' || i, h_name, 'hardness',
                        0, h_y, h_x,
                        h_name, 20.0, 'HLD（里氏硬度）')
                ON CONFLICT (code) DO NOTHING;
            END;
        END LOOP;

        -- 超声波速传感器：4个测点（对应硬度计位置）
        FOR i IN 1..4 LOOP
            DECLARE
                u_x DECIMAL;
                u_y DECIMAL;
                u_name VARCHAR;
            BEGIN
                IF i = 1 THEN u_x := 0; u_y := bridge_record.span_length * bridge_record.rise_span_ratio + 0.1; u_name := '拱顶超声波速计';
                ELSIF i = 2 THEN u_x := -bridge_record.span_length * 0.3; u_y := bridge_record.span_length * bridge_record.rise_span_ratio * 0.7 + 0.1; u_name := '左拱腹超声波速计';
                ELSIF i = 3 THEN u_x := bridge_record.span_length * 0.3; u_y := bridge_record.span_length * bridge_record.rise_span_ratio * 0.7 + 0.1; u_name := '右拱腹超声波速计';
                ELSE u_x := 0; u_y := 0.5; u_name := '拱脚超声波速计';
                END IF;

                INSERT INTO sensor (bridge_id, code, name, type, loc_x, loc_y, loc_z, position, threshold, unit)
                VALUES (b_id, 'US-' || b_id || '-0' || i, u_name, 'ultrasonic',
                        0, u_y, u_x,
                        u_name, 2.0, 'km/s')
                ON CONFLICT (code) DO NOTHING;
            END;
        END LOOP;

        -- 振动传感器：2个测点（拱顶、1/4跨）
        FOR i IN 1..2 LOOP
            DECLARE
                v_x DECIMAL;
                v_y DECIMAL;
                v_name VARCHAR;
                v_code VARCHAR;
            BEGIN
                IF i = 1 THEN v_x := 0; v_y := bridge_record.span_length * bridge_record.rise_span_ratio; v_name := '拱顶振动传感器'; v_code := 'VIB-' || b_id || '-01';
                ELSE v_x := bridge_record.span_length * 0.25; v_y := bridge_record.span_length * bridge_record.rise_span_ratio * 0.8; v_name := '1/4跨振动传感器'; v_code := 'VIB-' || b_id || '-02';
                END IF;

                INSERT INTO sensor (bridge_id, code, name, type, loc_x, loc_y, loc_z, position, threshold, unit)
                VALUES (b_id, v_code, v_name, 'vibration',
                        0, v_y, v_x,
                        v_name, 0.5, 'm/s²')
                ON CONFLICT (code) DO NOTHING;
            END;
        END LOOP;
    END LOOP;
END $$;

-- =============================================
-- 新增告警阈值类型
-- =============================================
INSERT INTO alert_threshold (alert_type, warning_value, danger_value, description) VALUES
    ('weathering_rate', 2.0, 5.0, '石材风化速率阈值，单位：mm/年'),
    ('vibration_acceleration', 0.5, 1.0, '振动加速度阈值，单位：m/s²'),
    ('contact_force_exceed', 0.8, 0.95, '接触力超限比例阈值，单位：%'),
    ('priority_critical', 3, 1, '保护优先级阈值，排名前N位需紧急处理')
ON CONFLICT (alert_type) DO NOTHING;
