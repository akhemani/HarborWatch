-- V1__init.sql (Step 1)
-- Purpose: create the base tables used by the app (mirrors tutor's schema idea)
-- Note: Flyway runs this automatically on startup

CREATE TABLE IF NOT EXISTS performance_data (
    id SERIAL PRIMARY KEY,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    metric_name VARCHAR(50),
    metric_value DOUBLE PRECISION,
    metadata JSONB
);

CREATE TABLE IF NOT EXISTS computation_results (
    id SERIAL PRIMARY KEY,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    computation_type VARCHAR(50),
    input_size INTEGER,
    result TEXT,
    duration_ms DOUBLE PRECISION
);
