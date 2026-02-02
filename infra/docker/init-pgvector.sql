-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 创建 embeddings 表
CREATE TABLE IF NOT EXISTS embeddings (
    id SERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    embedding vector(1536),  -- text-embedding-3-small 维度
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建向量索引 (HNSW 算法，高性能)
CREATE INDEX IF NOT EXISTS embeddings_embedding_idx 
ON embeddings USING hnsw (embedding vector_cosine_ops);

-- 创建元数据索引
CREATE INDEX IF NOT EXISTS embeddings_metadata_idx 
ON embeddings USING gin (metadata);

-- 创建目的地向量表
CREATE TABLE IF NOT EXISTS destination_embeddings (
    id SERIAL PRIMARY KEY,
    destination_id BIGINT,
    destination_name VARCHAR(255),
    description TEXT,
    embedding vector(1536),
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS destination_embeddings_embedding_idx 
ON destination_embeddings USING hnsw (embedding vector_cosine_ops);

-- 创建行程向量表
CREATE TABLE IF NOT EXISTS itinerary_embeddings (
    id SERIAL PRIMARY KEY,
    trip_id BIGINT,
    content TEXT,
    embedding vector(1536),
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS itinerary_embeddings_embedding_idx 
ON itinerary_embeddings USING hnsw (embedding vector_cosine_ops);

-- 创建知识库向量表
CREATE TABLE IF NOT EXISTS knowledge_embeddings (
    id SERIAL PRIMARY KEY,
    category VARCHAR(100),
    question TEXT,
    answer TEXT,
    embedding vector(1536),
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS knowledge_embeddings_embedding_idx 
ON knowledge_embeddings USING hnsw (embedding vector_cosine_ops);
