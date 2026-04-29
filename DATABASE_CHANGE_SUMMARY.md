# Database Change Summary: Secufusion Persistence & Policy Updates

This document provides a clear breakdown of the database state, distinguishing between existing structures and the recent modifications made to fix persistence and support new policy features.

---

## ❓ Why These Changes? (The Strategic Need)

The modifications documented here solve three critical enterprise security hurdles:

1.  **Strict Multi-Tenancy**: Ensures that per-tenant default policies can be managed independently (`is_tenant_default`) without impacting the global master template.
2.  **Granular Access Control**: Decouples the main application login from the browser extension login (`extension_authorized`), allowing admins to revoke extension access without locking users out of the portal.
3.  **Seamless Persistence**: Fixes a data-binding gap where nested "Browser Extension Management" settings were failing to save, ensuring that "Block/Allow" lists are strictly enforced by the DB.

---

## 🚀 Production Migration Script (PostgreSQL Transaction)

Use this script to apply all structural changes to your Production environment safely. It includes the `BEGIN/COMMIT` block to ensure that either all changes succeed or none are applied (Atomic update).

**Target Databases:** Run against `secufusion_tenants` and `secufusion_iam`.

```sql
BEGIN;

-- 1. Add 'is_tenant_default' to Policy Tables
ALTER TABLE browserpolicy ADD COLUMN IF NOT EXISTS is_tenant_default BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE networkpolicy ADD COLUMN IF NOT EXISTS is_tenant_default BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE extension_policy ADD COLUMN IF NOT EXISTS is_tenant_default BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Add 'fk_landingpage_id' to Extension Policy
ALTER TABLE extension_policy ADD COLUMN IF NOT EXISTS fk_landingpage_id VARCHAR(36);

-- 3. Add 'extension_authorized' to Events Groups
ALTER TABLE events_groups ADD COLUMN IF NOT EXISTS extension_authorized BOOLEAN NOT NULL DEFAULT FALSE;

-- 4. Create Supporting Indexes
CREATE INDEX IF NOT EXISTS idx_browserpolicy_tenant_default ON browserpolicy (fk_tenant_id) WHERE is_tenant_default = TRUE;
CREATE INDEX IF NOT EXISTS idx_networkpolicy_tenant_default ON networkpolicy (fk_tenant_id) WHERE is_tenant_default = TRUE;
CREATE INDEX IF NOT EXISTS idx_extension_policy_tenant_default ON extension_policy (fk_tenant_id) WHERE is_tenant_default = TRUE;
CREATE INDEX IF NOT EXISTS idx_events_group_ext_authorized ON events_groups (tenant_id, extension_authorized, is_active);

-- 5. Backfill Extension Authorization
UPDATE events_groups SET extension_authorized = TRUE 
WHERE authorized = TRUE AND group_type = 'AZURE_GROUP' AND extension_authorized = FALSE;

COMMIT;
-- Note: If any line fails, the entire transaction will automatically roll back.
```

---

## 1. Initial State (Existing Tables)
The following tables and columns were already part of the database schema. No changes were made to their primary existence, but their data handling logic was improved.

### Core Tables
- `extension_policy`: Stores high-level browser extension policy settings.
- `managed_extensions`: Stores the nested configuration for Browser Extension Management.
- `events_groups`: Stores group definitions for authorization (Azure/API Key).
- `login_audit_event`: Unified audit table created in `V1`.

### Pre-existing Column Logic
- `managed_extensions.action`: Existed, but was previously being saved as an **Integer** (Ordinal).
- `extension_policy.isActive`: Used for versioning, but was not strictly enforced via unique constraints previously.

---

## 2. Recent Structural Changes (New Migration Updates)
We applied the following **structural changes** using SQL migrations (`V17` - `V25`) to add new capabilities and enforce rules.

### Added Columns (`ALTER TABLE ADD COLUMN`)
| Table | New Column | Migration | Purpose |
|---|---|---|---|
| `extension_policy` | `is_tenant_default` | `V17` | Identifies which policy is the default for a tenant. |
| `extension_policy` | `fk_landingpage_id` | `V20` | Links a policy to a specific landing page configuration. |
| `events_groups` | `extension_authorized`| `V21` | Independent gate for extension-side login access. |
| `browserpolicy` | `is_tenant_default` | `V17` | Shared default policy support for browser settings. |
| `networkpolicy` | `is_tenant_default` | `V17` | Shared default policy support for network settings. |

### New Constraints & Indexes (`CREATE UNIQUE INDEX`)
- **Constraint:** `uix_extension_policy_tenant_default` (`V19`)
  - **Rule:** Ensures a tenant can have exactly **one** active default extension policy.
  - **Logic:** Only applies where `is_tenant_default = TRUE` AND `is_active = TRUE`.

---

## 3. Data Integrity & Persistence Changes (Logic Level)
These changes were made at the **JPA/Code level** to change how data is written into the existing DB columns.

- **String Enums:** The `action` column in `managed_extensions` now stores strings (e.g., `'BLOCK_ALL'`, `'ALLOW_LIST'`) instead of numbers.
- **Explicit Mapping:** Added explicit database column bindings for `enforcement_action` and `warning_message` to ensure they are never ignored during a `SAVE` operation.
- **Auto-Initialization:** The service now creates a row in `managed_extensions` automatically when a default policy is created, preventing `NULL` reference errors.

---

## 4. How to Verify at DB Level (SQL Queries)

### A. Check if the New Columns Exist
Run this to see the "New" columns in your tables:
```sql
SELECT table_name, column_name, data_type 
FROM information_schema.columns 
WHERE table_name IN ('extension_policy', 'managed_extensions', 'events_groups')
  AND column_name IN ('is_tenant_default', 'fk_landingpage_id', 'extension_authorized', 'enforcement_action');
```

### B. Verify Data Persistence (The "Disappearing Data" Fix)
Run this after saving a policy to confirm the nested settings actually saved:
```sql
SELECT ep.name, ep.version, me.action, me.enforcement_action, me.warning_message
FROM extension_policy ep
JOIN managed_extensions me ON ep.fk_managed_extensions_id = me.pk_managed_extension_id
ORDER BY ep.created_at DESC LIMIT 5;
```

### C. Check Versioning (New Rows)
Verify that updates create **New Rows** instead of overwriting old ones:
```sql
SELECT name, policy_key, version, is_active, created_at 
FROM extension_policy 
WHERE name = 'Default Extension Policy' 
ORDER BY version DESC;
```

### D. Verify Unique Index Constraint
Use this to confirm the partial unique index `uix_extension_policy_tenant_default` is correctly defined and active:
```sql
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'extension_policy'
  AND indexname = 'uix_extension_policy_tenant_default';
```
What this does:
- Confirms the index is **Unique**.
- Verifies the **Filter Condition**: `WHERE (is_tenant_default = TRUE AND is_active = TRUE)`.
- Ensures that only **one active default** policy can exist per tenant.

---

## 5. Summary Table: Nothing New vs. Brand New
| Component | Status | Description |
|---|---|---|
| **Tables** | **Existing** | We used existing tables; no `CREATE TABLE` for core entities was added. |
| **Columns** | **Brand New** | We added `is_tenant_default`, `fk_landingpage_id`, etc., via `ALTER`. |
| **Indexes** | **Brand New** | We added unique constraints to enforce business rules. |
| **Rows (Data)**| **New Every Save**| The system inserts a new row for every update (Versioning). |


