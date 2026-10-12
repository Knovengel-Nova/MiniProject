# LAN Secure Chat — Wire Protocol Specification

**Version:** 1.0.0  
**Transport:** TLSv1.3 over TCP  
**Framing:** Newline-delimited JSON (`\n` separated UTF-8 strings)  
**Frame Limit:** 65,536 bytes (64 KB) maximum per message  

---

## 1. Framing and Envelope Standard

Communication between the Java Swing Client and Java Application Server uses newline-delimited JSON over a persistent TLS connection. Every line transmitted represents exactly one complete JSON object adhering to the standard `MessageEnvelope` structure.

### 1.1 Message Envelope Schema

```json
{
  "type": "MESSAGE_TYPE_STRING",
  "requestId": "uuid-v4",
  "messageId": "optional-uuid-v4",
  "timestamp": "2026-10-12T08:30:00.000Z",
  "status": "OK | ERROR | PENDING",
  "error": "Optional human-readable error description",
  "payload": { ... }
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `type` | String | Yes | Identifies the protocol operation (see Section 2). |
| `requestId` | String | Yes | Unique UUID for correlating requests with asynchronous responses. |
| `messageId` | String | Optional | Unique UUID of the specific message payload (for messages and ACKs). |
| `timestamp` | String (ISO-8601) | Yes | Instant of envelope creation. |
| `status` | String | Yes | `"OK"` for success, `"ERROR"` on failure, `"PENDING"` for queued state. |
| `error` | String | Conditional | Present when `status == "ERROR"`, explains the failure reason. |
| `payload` | Object | Optional | Typed request or response DTO object. |

---

## 2. Message Types and Contract Examples

### 2.1 User Registration

#### Request (`REGISTER_REQ`)
```json
{
  "type": "REGISTER_REQ",
  "requestId": "7b0d3e52-192a-4a67-9c98-dfd452a12101",
  "timestamp": "2026-10-12T08:30:01.120Z",
  "status": "OK",
  "payload": {
    "username": "alice",
    "password": "CorrectHorseBatteryStaple123!"
  }
}
```

#### Success Response (`REGISTER_RESP`)
```json
{
  "type": "REGISTER_RESP",
  "requestId": "7b0d3e52-192a-4a67-9c98-dfd452a12101",
  "timestamp": "2026-10-12T08:30:01.185Z",
  "status": "OK",
  "payload": {
    "userId": 1,
    "username": "alice",
    "message": "Registration successful"
  }
}
```

#### Duplicate Error Response (`REGISTER_RESP`)
```json
{
  "type": "REGISTER_RESP",
  "requestId": "7b0d3e52-192a-4a67-9c98-dfd452a12101",
  "timestamp": "2026-10-12T08:30:01.185Z",
  "status": "ERROR",
  "error": "Username 'alice' is already registered"
}
```

---

### 2.2 User Authentication (Login & Logout)

#### Login Request (`LOGIN_REQ`)
```json
{
  "type": "LOGIN_REQ",
  "requestId": "98a12bc4-3841-4562-b91c-1981ad720021",
  "timestamp": "2026-10-12T08:30:05.200Z",
  "status": "OK",
  "payload": {
    "username": "alice",
    "password": "CorrectHorseBatteryStaple123!"
  }
}
```

#### Success Response (`LOGIN_RESP`)
```json
{
  "type": "LOGIN_RESP",
  "requestId": "98a12bc4-3841-4562-b91c-1981ad720021",
  "timestamp": "2026-10-12T08:30:05.280Z",
  "status": "OK",
  "payload": {
    "userId": 1,
    "username": "alice",
    "sessionToken": "4e788bc2-a630-4e08-a532-6138622ff611",
    "message": "Login successful"
  }
}
```

#### Generic Failure Response (`LOGIN_RESP`)
*Note: Never reveals whether the username exists.*
```json
{
  "type": "LOGIN_RESP",
  "requestId": "98a12bc4-3841-4562-b91c-1981ad720021",
  "timestamp": "2026-10-12T08:30:05.280Z",
  "status": "ERROR",
  "error": "Invalid username or password"
}
```

---

### 2.3 User Presence Notification (`PRESENCE_NOTIF`)

Server broadcasts this envelope to all authenticated clients when a user connects or disconnects:
```json
{
  "type": "PRESENCE_NOTIF",
  "requestId": "f82b7194-e350-4d51-9960-97451299ab10",
  "timestamp": "2026-10-12T08:30:05.300Z",
  "status": "OK",
  "payload": {
    "userId": 1,
    "username": "alice",
    "status": "online",
    "timestamp": "2026-10-12T08:30:05.300Z"
  }
}
```

---

### 2.4 Direct Messaging Protocol

#### 1. Sender Dispatches Message (`DIRECT_MSG_REQ`)
*Sender identity is derived on the server from the authenticated socket session, never from payload.*
```json
{
  "type": "DIRECT_MSG_REQ",
  "requestId": "a012bf44-6721-4f10-bf91-a18928461298",
  "messageId": "msg-8f192b01-1249-4933-bf41-610293847561",
  "timestamp": "2026-10-12T08:31:00.100Z",
  "status": "OK",
  "payload": {
    "messageId": "msg-8f192b01-1249-4933-bf41-610293847561",
    "recipientUsername": "bob",
    "messageText": "Hey Bob, let's meet in Lab 3 at 4 PM."
  }
}
```

#### 2. Server Returns Immediate Processing Status (`DIRECT_MSG_RESP`)
```json
{
  "type": "DIRECT_MSG_RESP",
  "requestId": "a012bf44-6721-4f10-bf91-a18928461298",
  "messageId": "msg-8f192b01-1249-4933-bf41-610293847561",
  "timestamp": "2026-10-12T08:31:00.145Z",
  "status": "OK",
  "payload": {
    "messageId": "msg-8f192b01-1249-4933-bf41-610293847561",
    "recipientUsername": "bob",
    "deliveryStatus": "DELIVERED",
    "predictedRiskScore": 0.0821,
    "flagged": false,
    "modelStatus": "ok",
    "timestamp": "2026-10-12T08:31:00.145Z"
  }
}
```

#### 3. Server Pushes Message to Online Recipient (`DIRECT_MSG_PUSH`)
```json
{
  "type": "DIRECT_MSG_PUSH",
  "requestId": "push-01827461-8291-4902-8812-a81290348712",
  "messageId": "msg-8f192b01-1249-4933-bf41-610293847561",
  "timestamp": "2026-10-12T08:31:00.150Z",
  "status": "OK",
  "payload": {
    "messageId": "msg-8f192b01-1249-4933-bf41-610293847561",
    "senderUsername": "alice",
    "recipientUsername": "bob",
    "messageText": "Hey Bob, let's meet in Lab 3 at 4 PM.",
    "timestamp": "2026-10-12T08:31:00.100Z",
    "predictedRiskScore": 0.0821,
    "flagged": false,
    "modelStatus": "ok"
  }
}
```

#### 4. Recipient Emits Durable Storage ACK (`DELIVERY_ACK_REQ`)
*Only sent AFTER local PostgreSQL database transaction commits successfully!*
```json
{
  "type": "DELIVERY_ACK_REQ",
  "requestId": "ack-req-91028347-1928-4019-9182-a01928374651",
  "messageId": "msg-8f192b01-1249-4933-bf41-610293847561",
  "timestamp": "2026-10-12T08:31:00.220Z",
  "status": "OK",
  "payload": {
    "messageId": "msg-8f192b01-1249-4933-bf41-610293847561",
    "recipientUsername": "bob",
    "storedTimestamp": "2026-10-12T08:31:00.215Z"
  }
}
```

#### 5. Server Deletes Message Body & Returns Response (`DELIVERY_ACK_RESP`)
```json
{
  "type": "DELIVERY_ACK_RESP",
  "requestId": "ack-req-91028347-1928-4019-9182-a01928374651",
  "messageId": "msg-8f192b01-1249-4933-bf41-610293847561",
  "timestamp": "2026-10-12T08:31:00.240Z",
  "status": "OK",
  "payload": {
    "messageId": "msg-8f192b01-1249-4933-bf41-610293847561",
    "status": "ACKNOWLEDGED",
    "serverContentDeleted": true
  }
}
```

---

### 2.5 Group Messaging & Broadcast Protocol

#### 1. Group Creation (`GROUP_CREATE_REQ`)
```json
{
  "type": "GROUP_CREATE_REQ",
  "requestId": "grp-create-1029-3847-1928",
  "timestamp": "2026-10-12T08:32:00.000Z",
  "status": "OK",
  "payload": {
    "groupName": "Mini Project Group 18",
    "memberUsernames": ["bob", "charlie"]
  }
}
```

#### 2. Group Broadcast (`GROUP_MSG_REQ`)
```json
{
  "type": "GROUP_MSG_REQ",
  "requestId": "grp-msg-8812-4019-2918",
  "messageId": "msg-grp-91028374-4019-4821",
  "timestamp": "2026-10-12T08:32:10.000Z",
  "status": "OK",
  "payload": {
    "messageId": "msg-grp-91028374-4019-4821",
    "groupId": "grp-fef74c1d",
    "messageText": "Everyone please review the architecture documentation today!"
  }
}
```

#### 3. Group Push to Members (`GROUP_MSG_PUSH`)
```json
{
  "type": "GROUP_MSG_PUSH",
  "requestId": "push-grp-99128374-1029",
  "messageId": "msg-grp-91028374-4019-4821",
  "timestamp": "2026-10-12T08:32:10.050Z",
  "status": "OK",
  "payload": {
    "messageId": "msg-grp-91028374-4019-4821",
    "groupId": "grp-fef74c1d",
    "groupName": "Mini Project Group 18",
    "senderUsername": "alice",
    "messageText": "Everyone please review the architecture documentation today!",
    "timestamp": "2026-10-12T08:32:10.000Z",
    "predictedRiskScore": 0.0514,
    "flagged": false,
    "modelStatus": "ok"
  }
}
```

---

## 3. Python PDS HTTP REST Contract

The Java `PDSService` communicates with the local Python service over HTTP on `127.0.0.1:5005`.

### 3.1 Inference Endpoint (`POST /predict`)

#### Request
```json
{
  "requestId": "req-71928347-1928-4019",
  "messageId": "msg-8f192b01-1249-4933",
  "text": "URGENT!! Your account is suspended! Confirm your password at http://fake.com"
}
```

#### Response
```json
{
  "requestId": "req-71928347-1928-4019",
  "messageId": "msg-8f192b01-1249-4933",
  "predictedRiskScore": 0.8415,
  "flagged": true,
  "threshold": 0.70,
  "modelVersion": "linear-regression-v1",
  "status": "ok"
}
```

### 3.2 Health Check Endpoint (`GET /health`)

#### Response
```json
{
  "algorithm": "LinearRegression (scikit-learn)",
  "defaultThreshold": 0.70,
  "modelLoaded": true,
  "modelVersion": "linear-regression-v1",
  "service": "LAN Secure Chat PDS",
  "status": "ok"
}
```
