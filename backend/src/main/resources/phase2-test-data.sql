-- Phase 2 测试数据
-- 插入澳大利亚热门目的地

USE travel_agent;

-- 插入目的地数据
INSERT INTO destinations (name, country, state, latitude, longitude, description, budget_level, best_season, timezone, created_at) VALUES
('Byron Bay', 'Australia', 'NSW', -28.6474, 153.6020, 'Beach paradise with surfing, whale watching, and bohemian atmosphere', 2, 'Oct-Mar', 'Australia/Sydney', NOW()),
('Port Douglas', 'Australia', 'QLD', -16.4839, 145.4638, 'Tropical gateway to Great Barrier Reef with stunning beaches', 2, 'Apr-Nov', 'Australia/Brisbane', NOW()),
('Margaret River', 'Australia', 'WA', -33.9546, 115.0726, 'Wine region with beautiful coastline and gourmet food', 3, 'Dec-Feb', 'Australia/Perth', NOW()),
('Blue Mountains', 'Australia', 'NSW', -33.7000, 150.3000, 'Stunning mountain scenery with hiking trails and waterfalls', 1, 'Sep-Nov', 'Australia/Sydney', NOW()),
('Great Ocean Road', 'Australia', 'VIC', -38.6857, 143.3900, 'Iconic coastal drive with Twelve Apostles and surf beaches', 2, 'Dec-Feb', 'Australia/Melbourne', NOW());

-- 获取插入的 destination_id
SET @byron_id = (SELECT id FROM destinations WHERE name = 'Byron Bay');
SET @port_id = (SELECT id FROM destinations WHERE name = 'Port Douglas');
SET @margaret_id = (SELECT id FROM destinations WHERE name = 'Margaret River');
SET @blue_id = (SELECT id FROM destinations WHERE name = 'Blue Mountains');
SET @ocean_id = (SELECT id FROM destinations WHERE name = 'Great Ocean Road');

-- 插入目的地特征
INSERT INTO destination_features (destination_id, feature_name, created_at) VALUES
-- Byron Bay features
(@byron_id, 'beach', NOW()),
(@byron_id, 'nature', NOW()),
(@byron_id, 'relaxation', NOW()),
(@byron_id, 'surfing', NOW()),

-- Port Douglas features
(@port_id, 'beach', NOW()),
(@port_id, 'nature', NOW()),
(@port_id, 'adventure', NOW()),
(@port_id, 'snorkeling', NOW()),

-- Margaret River features
(@margaret_id, 'beach', NOW()),
(@margaret_id, 'food', NOW()),
(@margaret_id, 'wine', NOW()),
(@margaret_id, 'relaxation', NOW()),

-- Blue Mountains features
(@blue_id, 'mountains', NOW()),
(@blue_id, 'nature', NOW()),
(@blue_id, 'hiking', NOW()),
(@blue_id, 'photography', NOW()),

-- Great Ocean Road features
(@ocean_id, 'beach', NOW()),
(@ocean_id, 'nature', NOW()),
(@ocean_id, 'photography', NOW()),
(@ocean_id, 'roadtrip', NOW());

-- 验证数据插入成功
SELECT d.name, d.state, GROUP_CONCAT(df.feature_name) AS features
FROM destinations d
LEFT JOIN destination_features df ON d.id = df.destination_id
GROUP BY d.id;
