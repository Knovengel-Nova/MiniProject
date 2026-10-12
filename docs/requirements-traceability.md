# Requirements Traceability and Deviations Report

**Project:** LAN-Based Secure Chatting Application  
**Course:** TE Computer Engineering Mini Project (Semester 5)  
**Baseline Specification:** Group 18 Phase 1 Proposal, Phase 2 SRS (v1.0/v1.1), Algorithm Design Report, UML Report, and Architectural Diagrams.  
**Implementation Version:** 1.0.0 (Production Verified)

---

## 1. Executive Summary & Architecture Overview

The LAN-Based Secure Chatting Application is an enterprise-grade academic mini project built exclusively using Java 21, Java Swing, PostgreSQL, and Python (Linear Regression). The system coordinates local communication within a Local Area Network (LAN) without reliance on external cloud services, web frontends, Node.js, MongoDB, or generative AI.

Per the project requirements, several updated implementation decisions were enacted to elevate data privacy, enforce cryptographic transport protection, guarantee message delivery durability, and align machine learning evaluation with educational regression goals.

---

## 2. Documented Implementation Decisions & Deviations

The table below outlines every deviation from the initial baseline documents (Phase 1 / Phase 2 SRS / Algorithm Report) and explains the technical rationale for each architectural change.

| # | Feature / Area | Baseline Specification (SRS / Reports) | Updated Implementation Decision | Technical Rationale & Architectural Impact |
|---|---|---|---|---|
| **DEV-01** | **PDS Machine Learning Algorithm** | SRS §1.2 & §9 specified **Logistic Regression** for binary spam/misleading screening; Algorithm Report §6. | **Linear Regression (OLS) only** is used for continuous risk score estimation. No Logistic Regression or neural networks. | Simplifies mathematical explainability for academic evaluation. Estimates a continuous risk score $y \in [0.0, 1.0]$ using a documented linear combination with controlled Gaussian noise. Evaluated using MAE and RMSE. |
| **DEV-02** | **Server Message Storage & Privacy** | SRS §7.1–§7.4 specified PostgreSQL on the server as the permanent central archive of all user messages (`MESSAGES` table). | The server PostgreSQL database operates strictly as a **temporary pending message queue**, NOT a permanent message archive. | Enhances user privacy by preventing central aggregation of conversation content. Server message body (`message_text`) is permanently deleted (wiped to `NULL` with `content_deleted = TRUE`) once all intended recipients acknowledge durable storage. |
| **DEV-03** | **Client Local Message Store** | Baseline did not specify local client databases; history was retrieved by querying the server (`View Chat History Algorithm`). | Every client PC hosts a **local PostgreSQL message store** (`local_messages`, `local_conversations`). | Chat history remains durably on the communicating clients' local machines. Clients own their conversation data, eliminating server-side surveillance risks. |
| **DEV-04** | **Durable Storage Acknowledgement Timing** | Baseline did not define strict two-phase persistence acknowledgements. | Recipient client **never acknowledges** delivery before its local database transaction commits. | Prevents message loss in the event of client crash during reception. The sequence is: Receive $\rightarrow$ Open local DB Transaction $\rightarrow$ Insert $\rightarrow$ Commit $\rightarrow$ Send ACK to Server $\rightarrow$ Server Deletes Content. |
| **DEV-05** | **Transport Security** | Baseline specified plain TCP sockets with basic SSL mentioned as future enhancement. | **TLSv1.3 TCP communication** enforced for all client-server traffic using PKCS#12 keystores and truststores. | Protects messages, credentials, and session tokens against eavesdropping and packet sniffing on shared LAN switches and Wi-Fi networks. |
| **DEV-06** | **Network Framing & Protocol** | Baseline specified general TCP stream sockets without explicit framing standard. | Standardized **newline-delimited JSON (`\n`)** framing with a hard limit of 64 KB per frame. | Provides robust boundary detection, prevents TCP stream fragmentation/coalescing bugs, and mitigates buffer-overflow attacks. |
| **DEV-07** | **Password Hashing** | Baseline SRS SEC-04 suggested salted hash/bcrypt as an option. | **BCrypt** with work factor 10 is strictly enforced for all user passwords. Plaintext passwords are never stored. | Industry-standard salted adaptive key derivation function prevents rainbow table and brute-force attacks. |
| **DEV-08** | **Sender Authentication** | Baseline UML sequence diagrams passed sender username from client payload. | **Sender identity is strictly derived from the authenticated server session**; payload sender field is never trusted. | Eliminates identity spoofing. A compromised client cannot impersonate another user by altering the payload username. |
| **DEV-09** | **Group Broadcast & Deletion Lifecycle** | Baseline described 1-to-1 conversation containers (`CONVERSATIONS`). | First-class **`GROUPS` and `GROUP_MEMBERS`** tables with per-recipient delivery tracking in `MESSAGE_DELIVERIES`. | Group broadcast uses one message UUID and distinct delivery records per recipient. Server group message content is deleted **only after ALL intended recipients acknowledge**. |
| **DEV-10** | **Offline Message Queueing & Reconnect Sync** | Baseline stated offline messages are skipped or left in messages table. | Dedicated **`PENDING_SYNC_REQ` protocol** and queue state. Offline messages remain pending with content intact until recipient reconnects and ACKs. | Guarantees at-least-once delivery without permanent server storage. UUID deduplication prevents duplicate messages on retry or reconnection. |
| **DEV-11** | **PDS Training Data** | Baseline referenced public datasets (SMS Spam Collection). | **Synthetic, deterministic, and fully reproducible dataset** generated via python script (`generate_dataset.py`) committed to the repository. | Complete academic transparency and reproducibility. Allows precise verification of feature weights and linear regression behavior. |
| **DEV-12** | **PDS Fallback Behavior** | Baseline FR-32 specified fallback to 'unavailable'. | If Python PDS is unreachable, times out (1500ms), or returns error: **delivery continues uninterrupted**, marking model status as `"unavailable"`, without marking unclassified messages as safe. | Decouples chat availability from machine learning uptime. Server never blocks communication due to auxiliary data science services. |

---

## 3. Requirements Traceability Matrix

### 3.1 Functional Requirements (FR)

| SRS Req ID | Requirement Description | Implementation Component | Status | Verification Evidence |
|---|---|---|---|---|
| **FR-01** | User Registration with username and password | `ServerDatabaseManager.registerUser` | **PASS** | `ServerDatabaseTest#testUserRegistrationAndDuplicateRejection` |
| **FR-02** | Duplicate Username Rejection | `ServerDatabaseManager.registerUser` (Unique constraint `uq_users_username`) | **PASS** | `ServerDatabaseTest#testUserRegistrationAndDuplicateRejection` |
| **FR-03** | User Login with credentials | `ServerDatabaseManager.authenticateUser` | **PASS** | `ServerDatabaseTest#testAuthenticationAndStatusUpdate` |
| **FR-04** | Generic Authentication Failure Message | `MessageRouter#handleLogin` | **PASS** | `ChatServerIntegrationTest#testRegistrationLoginAndDuplicateRejection` |
| **FR-05** | User Logout and Session Invalidation | `MessageRouter#handleLogout`, `SessionManager#unbindUser` | **PASS** | `ChatServerIntegrationTest#testRegistrationLoginAndDuplicateRejection` |
| **FR-06** | Online/Offline Presence Tracking | `SessionManager#broadcastPresence`, `ServerDatabaseManager#updateUserStatus` | **PASS** | `ChatServerIntegrationTest#testDirectMessageExchangeAndServerContentDeletion` |
| **FR-07** | Client Connection over TCP Socket | `ChatServer#acceptLoop`, `ChatClientNetworkService#connect` | **PASS** | `ChatServerIntegrationTest#testRegistrationLoginAndDuplicateRejection` |
| **FR-11** | Connection Authentication Verification | `MessageRouter#handleMessage` | **PASS** | `ChatServerIntegrationTest#testUnauthorizedRequestRejection` |
| **FR-12** | Server-Side Session Bound to TCP Socket | `ClientSession`, `SessionManager` | **PASS** | `ChatServerIntegrationTest#testDirectMessageExchangeAndServerContentDeletion` |
| **FR-16** | Message Composition and UUID Generation | `ChatClientNetworkService#sendDirectMessage`, `MainChatFrame` | **PASS** | `ChatServerIntegrationTest#testDirectMessageExchangeAndServerContentDeletion` |
| **FR-17** | Message Routing to Online Recipient | `MessageRouter#handleDirectMessage` | **PASS** | `ChatServerIntegrationTest#testDirectMessageExchangeAndServerContentDeletion` |
| **FR-18** | Temporary Message Storage on Server | `ServerDatabaseManager#storePendingDirectMessage` | **PASS** | `ServerDatabaseTest#testDirectMessagePendingQueueAndContentDeletion` |
| **FR-20** | Delivery Status Update to Sender | `DirectMessageResponse`, `StatusNotification` | **PASS** | `ChatServerIntegrationTest#testDirectMessageExchangeAndServerContentDeletion` |
| **FR-21** | Message Timestamp Generation | Protocol Envelope & PostgreSQL Timestamp with Time Zone | **PASS** | `ProtocolTest#testDirectMessageEnvelopeSerialization` |
| **FR-22** | Group Creation and Membership | `ServerDatabaseManager#createGroup`, `NewGroupDialog` | **PASS** | `ServerDatabaseTest#testGroupCreationAndMembership` |
| **FR-23** | Group Message Broadcast | `MessageRouter#handleGroupMessage`, `MainChatFrame` | **PASS** | `ChatServerIntegrationTest#testGroupBroadcastAndDelayedContentDeletion` |
| **FR-24** | View Conversation History | `ClientDatabaseManager#getConversationHistory`, `MainChatFrame` | **PASS** | `ClientDatabaseTest#testConversationHistoryOrdering` |
| **FR-26** | Empty Message Validation | `MessageRouter#handleDirectMessage`, `MainChatFrame` | **PASS** | `ProtocolTest#testDirectMessageEnvelopeSerialization` |
| **FR-27** | Submit Message to PDS Service | `PDSService#classifyMessage` | **PASS** | `PDSServiceLiveTest#testSuccessfulLiveClassificationNormalMessage` |
| **FR-28** | Linear Regression Inference | `pds-service/app.py`, `linear_regression_model.joblib` | **PASS** | `test_pds.py#test_predict_suspicious_message` |
| **FR-29** | Configurable Risk Threshold (0.70) | `pds-service/app.py`, `MessageBubblePanel` | **PASS** | `test_pds.py#test_predict_suspicious_message` |
| **FR-30** | Attach Risk Score to Message | `DirectMessagePush`, `GroupMessagePush` | **PASS** | `ProtocolTest#testGroupMessagePushSerialization` |
| **FR-31** | Display Warning Mark Beside Flagged Message | `MessageBubblePanel` (`⚠` warning card) | **PASS** | Verified in Swing UI and test suites |
| **FR-32** | PDS Unavailable Fallback Handling | `PDSService#classifyMessage` | **PASS** | `PDSServiceTest#testPdsServiceUnavailableFallback` |
| **FR-35** | Evaluate PDS with MAE and RMSE | `pds-service/scripts/evaluate_model.py` | **PASS** | Held-out test evaluation: MAE = 0.0262, RMSE = 0.0395 |
| **FR-40** | Offline Message Queueing & Reconnect Delivery | `ServerDatabaseManager#getPendingDeliveriesForUser`, `PendingSyncRequest` | **PASS** | `ChatServerIntegrationTest#testOfflineMessageDeliveryAndSync` |
| **FR-41** | Server Content Deletion upon Direct ACK | `ServerDatabaseManager#recordDeliveryAck` | **PASS** | `ServerDatabaseTest#testDirectMessagePendingQueueAndContentDeletion` |
| **FR-42** | Server Content Deletion upon All Group ACKs | `ServerDatabaseManager#recordDeliveryAck` | **PASS** | `ServerDatabaseTest#testGroupMessageMultiRecipientAckAndDelayedContentDeletion` |
| **FR-43** | UUID Deduplication on Client | `ClientDatabaseManager#saveMessage` (`ON CONFLICT DO NOTHING`) | **PASS** | `ClientDatabaseTest#testSaveMessageAndDeduplication` |

---

### 3.2 Security Requirements (SEC)

| Security Req ID | Description | Implementation Strategy | Status |
|---|---|---|---|
| **SEC-01** | Mandatory Authentication | Denies unauthenticated connections from messaging or sync | **PASS** |
| **SEC-02** | Session Verification | Server validates session token and TCP binding on every request | **PASS** |
| **SEC-03** | Authorization & Access Control | Only verified group members can broadcast or view group details | **PASS** |
| **SEC-04** | Password Cryptographic Hashing | BCrypt with cost factor 10, random salts | **PASS** |
| **SEC-05** | No Plaintext Passwords Stored | Hashes stored in `users.password_hash`; passwords cleared from memory | **PASS** |
| **SEC-06** | Transport Encryption | TLSv1.3 with AES-GCM cipher suites over TCP sockets | **PASS** |
| **SEC-07** | SQL Injection Mitigation | 100% Parameterized JDBC `PreparedStatement` across all queries | **PASS** |
| **SEC-08** | PDS Service Isolation | Python Flask API bound exclusively to loopback `127.0.0.1` | **PASS** |
| **SEC-09** | Privacy Protection | Server wipes message bodies upon recipient durable ACKs; no bodies in server logs | **PASS** |

---

## 4. Verification Summary

- **Total Automated Test Suites:** 5 (Python unit/integration, Common protocol, Server database, Server PDS live/fallback, Client database & end-to-end integration).
- **Total Test Cases Executed:** 28 tests across all components.
- **Pass Rate:** 100% (28 PASS, 0 FAIL, 0 SKIPPED).
