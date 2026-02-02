-- ============================================================
-- PostgreSQL setup script for travel_agent database
-- Complete version with all tables required by Pathfinder Agent
-- ============================================================

-- Drop existing database and recreate
DROP DATABASE IF EXISTS travel_agent;
CREATE DATABASE travel_agent WITH ENCODING 'UTF8';

\c travel_agent;

-- ============================================================
-- Core Tables
-- ============================================================

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    status VARCHAR(20) DEFAULT 'active' CHECK (status IN ('active', 'inactive')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE users IS 'User accounts';

-- User profiles table
CREATE TABLE IF NOT EXISTS user_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    language VARCHAR(10) DEFAULT 'en',
    location VARCHAR(255),
    age_range VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
COMMENT ON TABLE user_profiles IS 'User profiles';

-- User preferences table
CREATE TABLE IF NOT EXISTS user_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    travel_style VARCHAR(50) CHECK (travel_style IN ('family', 'solo', 'couple', 'business')),
    interests JSONB,
    budget_preference SMALLINT DEFAULT 2 CHECK (budget_preference BETWEEN 1 AND 3),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
COMMENT ON TABLE user_preferences IS 'Travel preferences';
CREATE INDEX IF NOT EXISTS idx_user_preferences_user ON user_preferences(user_id);

-- Destinations table
CREATE TABLE IF NOT EXISTS destinations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    country VARCHAR(100) NOT NULL,
    state VARCHAR(100),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    description TEXT,
    budget_level SMALLINT DEFAULT 2 CHECK (budget_level BETWEEN 1 AND 3),
    best_season VARCHAR(50),
    timezone VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE destinations IS 'Travel destinations';
CREATE INDEX IF NOT EXISTS idx_destinations_country ON destinations(country);
CREATE INDEX IF NOT EXISTS idx_destinations_state ON destinations(state);
CREATE INDEX IF NOT EXISTS idx_destinations_name ON destinations(name);
CREATE INDEX IF NOT EXISTS idx_destinations_budget ON destinations(budget_level);

-- Destination features table
CREATE TABLE IF NOT EXISTS destination_features (
    id BIGSERIAL PRIMARY KEY,
    destination_id BIGINT NOT NULL,
    feature_name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (destination_id) REFERENCES destinations(id) ON DELETE CASCADE,
    UNIQUE (destination_id, feature_name)
);
COMMENT ON TABLE destination_features IS 'Destination features';
CREATE INDEX IF NOT EXISTS idx_destination_features_feature ON destination_features(feature_name);

-- ============================================================
-- Trip Management Tables (Core for Pathfinder Agent)
-- ============================================================

-- Trips table (核心表)
CREATE TABLE IF NOT EXISTS trips (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    destination_id BIGINT,
    destination_name VARCHAR(255),
    destination_country VARCHAR(100),
    destination_latitude DECIMAL(10, 8),
    destination_longitude DECIMAL(11, 8),
    start_date DATE,
    end_date DATE,
    duration_days INTEGER NOT NULL,
    party_size INTEGER DEFAULT 1,
    total_budget DECIMAL(10, 2),
    currency VARCHAR(3) DEFAULT 'AUD',
    status VARCHAR(20) DEFAULT 'planning' CHECK (status IN ('planning', 'generating', 'confirmed', 'ongoing', 'completed')),
    current_version INTEGER DEFAULT 1,
    ai_summary TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (destination_id) REFERENCES destinations(id)
);
COMMENT ON TABLE trips IS 'Trip plans';
CREATE INDEX IF NOT EXISTS idx_trips_user ON trips(user_id);
CREATE INDEX IF NOT EXISTS idx_trips_status ON trips(status);

-- Itinerary days table
CREATE TABLE IF NOT EXISTS itinerary_days (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    day_number INTEGER NOT NULL,
    date DATE,
    theme VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    UNIQUE (trip_id, day_number)
);
COMMENT ON TABLE itinerary_days IS 'Daily itinerary';
CREATE INDEX IF NOT EXISTS idx_itinerary_days_trip ON itinerary_days(trip_id);

-- Itinerary items table
CREATE TABLE IF NOT EXISTS itinerary_items (
    id BIGSERIAL PRIMARY KEY,
    day_id BIGINT NOT NULL,
    trip_id BIGINT NOT NULL,
    order_index INTEGER DEFAULT 0,
    activity_name VARCHAR(255) NOT NULL,
    activity_type VARCHAR(50) NOT NULL CHECK (activity_type IN ('transportation', 'accommodation', 'dining', 'activity', 'other')),
    start_time TIME NOT NULL,
    duration_minutes INTEGER NOT NULL,
    location VARCHAR(255),
    cost DECIMAL(10, 2) DEFAULT 0.00,
    booking_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'planned' CHECK (status IN ('planned', 'completed', 'cancelled')),
    notes TEXT,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    place_id VARCHAR(100),
    original_flag BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (day_id) REFERENCES itinerary_days(id) ON DELETE CASCADE,
    FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
);
COMMENT ON TABLE itinerary_items IS 'Itinerary activities';
CREATE INDEX IF NOT EXISTS idx_itinerary_items_day ON itinerary_items(day_id);
CREATE INDEX IF NOT EXISTS idx_itinerary_items_trip ON itinerary_items(trip_id);
CREATE INDEX IF NOT EXISTS idx_itinerary_items_day_order ON itinerary_items(day_id, order_index);
CREATE INDEX IF NOT EXISTS idx_itinerary_items_location ON itinerary_items(latitude, longitude);

-- Itinerary versions table
CREATE TABLE IF NOT EXISTS itinerary_versions (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    version_number INTEGER NOT NULL,
    snapshot JSONB NOT NULL,
    change_description TEXT,
    total_cost DECIMAL(10, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    UNIQUE (trip_id, version_number)
);
COMMENT ON TABLE itinerary_versions IS 'Itinerary version history';

-- ============================================================
-- AI & Conversation Tables
-- ============================================================

-- Conversation history table
CREATE TABLE IF NOT EXISTS conversation_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    session_id VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
    message TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
COMMENT ON TABLE conversation_history IS 'AI conversation history';
CREATE INDEX IF NOT EXISTS idx_conversation_session ON conversation_history(session_id);
CREATE INDEX IF NOT EXISTS idx_conversation_user_session ON conversation_history(user_id, session_id, created_at);

-- AI recommendations table
CREATE TABLE IF NOT EXISTS ai_recommendations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    intent_hash VARCHAR(64) NOT NULL,
    mood VARCHAR(50),
    keywords TEXT,
    preferred_features TEXT,
    budget_level SMALLINT,
    estimated_duration INTEGER,
    recommendations JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);
COMMENT ON TABLE ai_recommendations IS 'AI recommendation cache';
CREATE INDEX IF NOT EXISTS idx_ai_recommendations_user_session ON ai_recommendations(user_id, session_id);
CREATE INDEX IF NOT EXISTS idx_ai_recommendations_intent ON ai_recommendations(intent_hash);
CREATE INDEX IF NOT EXISTS idx_ai_recommendations_created ON ai_recommendations(created_at);

-- ============================================================
-- Additional Feature Tables
-- ============================================================

-- Checklists table
CREATE TABLE IF NOT EXISTS checklists (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    checklist_type VARCHAR(50) DEFAULT 'preparation' CHECK (checklist_type IN ('preparation', 'packing', 'documents')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
);
COMMENT ON TABLE checklists IS 'Travel checklists';
CREATE INDEX IF NOT EXISTS idx_checklists_trip ON checklists(trip_id);

-- Checklist items table
CREATE TABLE IF NOT EXISTS checklist_items (
    id BIGSERIAL PRIMARY KEY,
    checklist_id BIGINT NOT NULL,
    trip_id BIGINT NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    priority VARCHAR(20) DEFAULT 'medium' CHECK (priority IN ('high', 'medium', 'low')),
    category VARCHAR(50),
    completed BOOLEAN DEFAULT FALSE,
    deadline DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (checklist_id) REFERENCES checklists(id) ON DELETE CASCADE,
    FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
);
COMMENT ON TABLE checklist_items IS 'Checklist items';
CREATE INDEX IF NOT EXISTS idx_checklist_items_checklist ON checklist_items(checklist_id);
CREATE INDEX IF NOT EXISTS idx_checklist_items_trip ON checklist_items(trip_id);
CREATE INDEX IF NOT EXISTS idx_checklist_items_priority ON checklist_items(priority);

-- Trip photos table
CREATE TABLE IF NOT EXISTS trip_photos (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    s3_key VARCHAR(512),
    url VARCHAR(1024) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
);
COMMENT ON TABLE trip_photos IS 'Trip photos stored in S3';
CREATE INDEX IF NOT EXISTS idx_trip_photos_trip ON trip_photos(trip_id);

-- ============================================================
-- Vector Embeddings for RAG (LangChain4j + Chroma)
-- ============================================================

-- Note: This table is optional if using external Chroma service
-- Uncomment if you want to store embeddings in PostgreSQL with pgvector extension

-- CREATE EXTENSION IF NOT EXISTS vector;
-- 
-- CREATE TABLE IF NOT EXISTS embeddings (
--     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
--     content TEXT NOT NULL,
--     metadata JSONB,
--     embedding vector(1536),
--     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
-- );
-- 
-- CREATE INDEX IF NOT EXISTS idx_embeddings_vector ON embeddings 
--     USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
-- 
-- COMMENT ON TABLE embeddings IS 'Vector embeddings for semantic search (RAG)';

-- ============================================================
-- Sample Data (Optional - for testing)
-- ============================================================

-- Insert sample destinations
INSERT INTO destinations (name, country, state, description, budget_level, latitude, longitude, best_season, timezone) VALUES
('Sydney', 'Australia', 'New South Wales', 'Beautiful harbor city with iconic Opera House and Harbour Bridge', 3, -33.8688197, 151.2092955, 'Spring/Autumn', 'Australia/Sydney'),
('Melbourne', 'Australia', 'Victoria', 'Cultural capital with great coffee, art scene and laneways', 3, -37.8136276, 144.9630576, 'Spring/Autumn', 'Australia/Melbourne'),
('Gold Coast', 'Australia', 'Queensland', 'Famous beaches, surfing and theme parks', 2, -28.0167, 153.4000, 'Year-round', 'Australia/Brisbane'),
('Byron Bay', 'Australia', 'New South Wales', 'Relaxed beach town with great surfing and yoga', 2, -28.6474, 153.6020, 'Year-round', 'Australia/Sydney'),
('Great Barrier Reef', 'Australia', 'Queensland', 'World''s largest coral reef system', 4, -18.2871, 147.6992, 'May-October', 'Australia/Brisbane'),
('Tokyo', 'Japan', NULL, 'Vibrant metropolis blending tradition and modernity', 3, 35.6762, 139.6503, 'Spring/Autumn', 'Asia/Tokyo'),
('Kyoto', 'Japan', NULL, 'Ancient capital with temples, gardens and geishas', 2, 35.0116, 135.7681, 'Spring/Autumn', 'Asia/Tokyo'),
('Paris', 'France', NULL, 'City of Light with art, fashion and cuisine', 4, 48.8566, 2.3522, 'Spring/Autumn', 'Europe/Paris')
ON CONFLICT DO NOTHING;

-- ============================================================
-- Database Setup Complete
-- ============================================================

-- Display table count
DO $$
DECLARE
    table_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO table_count 
    FROM information_schema.tables 
    WHERE table_schema = 'public' AND table_type = 'BASE TABLE';
    
    RAISE NOTICE '============================================================';
    RAISE NOTICE 'Pathfinder Agent Database Setup Complete!';
    RAISE NOTICE 'Total tables created: %', table_count;
    RAISE NOTICE '============================================================';
    RAISE NOTICE 'Core tables:';
    RAISE NOTICE '  - users, user_profiles, user_preferences';
    RAISE NOTICE '  - destinations, destination_features';
    RAISE NOTICE '  - trips, itinerary_days, itinerary_items, itinerary_versions';
    RAISE NOTICE '  - conversation_history, ai_recommendations';
    RAISE NOTICE '  - checklists, checklist_items';
    RAISE NOTICE '  - trip_photos';
    RAISE NOTICE '============================================================';
END $$;
