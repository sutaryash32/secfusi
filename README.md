# 🔐 Secufusion — Multi-Tenant Security Platform

## What is Secufusion?
Secufusion is a **browser-based security platform** that companies use to monitor and protect their employees' browser activity. It works through a **Chrome extension** that enforces security policies in real-time.

---

## 🏗️ Tenant Hierarchy

```
Master MSSP (Secufusion — Top Level / God Mode)
        ↓
MSSP (Reseller / Middle Layer — e.g. Local IT Security Company)
        ↓
Enterprise (End Client — e.g. Motivity Labs)
        ↓
Users & Devices
```

### Who is who?
| Role | Description | Real World Example |
|------|-------------|-------------------|
| **Master MSSP** | Platform owner, creates MSSPs and Enterprise tenants directly | Apple |
| **MSSP** | Reseller/manager who manages multiple Enterprise clients | Apptronix |
| **Enterprise** | Actual company using the product, has its own users & devices | You buying from Apptronix |

### Why is MSSP needed?
- Secufusion cannot directly manage thousands of small companies
- MSSP acts as a **local manager** — handles setup, support, and policies for their clients
- Secufusion only deals with MSSPs → MSSPs deal with their clients
- **Scales the business without extra effort from Secufusion**

### 💡 Real World Examples

**Example 1 — Apple & Apptronix:**
```
Apple (Master MSSP) — banaya iPhone (product)
      ↓
Apptronix (MSSP) — reseller, setup kiya, support diya
      ↓
Tu (Enterprise) — end customer
```

**Example 2 — Jio & Local Cable Provider:**
```
Jio (Master MSSP) — network owner
      ↓
Local Cable/Internet Provider (MSSP) — tera ghar tak internet pahunchaya
      ↓
Tu (Enterprise) — end user
```

**Example 3 — Direct (No MSSP):**
```
Master MSSP directly → Enterprise banaya (is document mein yahi hua)
Motivity Labs ko directly Master MSSP ne manage kiya — beech mein koi MSSP nahi
```

---

## 📦 Platform Overview — 6 Parts

### Part 1 — Tenant Creation & Setup
- Master MSSP logs in and creates a new tenant via a **4-step wizard**
  - Step 1: Company info (name, domain, email, region, org type, SSO type)
  - Step 2: Contact details (primary admin)
  - Step 3: Subscription plan (Trial / Freemium / Basic / Standard / Premium)
  - Step 4: Review & Create
- Provisioning takes **~60 seconds**
- After creation, tenant gets a unique **portal URL, realm name, and admin credentials**

### Part 2 — Extension Flow & Configuration
- Secufusion works via a **Chrome browser extension**
- After tenant creation, an **API Key** is generated to connect the extension to the tenant
- Extension is configured via Chrome's Service Worker console:
```javascript
chrome.storage.local.set({
  authMode: 'apikey',
  apiKey: '<YOUR_API_KEY>',
  tenantHost: '<TENANT_HOST_NAME>',
  appEnv: 'DEV'
});
```

### Part 3 — Policies & Access Control
| Policy Type | What it does |
|-------------|-------------|
| **Extension Policy** | Controls browser behavior and compliance rules |
| **DLP Policy** | Prevents sensitive data leakage (blocks copy/paste, PII input) |
| **Network Policy** | URL filtering, DNS filtering, Zero Trust access control |

#### DLP (Data Leak Prevention) — PII Detection:
- Detects and blocks: **SSN, Credit Card, Phone Number, Email**
- Actions: **Block Input** or **Warn Only**
- Controls: **Block Copy/Paste**, **Block Cross-Application Paste**

### Part 4 — Users & Devices
- **Users**: Manage user accounts, roles, groups, and activity
- **Devices**: Every device with the extension installed is auto-registered
- Devices are organized into **API Key Groups**

### Part 5 — Security Operations
| Feature | Description |
|---------|-------------|
| **Security Events** | Real-time logs of every browser action |
| **Security Alerts** | Severity-based alerts (Critical / High / Medium / Low / Blocked / Warned) |
| **Incidents** | Formal investigation workflow (Open → Investigating → Resolved → Closed) |

#### Incident Priority Levels:
- **P1** = Critical
- **P2** = High
- **P3** = Medium
- **P4** = Low

### Part 6 — Analytics & Insights
- Tenant Activity Summary (devices, events, violations)
- Activity Trend charts
- Browser & Device distribution
- Top domains, top active users, violations by type

---

## 🔑 Key Concepts

### API Key
- Used to authenticate the Chrome extension with the tenant
- Generated from: **Settings > API Key Management**
- Each tenant has a unique API key

### SSO Types
- **APIKEY** — Extension authenticates via API key
- **AZURE** — Azure Active Directory SSO

### Organization Types
- **Master MSSP** — Top level, full platform control
- **MSSP** — Reseller, manages multiple enterprise clients
- **Enterprise** — End client company

---

## 📋 Quick Reference — Tenant Creation

| Step | What happens |
|------|-------------|
| Login | softwareadmin logs into Secufusion |
| Dashboard | Click "+" to create new tenant |
| Step 1/4 | Company info, org type, SSO type |
| Step 2/4 | Primary contact / admin details |
| Step 3/4 | Select subscription plan |
| Step 4/4 | Review and click "Create Organization" |
| Provisioning | ~60 seconds — realm, clients, admin account created |
| Verify | Check tenant is "Active" in tenant list |

---

## 🔄 Complete Business Flow

### 1️⃣ Master MSSP Admin Flow
```
Login (softwareadmin)
      ↓
Master Portal (master.agenticworkspace.ai)
      ↓
Dashboard → All Tenants List
      ↓
Create Tenant (4-step wizard)
  → Company Info → Contact → Subscription Plan → Review & Create
      ↓
Org Created! → Welcome email sent to tenant admin
```
**Portal Features:** Tenant Management, Identity & Access, MSSP Monitoring, Reports & Analytics

---

### 2️⃣ Tenant Admin — First Login Flow
```
Welcome Email milta hai (login URL + username)
      ↓
First Login? → Forgot Password → Reset link (5 min expiry)
      ↓
Password set karo
      ↓
Tenant Dashboard
  ├── SecOps
  ├── AI Ops
  ├── Policy Profile
  ├── Zero Trust
  └── Insights
```

---

### 3️⃣ Browser Extension Setup
```
Identity & Access → API Keys
      ↓
API Key banao (90 days, SHA-256) — sirf ek baar dikhta hai!
      ↓
Chrome Extension install karo
      ↓
Work email enter karo
      ↓
Policy Enforced ON ✅
```
**Extension kya karta hai:** Block/Warn inputs, Monitor clipboard, DLP enforcement, Block sites

---

### 4️⃣ Policy & Zero Trust Flow
```
Extension Policy:
  → DLP Settings (SSN, Email, Phone, Credit Card)
  → Clipboard Controls (block copy/paste)
  → Enforcement Options
  → Policy Saved ✅

Network Policy:
  → URL Filtering (Allow/Block list)
  → SASE Configuration (Proxy mode)
  → Policy Saved ✅

Assign to Group:
  → Browser + Extension + Network policies assign
  → Policy Live on Device ✅
  → Test karo — DLP blocks, URL blocklist works
```
> **Note:** Google.com blocked ❌ | Bing.com allowed ✅

---

### 5️⃣ SecOps — Security Events & Incidents
```
All Events (127 total) → Security Alerts Dashboard (47 total)
      ↓
Alert triggered → Create Incident (P1-P4)
      ↓
Team member ko assign → Email notification
      ↓
Status: Investigating
      ↓
Resolve → Closed → Post-Incident Report
```

---

### 6️⃣ User & Role Management
```
Create User → Assign to Group → Activation Email (12 hr expiry)
      ↓
User sets password → User Active ✅

Create Role → Assign Scopes (38 total) → Assign to User/Group
```

---

### 7️⃣ Second Tenant Onboarding (Motivity Labs)
```
Master MSSP → Creates Motivity Labs (AZURE SSO)
      ↓
Company details → Contact → Trial Plan (14 days, 14 features)
      ↓
Review & Create → Welcome Email sent ✅
```

---

*Document based on: Secufusion_API_Key_Enterprise_Document.docx & Secufusion_NewFlow_1.docx*
*Platform Version: 1.0 — Prepared by Software Admin (Master MSSP) — March 21, 2026*"# secfusion" 
"# secfusion" 
"# secfusion" 
