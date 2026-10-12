-- ============================================================================
-- LAN Secure Chat - Client-Local PostgreSQL Database Schema
-- Description: Schema for durable message storage on each client PC.
--              Stores local conversation history, outgoing/incoming messages,
--              risk metadata, and local synchronization state.
-- ============================================================================

DROP TABLE IF EXISTS local_sync_state CASCADE;
DROP TABLE IF EXISTS local_conversations CASCADE;
DROP TABLE IF EXISTS local_messages CASCADE;

-- 1. LOCAL_MESSAGES Table
-- Uses message_id (UUID) as PRIMARY KEY to ensure strict deduplication
CREATE TABLE local_messages (
    message_id VARCHAR(64) PRIMARY KEY,
    message_type VARCHAR(20) NOT NULL, -- 'DIRECT' or 'GROUP'
    conversation_partner VARCHAR(64) NOT NULL, -- other username for DIRECT, group_id for GROUP
    group_name VARCHAR(128),
    sender_username VARCHAR(64) NOT NULL,
    is_outgoing BOOLEAN NOT NULL DEFAULT FALSE,
    message_text TEXT NOT NULL,
    predicted_risk_score DOUBLE PRECISION,
    flagged BOOLEAN DEFAULT FALSE,
    model_status VARCHAR(32) DEFAULT 'ok',
    delivery_status VARCHAR(20) DEFAULT 'STORED', -- 'STORED', 'PENDING', 'DELIVERED', 'ACKNOWLEDGED'
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    stored_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_local_messages_partner ON local_messages(conversation_partner, timestamp);
CREATE INDEX idx_local_messages_timestamp ON local_messages(timestamp);

-- 2. LOCAL_CONVERSATIONS Table
CREATE TABLE local_conversations (
    conversation_key VARCHAR(128) PRIMARY KEY, -- 'USER:alice' or 'GROUP:grp-uuid'
    conversation_type VARCHAR(20) NOT NULL, -- 'DIRECT' or 'GROUP'
    target_id VARCHAR(64) NOT NULL,
    target_name VARCHAR(128) NOT NULL,
    last_message_preview TEXT,
    last_message_at TIMESTAMP WITH TIME ZONE,
    unread_count INT DEFAULT 0
);

-- 3. LOCAL_SYNC_STATE Table
CREATE TABLE local_sync_state (
    state_key VARCHAR(64) PRIMARY KEY,
    state_value TEXT,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
