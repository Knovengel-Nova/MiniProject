package com.spit.lanchat.common.protocol;

/**
 * Enumeration of all supported protocol message types in LAN Secure Chat.
 */
public enum MessageType {
    // Registration & Auth
    REGISTER_REQ,
    REGISTER_RESP,
    LOGIN_REQ,
    LOGIN_RESP,
    LOGOUT_REQ,
    LOGOUT_RESP,

    // Presence & Contact Discovery
    USER_LIST_REQ,
    USER_LIST_RESP,
    PRESENCE_NOTIF,

    // Direct Messaging
    DIRECT_MSG_REQ,
    DIRECT_MSG_RESP,
    DIRECT_MSG_PUSH,

    // Group Management & Messaging
    GROUP_CREATE_REQ,
    GROUP_CREATE_RESP,
    GROUP_MEMBER_REQ,
    GROUP_MEMBER_RESP,
    GROUP_LIST_REQ,
    GROUP_LIST_RESP,
    GROUP_MSG_REQ,
    GROUP_MSG_RESP,
    GROUP_MSG_PUSH,

    // Delivery Acknowledgements & Sync
    DELIVERY_ACK_REQ,
    DELIVERY_ACK_RESP,
    STATUS_UPDATE_PUSH,
    PENDING_SYNC_REQ,
    PENDING_SYNC_RESP,

    // Error & Diagnostics
    ERROR_RESP
}
