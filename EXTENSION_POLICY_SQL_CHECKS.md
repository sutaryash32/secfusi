# Extension Policy SQL Checks

This file explains how to inspect the `extension_policy` persistence flow directly in PostgreSQL.

It covers:

- how to find the relevant tables
- how to inspect their columns
- how to verify a specific `pkExtensionPolicyId`
- how to compare old and new versioned policy rows
- how to confirm whether `managedExtension` values were actually persisted

## 1. Why These Queries Matter

The Extension Policy API stores data across related tables.

Main relationship:

- `extension_policy`
  - parent policy row
- `managed_extensions`
  - nested `managedExtension` object
- `extension_detail`
  - child rows inside `managedExtension.extensions`

The join path is:

- `extension_policy.fk_managed_extensions_id`
- points to `managed_extensions.pk_managed_extension_id`

If you want to verify whether the API request body was really saved, these are the tables to inspect.

## 2. Where `information_schema.tables` Comes From

`information_schema.tables` is not one of your application tables.

It is a built-in PostgreSQL system view used to discover metadata such as:

- which tables exist
- which schema they belong to
- whether they are base tables or views

You can query it directly from the same database connection.

## 3. Find the Exact Tables

Use this to confirm the related tables exist in `public`:

```sql
SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('extension_policy', 'managed_extensions', 'extension_detail')
ORDER BY table_name;
```

What this does:

- checks PostgreSQL metadata
- filters only the `public` schema
- returns the exact table names relevant to extension policy persistence

## 4. Inspect the Columns

Use this to inspect the actual columns in those tables:

```sql
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('extension_policy', 'managed_extensions', 'extension_detail')
ORDER BY table_name, ordinal_position;
```

What this does:

- shows column names in table order
- helps confirm fields like:
  - `pk_extension_policy_id`
  - `fk_managed_extensions_id`
  - `action`
  - `enforcement_action`
  - `warning_message`

## 5. Check One Policy Row by Policy ID

Use this query for one exact policy:

```sql
SELECT ep.pk_extension_policy_id,
       ep.name,
       ep.description,
       ep.policy_key,
       ep.version,
       ep.is_active,
       ep.fk_tenant_id,
       ep.fk_managed_extensions_id,
       me.pk_managed_extension_id,
       me.action,
       me.enforcement_action,
       me.warning_message
FROM public.extension_policy ep
LEFT JOIN public.managed_extensions me
  ON ep.fk_managed_extensions_id = me.pk_managed_extension_id
WHERE ep.pk_extension_policy_id = '844dd67d-e3cf-43a8-ac65-3ea440422d9b';
```

What this does:

- finds the parent `extension_policy` row
- joins the linked `managed_extensions` row
- shows the stored managed-extension values for that policy

Use this version for the newer policy row:

```sql
SELECT ep.pk_extension_policy_id,
       ep.name,
       ep.description,
       ep.policy_key,
       ep.version,
       ep.is_active,
       ep.fk_tenant_id,
       ep.fk_managed_extensions_id,
       me.pk_managed_extension_id,
       me.action,
       me.enforcement_action,
       me.warning_message
FROM public.extension_policy ep
LEFT JOIN public.managed_extensions me
  ON ep.fk_managed_extensions_id = me.pk_managed_extension_id
WHERE ep.pk_extension_policy_id = 'cf22619a-0b58-4e95-9d6b-020d0a4eb665';
```

## 6. Compare Old and New Version Rows Together

Extension Policy updates are versioned.

That means:

- old row is not updated in place
- old row becomes inactive
- new row is inserted
- `policy_key` stays the same across versions
- `pk_extension_policy_id` changes

Use this query to compare both versions together:

```sql
SELECT ep.pk_extension_policy_id,
       ep.policy_key,
       ep.version,
       ep.is_active,
       ep.fk_managed_extensions_id,
       me.action,
       me.enforcement_action,
       me.warning_message
FROM public.extension_policy ep
LEFT JOIN public.managed_extensions me
  ON ep.fk_managed_extensions_id = me.pk_managed_extension_id
WHERE ep.pk_extension_policy_id IN (
  '844dd67d-e3cf-43a8-ac65-3ea440422d9b',
  'cf22619a-0b58-4e95-9d6b-020d0a4eb665'
)
ORDER BY ep.version;
```

What this does:

- returns both the old and new policy versions
- lets you confirm:
  - old row is inactive
  - new row is active
  - both rows share the expected persistence behavior

## 7. Check the Nested Extension List

If you want to inspect `managedExtension.extensions`, use this:

```sql
SELECT ep.pk_extension_policy_id,
       me.pk_managed_extension_id,
       ed.extension_id,
       ed.extension_name,
       ed.publisher
FROM public.extension_policy ep
LEFT JOIN public.managed_extensions me
  ON ep.fk_managed_extensions_id = me.pk_managed_extension_id
LEFT JOIN public.extension_detail ed
  ON ed.fk_managed_extension_id = me.pk_managed_extension_id
WHERE ep.pk_extension_policy_id = '844dd67d-e3cf-43a8-ac65-3ea440422d9b';
```

What this does:

- finds the policy
- finds its `managed_extensions` row
- finds child extension rows under that managed extension

If `extensions` was sent as an empty array, this query may return:

- the policy and managed extension values
- no `extension_detail` child rows

## 8. What Values You Expect to See

If the payload was persisted correctly, you should see values like:

```json
{
  "managedExtension": {
    "extensionPolicyType": "BLOCK_ALL",
    "enforcementAction": "BLOCK_BROWSER",
    "warningMessage": "All browser extensions are blocked by policy."
  }
}
```

Stored DB mapping:

- API `managedExtension.extensionPolicyType` -> DB `managed_extensions.action`
- API `managedExtension.enforcementAction` -> DB `managed_extensions.enforcement_action`
- API `managedExtension.warningMessage` -> DB `managed_extensions.warning_message`

So the database row should show:

- `action = BLOCK_ALL`
- `enforcement_action = BLOCK_BROWSER`
- `warning_message = All browser extensions are blocked by policy.`

## 9. How to Run the Check Flow

Use this order when validating persistence.

### Step 1: Create or update through the API

Run your `POST` or `PUT` request first.

Example expectation:

- `POST` creates a policy row
- `PUT` creates a new version row

### Step 2: Capture the returned `pkExtensionPolicyId`

For `POST`:

- use the returned `pkExtensionPolicyId`

For `PUT`:

- very important: use the new `pkExtensionPolicyId` from the response
- do not keep validating only against the old ID

### Step 3: Run the SQL check

Use the single-policy query:

```sql
SELECT ep.pk_extension_policy_id,
       ep.name,
       ep.description,
       ep.policy_key,
       ep.version,
       ep.is_active,
       ep.fk_tenant_id,
       ep.fk_managed_extensions_id,
       me.pk_managed_extension_id,
       me.action,
       me.enforcement_action,
       me.warning_message
FROM public.extension_policy ep
LEFT JOIN public.managed_extensions me
  ON ep.fk_managed_extensions_id = me.pk_managed_extension_id
WHERE ep.pk_extension_policy_id = '<PUT_OR_POST_POLICY_ID>';
```

### Step 4: Verify the stored values

Check whether:

- `fk_managed_extensions_id` is populated
- joined `managed_extensions` row exists
- `action` matches the payload
- `enforcement_action` matches the payload
- `warning_message` matches the payload

### Step 5: For `PUT`, compare old vs new versions

Run the compare query to confirm:

- old row became inactive
- new row became active
- new row contains the latest managed-extension values

## 10. How to Interpret the Results

### Correct persistence

If the fix is working, you should see:

- a linked `managed_extensions` row
- expected non-default values from the request
- correct version behavior on `PUT`

### Old broken behavior

If the old bug is present, you may see:

- policy row created successfully
- `managed_extensions` row linked, but values show defaults
- or values do not match what was sent in the payload

Typical broken values were:

- `action = ALLOW_ALL`
- `enforcement_action = WARN_USER`
- default warning message

## 11. Quick Copy-Paste Queries

### Find exact tables

```sql
SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('extension_policy', 'managed_extensions', 'extension_detail')
ORDER BY table_name;
```

### Find exact columns

```sql
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('extension_policy', 'managed_extensions', 'extension_detail')
ORDER BY table_name, ordinal_position;
```

### Check policy `844dd67d-e3cf-43a8-ac65-3ea440422d9b`

```sql
SELECT ep.pk_extension_policy_id,
       ep.name,
       ep.description,
       ep.policy_key,
       ep.version,
       ep.is_active,
       ep.fk_tenant_id,
       ep.fk_managed_extensions_id,
       me.pk_managed_extension_id,
       me.action,
       me.enforcement_action,
       me.warning_message
FROM public.extension_policy ep
LEFT JOIN public.managed_extensions me
  ON ep.fk_managed_extensions_id = me.pk_managed_extension_id
WHERE ep.pk_extension_policy_id = '844dd67d-e3cf-43a8-ac65-3ea440422d9b';
```

### Check policy `cf22619a-0b58-4e95-9d6b-020d0a4eb665`

```sql
SELECT ep.pk_extension_policy_id,
       ep.name,
       ep.description,
       ep.policy_key,
       ep.version,
       ep.is_active,
       ep.fk_tenant_id,
       ep.fk_managed_extensions_id,
       me.pk_managed_extension_id,
       me.action,
       me.enforcement_action,
       me.warning_message
FROM public.extension_policy ep
LEFT JOIN public.managed_extensions me
  ON ep.fk_managed_extensions_id = me.pk_managed_extension_id
WHERE ep.pk_extension_policy_id = 'cf22619a-0b58-4e95-9d6b-020d0a4eb665';
```

### Compare both policy IDs

```sql
SELECT ep.pk_extension_policy_id,
       ep.policy_key,
       ep.version,
       ep.is_active,
       ep.fk_managed_extensions_id,
       me.action,
       me.enforcement_action,
       me.warning_message
FROM public.extension_policy ep
LEFT JOIN public.managed_extensions me
  ON ep.fk_managed_extensions_id = me.pk_managed_extension_id
WHERE ep.pk_extension_policy_id IN (
  '844dd67d-e3cf-43a8-ac65-3ea440422d9b',
  'cf22619a-0b58-4e95-9d6b-020d0a4eb665'
)
ORDER BY ep.version;
```
