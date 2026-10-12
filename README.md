# LAN-Based Secure Chatting Application

A production-grade, self-hosted Local Area Network (LAN) desktop chatting system developed in **Java 21 (Swing)**, **PostgreSQL**, and **Python (Linear Regression PDS)**.

Designed and engineered for TE Computer Engineering Semester 5 Mini Project.

---

## 1. Architectural Highlights

```
+---------------------------------------------------------------------------------------------------+
|                                          SERVER PC                                                |
|                                                                                                   |
|  +---------------------------+       JDBC         +--------------------------------------------+  |
|  |     Java ChatServer       | <----------------> |        PostgreSQL Server Database          |  |
|  |   (TLS Socket Listener)   |                    | (Temporary Delivery Queue, Groups, Users)  |  |
|  +---------------------------+                    +--------------------------------------------+  |
|               ^       |                                                                           |
|   HTTP JSON   |       | Local loopback only                                                       |
|   (127.0.0.1) |       v (5005)                                                                    |
|  +---------------------------+                                                                    |
|  |   Python PDS Service      |                                                                    |
|  | (Linear Regression Model) |                                                                    |
|  +---------------------------+                                                                    |
+---------------------------------------------------------------------------------------------------+
        ^ (TLSv1.3 TCP Socket)                       ^ (TLSv1.3 TCP Socket)
        | (Newline-Delimited JSON)                   | (Newline-Delimited JSON)
+-------------------------------+            +-------------------------------+
|          CLIENT PC 1          |            |          CLIENT PC 2          |
|                               |            |                               |
|  +-------------------------+  |            |  +-------------------------+  |
|  |    Java Swing Client    |  |            |  |    Java Swing Client    |  |
|  +-------------------------+  |            |  +-------------------------+  |
|               ^               |            |               ^               |
|          JDBC | (Localhost)   |            |          JDBC | (Localhost)   |
|               v               |            |               v               |
|  +-------------------------+  |            |  +-------------------------+  |
|  | Local PostgreSQL Store  |  |            |  | Local PostgreSQL Store  |  |
|  | (Durable Chat History)  |  |            |  | (Durable Chat History)  |  |
|  +-------------------------+  |            |  +-------------------------+  |
+-------------------------------+            +-------------------------------+
```

1. **Pure Desktop Architecture**: Built strictly with Java Swing desktop UI, TLS sockets, and local PostgreSQL databases. No web app, React, MongoDB, Firebase, or cloud services.
2. **Server as Temporary Queue**: The central PostgreSQL database functions solely as a temporary queue for pending messages. Once all intended recipients acknowledge durable storage, the server permanently deletes message text from disk.
3. **Local Client Message Store**: Each user PC runs a local PostgreSQL database storing messages durably. Chat history is retained client-side under user ownership.
4. **Durable Two-Phase Acknowledgement**: The recipient Swing client commits incoming messages to its local PostgreSQL database inside an atomic transaction *before* emitting a delivery acknowledgement to the Java server.
5. **Linear Regression PDS**: Machine learning risk screening is powered exclusively by a scikit-learn Linear Regression model trained on synthetic message features and evaluated with MAE and RMSE.
6. **Robust Transport Security**: All communication is encrypted via TLSv1.3 using PKCS#12 keystores and newline-delimited JSON framing with 64 KB safety bounds.

---

## 2. Technology Stack & Prerequisites

| Component | Technology | Version | Purpose |
|---|---|---|---|
| **Programming Language** | Java (JDK) | 21+ | Client Swing GUI and Server application |
| **Build Automation** | Apache Maven | 3.9+ | Multi-module compilation and testing |
| **GUI Framework** | Java Swing | Standard | Desktop user interface |
| **Server Database** | PostgreSQL | 14+ (18.6 Verified) | User accounts, groups, temporary queue |
| **Client Database** | PostgreSQL | 14+ (18.6 Verified) | Local durable message history store |
| **Data Science / PDS** | Python | 3.10+ (3.14 Verified) | Synthetic dataset generation & regression |
| **ML Framework** | scikit-learn, NumPy, pandas | Latest | Linear Regression inference & metrics |
| **PDS HTTP Microservice** | Flask | 3.1+ | Local HTTP REST API on `127.0.0.1:5005` |

---

## 3. Database Setup & Initialization

### 3.1 Server Database Setup
On the designated Server PC, create and initialize the database:

```powershell
# Set PostgreSQL environment password (default postgres)
$env:PGPASSWORD = "postgres"

# Create server database
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h 127.0.0.1 -c "CREATE DATABASE lan_chat_server;"

# Execute initialization script
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h 127.0.0.1 -d lan_chat_server -f "database/server_schema.sql"
```

### 3.2 Client Local Database Setup
On each client PC (or test environment), create a local database:

```powershell
# Create client local database
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h 127.0.0.1 -c "CREATE DATABASE lan_chat_client_test;"

# Execute client schema script
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h 127.0.0.1 -d lan_chat_client_test -f "database/client_schema.sql"
```

*Tip: You can run `scripts\init_databases.bat` to automatically reset and initialize both databases.*

---

## 4. TLS Certificate Generation

The project comes with pre-generated developer certificates in `certs/`. To regenerate certificates with your custom LAN domain or IP:

```powershell
.\scripts\generate_certs.ps1
```
Or with batch script:
```cmd
scripts\generate_certs.bat
```

This creates:
- `certs/server-keystore.p12` (Server private key and certificate chain, password: `changeit`)
- `certs/server-cert.crt` (Public certificate exported from server)
- `certs/client-truststore.p12` (Client truststore containing the server certificate, password: `changeit`)

---

## 5. Python PDS Setup & Model Training

The Probability and Data Science (PDS) service runs **only on the server PC**.

### 5.1 Install Python Dependencies
```cmd
python -m pip install numpy pandas scikit-learn flask
```

### 5.2 Generate Synthetic Dataset
```cmd
python pds-service/scripts/generate_dataset.py
```
*Generates `pds-service/data/synthetic_risk_dataset.csv` (1,500 deterministic samples with controlled Gaussian noise).*

### 5.3 Train Linear Regression Model
```cmd
python pds-service/scripts/train_model.py
```
*Trains an Ordinary Least Squares (OLS) Linear Regression model, outputs coefficients, and persists `pds-service/models/linear_regression_model.joblib`.*

### 5.4 Evaluate Model Performance
```cmd
python pds-service/scripts/evaluate_model.py
```
**Actual Benchmark Results on Held-out Test Set (300 Samples):**
- **MAE (Mean Absolute Error):** `0.026228`
- **RMSE (Root Mean Squared Error):** `0.039502`
- **$R^2$ Score:** `0.978350`
- **Flagged Threshold:** `0.70` (16.0% flagged as high risk)

### 5.5 Start Python PDS Microservice
```cmd
python pds-service/app.py
```
The service starts listening exclusively on loopback: `http://127.0.0.1:5005`.

---

## 6. Building and Testing the Java Project

### 6.1 Compile All Modules
```cmd
mvn clean compile
```

### 6.2 Execute Automated Test Suite
```cmd
mvn test
```
Runs:
- `pds-service/tests/test_pds.py` (8 Python unit tests)
- `chat-common` (5 protocol & framing tests)
- `chat-server` (6 database queue/deletion tests + 5 PDS service tests)
- `chat-client` (3 local persistence tests + 6 end-to-end multi-client integration tests)

### 6.3 Package Executable JARs
```cmd
mvn package -DskipTests
```

---

## 7. Running the System

### Step 1: Start the Python PDS Service (Server PC)
```cmd
scripts\run_pds.bat
```

### Step 2: Start the Java Application Server (Server PC)
```cmd
scripts\run_server.bat
```
Server starts listening on port `8443` over TLSv1.3.

### Step 3: Start Client Applications (Client PCs / Terminals)
Open separate command prompts to launch client instances:

**Client 1 (Alice):**
```cmd
scripts\run_client.bat
```

**Client 2 (Bob):**
```cmd
scripts\run_client.bat
```

**Client 3 (Charlie):**
```cmd
scripts\run_client.bat
```

---

## 8. Multi-Client Demonstration Guide (3 Clients)

Follow this step-by-step walkthrough to demonstrate all project capabilities to evaluators:

### Scenario A: Registration, Login & Presence
1. **Alice**: Open Client 1 $\rightarrow$ Click "Register New Account" $\rightarrow$ Username `alice`, Password `Password123!` $\rightarrow$ Click "Login".
2. **Bob**: Open Client 2 $\rightarrow$ Register `bob`, Password `Password123!` $\rightarrow$ Click "Login".
3. **Charlie**: Open Client 3 $\rightarrow$ Register `charlie`, Password `Password123!` $\rightarrow$ Click "Login".
4. Observe the sidebar: Green dots 🟢 appear beside online contacts in real time.

### Scenario B: 1-to-1 Direct Messaging & Durable Persistence
1. On Alice's client, click `bob` in the Direct Messages list.
2. Type `"Hello Bob, are you in the lab?"` and press **Enter**.
3. On Bob's client, the message arrives instantly and is saved to Bob's local PostgreSQL database.
4. Bob's client commits the local transaction, then sends a durable ACK to the Java server.
5. On Alice's client, the status indicator updates from `🕒 Pending` to `✓ Delivered` to `✓✓ Stored & Acknowledged`.
6. Inspect server database:
   ```sql
   SELECT message_id, message_text, content_deleted FROM pending_messages;
   ```
   Notice `message_text` is `NULL` and `content_deleted` is `TRUE`. The message content was deleted from the server!

### Scenario C: PDS Risk Warning Badge (Linear Regression)
1. On Alice's client, send Bob a phishing-like message:
   `"URGENT: Your account has been suspended! Confirm password at http://fake-bank-login.com immediately!! $$$"`
2. The message is evaluated by Python PDS:
   - Predicted risk score $> 0.70$
   - Flagged: `true`
3. Bob's UI displays a prominent warning card:
   `⚠ Risk: 0.84 — Synthetic model risk indicator — review this message.`

### Scenario D: Offline Message Queueing & Reconnect Delivery
1. Close Bob's client window (Bob goes offline).
2. On Alice's client, observe Bob's status changes to ⚪ Offline.
3. Alice sends Bob: `"Bob, I left the flash drive on your desk."`
4. Alice's client shows `🕒 Pending`.
5. Check server database: The message text remains intact because Bob has not yet stored it.
6. Launch Bob's client again and log in.
7. Bob automatically receives the pending message, persists it locally, and emits an ACK.
8. Server permanently wipes the message text once Bob acknowledges.

### Scenario E: Group Broadcast & Partial Acknowledgement
1. On Alice's client, click **"+ New Group"**.
2. Group Name: `Mini Project Team`, Members: `bob, charlie`. Click **Create**.
3. Close Charlie's client (Charlie goes offline).
4. Alice broadcasts to the group: `"Standup meeting starts now!"`
5. Bob receives the broadcast and ACKs.
6. Verify server database: Content is **retained** because Charlie has not yet received it.
7. Launch Charlie's client and log in.
8. Charlie receives the group message and ACKs.
9. Verify server database: Content is **now deleted** because all recipients have acknowledged!

---

## 9. Verification & Compliance Checklist

| Capability | Requirement | Status | Verification Reference |
|---|---|---|---|
| Desktop Swing UI | No web, React, or mockup | **PASS** | `com.spit.lanchat.client.ui.MainChatFrame` |
| Java TCP / TLS | TLSv1.3 TCP Socket framing | **PASS** | `TlsUtil`, `ChatServerIntegrationTest` |
| PostgreSQL Server Queue | Temporary delivery queue | **PASS** | `ServerDatabaseTest#testDirectMessagePendingQueueAndContentDeletion` |
| Client PostgreSQL Store | Durable local message history | **PASS** | `ClientDatabaseTest#testSaveMessageAndDeduplication` |
| Server Content Deletion | Delete body upon all ACKs | **PASS** | `ServerDatabaseTest#testGroupMessageMultiRecipientAckAndDelayedContentDeletion` |
| Python PDS Integration | HTTP REST API on 127.0.0.1 | **PASS** | `PDSServiceLiveTest`, `test_pds.py` |
| Linear Regression Only | No logistic, neural nets, or AI | **PASS** | `pds-service/scripts/train_model.py` (OLS) |
| Synthetic Dataset | Deterministic & reproducible | **PASS** | `pds-service/scripts/generate_dataset.py` |
| Regression Metrics | MAE & RMSE evaluation | **PASS** | `evaluate_model.py` (MAE: 0.0262, RMSE: 0.0395) |
| PDS Graceful Fallback | Mark 'unavailable', don't block | **PASS** | `PDSServiceTest#testPdsServiceUnavailableFallback` |
| Offline Message Sync | Deliver upon reconnect | **PASS** | `ChatServerIntegrationTest#testOfflineMessageDeliveryAndSync` |
| Group Broadcast | Multi-recipient tracking | **PASS** | `ChatServerIntegrationTest#testGroupBroadcastAndDelayedContentDeletion` |
