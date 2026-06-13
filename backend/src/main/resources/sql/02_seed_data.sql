-- =============================================
-- 10座古代石拱桥基础数据
-- =============================================

INSERT INTO bridge (name, location, built_year, span_length, rise_span_ratio, pier_thickness, arch_count, stone_modulus, stone_poisson, stone_strength, health_score, status) VALUES
('赵州桥', '河北省石家庄市赵县', 605, 37.02, 0.204, 9.6, 1, 45.0, 0.18, 80.0, 92, 'normal'),
('卢沟桥', '北京市丰台区', 1192, 16.5, 0.250, 7.8, 11, 42.0, 0.19, 75.0, 85, 'normal'),
('广济桥', '广东省潮州市', 1171, 22.0, 0.220, 8.5, 19, 40.0, 0.20, 70.0, 78, 'warning'),
('洛阳桥', '福建省泉州市', 1059, 15.0, 0.280, 7.0, 46, 48.0, 0.17, 85.0, 88, 'normal'),
('宝带桥', '江苏省苏州市', 816, 4.8, 0.300, 3.5, 53, 38.0, 0.21, 65.0, 82, 'normal'),
('安平桥', '福建省晋江市', 1152, 6.5, 0.260, 4.0, 362, 44.0, 0.18, 78.0, 90, 'normal'),
('五亭桥', '江苏省扬州市', 1757, 7.5, 0.350, 5.0, 15, 41.0, 0.19, 72.0, 95, 'normal'),
('铁索桥泸定桥', '四川省甘孜州泸定县', 1706, 103.0, 0.000, 0.0, 1, 200.0, 0.28, 400.0, 75, 'warning'),
('程阳风雨桥', '广西柳州市三江县', 1916, 12.0, 0.240, 6.0, 5, 35.0, 0.22, 60.0, 86, 'normal'),
('拱宸桥', '浙江省杭州市', 1631, 15.8, 0.270, 7.2, 3, 43.0, 0.18, 76.0, 89, 'normal')
ON CONFLICT DO NOTHING;

-- =============================================
-- 传感器配置数据 (每座桥8个传感器: 4应变+2位移+1裂缝+1温度)
-- =============================================

-- 应变传感器位置: 拱顶、拱脚(左右)、1/4跨(左右)
-- 位移传感器位置: 左右桥墩
-- 裂缝传感器位置: 主拱券最大弯矩处
-- 温度传感器位置: 桥身中部

DO $$
DECLARE
    b_id BIGINT;
    bridge_record RECORD;
    sensor_code VARCHAR(50);
    x_pos DECIMAL;
BEGIN
    FOR bridge_record IN SELECT id, name, span_length FROM bridge ORDER BY id LOOP
        b_id := bridge_record.id;
        -- 应变传感器1: 拱顶
        sensor_code := 'STRAIN-' || b_id || '-01';
        INSERT INTO sensor (bridge_id, code, name, type, loc_x, loc_y, loc_z, position, threshold, unit)
        VALUES (b_id, sensor_code, '拱顶应变计', 'strain', 0, bridge_record.span_length * 0.204, bridge_record.span_length / 2, '主拱拱顶', 150.0, '微应变')
        ON CONFLICT (code) DO NOTHING;

        -- 应变传感器2: 左拱脚
        INSERT INTO sensor (bridge_id, code, name, type, loc_x, loc_y, loc_z, position, threshold, unit)
        VALUES (b_id, 'STRAIN-' || b_id || '-02', '左拱脚应变计', 'strain', 0, 0, 0, '左拱脚', 150.0, '微应变')
        ON CONFLICT (code) DO NOTHING;

        -- 应变传感器3: 右拱脚
        INSERT INTO sensor (bridge_id, code, name, type, loc_x, loc_y, loc_z, position, threshold, unit)
        VALUES (b_id, 'STRAIN-' || b_id || '-03', '右拱脚应变计', 'strain', 0, 0, bridge_record.span_length, '右拱脚', 150.0, '微应变')
        ON CONFLICT (code) DO NOTHING;

        -- 应变传感器4: 左1/4跨
        INSERT INTO sensor (bridge_id, code, name, type, loc_x, loc_y, loc_z, position, threshold, unit)
        VALUES (b_id, 'STRAIN-' || b_id || '-04', '左1/4跨应变计', 'strain', 0, bridge_record.span_length * 0.204 * 0.75, bridge_record.span_length * 0.25, '左1/4跨拱肋', 120.0, '微应变')
        ON CONFLICT (code) DO NOTHING;

        -- 位移传感器1: 左桥墩沉降
        INSERT INTO sensor (bridge_id, code, name, type, loc_x, loc_y, loc_z, position, threshold, unit)
        VALUES (b_id, 'DISP-' || b_id || '-01', '左桥墩位移计', 'displacement', -1.0, 0, 0, '左桥墩沉降', 10.0, 'mm')
        ON CONFLICT (code) DO NOTHING;

        -- 位移传感器2: 右桥墩沉降
        INSERT INTO sensor (bridge_id, code, name, type, loc_x, loc_y, loc_z, position, threshold, unit)
        VALUES (b_id, 'DISP-' || b_id || '-02', '右桥墩位移计', 'displacement', -1.0, 0, bridge_record.span_length, '右桥墩沉降', 10.0, 'mm')
        ON CONFLICT (code) DO NOTHING;

        -- 裂缝传感器1: 拱顶裂缝
        INSERT INTO sensor (bridge_id, code, name, type, loc_x, loc_y, loc_z, position, threshold, unit)
        VALUES (b_id, 'CRACK-' || b_id || '-01', '拱顶裂缝计', 'crack', 0, bridge_record.span_length * 0.204 + 0.1, bridge_record.span_length / 2, '拱顶纵向裂缝', 1.0, 'mm/月')
        ON CONFLICT (code) DO NOTHING;

        -- 温度传感器1
        INSERT INTO sensor (bridge_id, code, name, type, loc_x, loc_y, loc_z, position, threshold, unit)
        VALUES (b_id, 'TEMP-' || b_id || '-01', '环境温度传感器', 'temperature', 0, bridge_record.span_length * 0.204 / 2, bridge_record.span_length / 2, '桥身环境温度', 50.0, '℃')
        ON CONFLICT (code) DO NOTHING;
    END LOOP;
END $$;
