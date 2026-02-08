-- AB测试实验数据表
CREATE TABLE IF NOT EXISTS ab_test_experiments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    trip_id BIGINT NOT NULL UNIQUE,
    group_name VARCHAR(10) NOT NULL,
    used_rag BOOLEAN NOT NULL DEFAULT FALSE,
    destination VARCHAR(100),
    
    -- 客观指标：内容准确性
    attraction_accuracy DECIMAL(5,2),
    price_accuracy DECIMAL(5,2),
    info_completeness DECIMAL(5,2),
    
    -- 主观指标：用户反馈
    credibility_score INTEGER CHECK (credibility_score >= 1 AND credibility_score <= 5),
    satisfaction_score INTEGER CHECK (satisfaction_score >= 1 AND satisfaction_score <= 5),
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_ab_test_group ON ab_test_experiments(group_name);
CREATE INDEX idx_ab_test_user ON ab_test_experiments(user_id);
CREATE INDEX idx_ab_test_trip ON ab_test_experiments(trip_id);
CREATE INDEX idx_ab_test_created ON ab_test_experiments(created_at);

-- 添加注释
COMMENT ON TABLE ab_test_experiments IS 'AB测试实验数据，用于验证RAG知识库增强方案效果';
COMMENT ON COLUMN ab_test_experiments.group_name IS 'A=无RAG纯AI生成, B=RAG增强';
COMMENT ON COLUMN ab_test_experiments.attraction_accuracy IS '景点真实性百分比(0-100)';
COMMENT ON COLUMN ab_test_experiments.price_accuracy IS '价格准确性百分比(0-100)';
COMMENT ON COLUMN ab_test_experiments.info_completeness IS '信息完整度百分比(0-100)';
COMMENT ON COLUMN ab_test_experiments.credibility_score IS '用户可信度评分(1-5)';
COMMENT ON COLUMN ab_test_experiments.satisfaction_score IS '用户满意度评分(1-5)';
