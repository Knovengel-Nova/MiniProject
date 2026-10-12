-- ============================================================================
-- LAN Secure Chat - Server PostgreSQL Database Schema
-- Description: Schema for user accounts, groups, temporary pending message queue,
--              delivery tracking, and operational metrics.
-- Note: Message content is deleted once all intended recipients acknowledge durable storage.
-- ============================================================================

-- Drop existing tables in reverse dependency order for clean recreation
DROP TABLE IF EXISTS operational_metrics CASCADE;
DROP TABLE IF EXISTS message_deliveries CASCADE;
DROP TABLE IF EXISTS pending_messages CASCADE;
DROP TABLE IF EXISTS group_members CASCADE;
DROP TABLE IF EXISTS groups CASCADE;
DROP TABLE IF EXISTS conversations CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- 1. USERS Table
CREATE TABLE users (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'offline',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_username UNIQUE (username)
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_status ON users(status);

-- 2. CONVERSATIONS Table (Direct 1-to-1 pairings)
CREATE TABLE conversations (
    conversation_id SERIAL PRIMARY KEY,
    user1_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    user2_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_conversation_pair UNIQUE (user1_id, user2_id),
    CONSTRAINT chk_user_order CHECK (user1_id < user2_id)
);

CREATE INDEX idx_conversations_user1 ON conversations(user1_id);
CREATE INDEX idx_conversations_user2 ON conversations(user2_id);

-- 3. GROUPS Table
CREATE TABLE groups (
    group_id VARCHAR(64) PRIMARY KEY,
    group_name VARCHAR(128) NOT NULL,
    created_by INT NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_groups_created_by ON groups(created_by);

-- 4. GROUP_MEMBERS Table
CREATE TABLE group_members (
    group_id VARCHAR(64) NOT NULL REFERENCES groups(group_id) ON DELETE CASCADE,
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, user_id)
);

CREATE INDEX idx_group_members_user ON group_members(user_id);

-- 5. PENDING_MESSAGES Table (Temporary Queue)
-- Message content is temporary! It is overwritten/nullified and marked content_deleted = TRUE
-- once all required recipient delivery records reach 'ACKNOWLEDGED'.
CREATE TABLE pending_messages (
    message_id VARCHAR(64) PRIMARY KEY,
    message_type VARCHAR(20) NOT NULL, -- 'DIRECT' or 'GROUP'
    conversation_id INT REFERENCES conversations(conversation_id) ON DELETE SET NULL,
    group_id VARCHAR(64) REFERENCES groups(group_id) ON DELETE SET NULL,
    sender_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    sender_username VARCHAR(64) NOT NULL,
    message_text TEXT, -- Nullified once content_deleted is TRUE
    predicted_risk_score DOUBLE PRECISION,
    flagged BOOLEAN DEFAULT FALSE,
    model_version VARCHAR(64),
    model_status VARCHAR(32) DEFAULT 'ok', -- 'ok', 'unavailable', 'error'
    content_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_pending_messages_sender ON pending_messages(sender_id);
CREATE INDEX idx_pending_messages_content_deleted ON pending_messages(content_deleted);

-- 6. MESSAGE_DELIVERIES Table (Per-recipient tracking)
CREATE TABLE message_deliveries (
    delivery_id SERIAL PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL REFERENCES pending_messages(message_id) ON DELETE CASCADE,
    recipient_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    recipient_username VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'DELIVERED', 'ACKNOWLEDGED'
    delivered_at TIMESTAMP WITH TIME ZONE,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_message_recipient UNIQUE (message_id, recipient_id)
);

CREATE INDEX idx_deliveries_recipient_status ON message_deliveries(recipient_id, status);
CREATE INDEX idx_deliveries_message_id ON message_deliveries(message_id);

-- 7. OPERATIONAL_METRICS Table
CREATE TABLE operational_metrics (
    metric_id SERIAL PRIMARY KEY,
    metric_name VARCHAR(64) NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    details TEXT,
    recorded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_operational_metrics_name ON operational_metrics(metric_name);
