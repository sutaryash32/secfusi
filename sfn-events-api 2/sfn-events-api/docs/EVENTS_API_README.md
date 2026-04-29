# Events API Documentation

## Overview

The Events API allows browser extensions to register devices and track user activity, security threats, policy violations, and file operations.

---

## Base URL

```
https://api.secufusion.com
```

---

## Authentication

All endpoints require JWT token authentication:

```
Authorization: Bearer <JWT_TOKEN>
```

---

# 1. Device Registration API

## Endpoint

```
POST /api/devices/register
```

## Request

```json
{
  "deviceName": "John's Work Laptop",
  "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
  "extensionVersion": "1.2.0",
  "ipAddress": "192.168.1.100",
  "osInfo": "Windows 11 Pro",
  "deviceFingerprint": "fp_abc123xyz789"
}
```

### Request Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `deviceName` | String | No | Human-readable name |
| `userAgent` | String | **Yes** | Browser user agent string |
| `extensionVersion` | String | No | Extension version |
| `ipAddress` | String | No | Device IP address |
| `osInfo` | String | No | OS information |
| `deviceFingerprint` | String | No | Unique fingerprint for upsert |

## Response (200 OK)

```json
{
  "data": {
    "deviceId": "bdr_1760096758998_yypjdj7by",
    "deviceName": "John's Work Laptop",
    "tenantId": "tenant-123",
    "userId": "user-456",
    "userName": "john.doe@company.com",
    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)...",
    "deviceType": "Desktop",
    "browserType": "Chrome",
    "extensionVersion": "1.2.0",
    "ipAddress": "192.168.1.100",
    "location": null,
    "status": "ACTIVE",
    "firstSeenAt": "2026-01-25T10:30:00",
    "lastSeenAt": "2026-01-25T10:30:00",
    "osInfo": "Windows 11 Pro"
  },
  "message": "200"
}
```

### Auto-Detected Fields

| Field | Detection Logic |
|-------|-----------------|
| `deviceType` | Desktop / Mobile / Tablet / Unknown |
| `browserType` | Chrome / Firefox / Edge / Safari / Opera / Unknown |

### Device Status Values

| Status | Description |
|--------|-------------|
| `ACTIVE` | Device is active |
| `INACTIVE` | No activity for 24+ hours |
| `BLOCKED` | Blocked by admin |

---

# 2. Events API

## Endpoint

```
POST /api/events
```

## Complete EventDto Schema

```json
{
  "url": "string",
  "timeStamp": "string",
  "browserType": "string",
  "deviceType": "string",
  "ipAddress": "string",
  "eventType": "string",
  "deviceId": "string",
  "title": "string",
  "durationSeconds": 0,
  "category": "string",
  "details": "string",
  "isPolicyViolation": false,
  "policyRuleId": "string",
  "policyName": "string",
  "policyType": "string",
  "filterType": "string",
  "patternType": "string",
  "matchedPattern": "string",
  "fileOperationType": "string",
  "fileName": "string",
  "fileSize": 0,
  "fileType": "string",
  "isBlocked": false,
  "isSecurityEvent": false,
  "severity": "string",
  "threatType": "string",
  "threatLevel": "string",
  "actionTaken": "string",
  "riskLevel": "string",
  "complianceImpact": "string",
  "processingStatus": "string"
}
```

---

## Event Types

| eventType | Description |
|-----------|-------------|
| `WEBSITE_VISIT` | URL navigation/page load |
| `FILE_OPERATION` | File download, upload, print, clipboard |
| `POLICY_VIOLATION` | URL blocked by policy |
| `SECURITY_THREAT` | CSP, malware, phishing, XSS |
| `USER_BEHAVIOR` | Idle, screenshot, etc. |
| `TRACKING_ACTIVITY` | Third-party trackers |
| `SYSTEM_CONFIGURATION` | Policy sync, settings |

## File Operation Types

| fileOperationType | Description |
|-------------------|-------------|
| `DOWNLOAD` | File download |
| `UPLOAD` | File upload |
| `PRINT` | Print operation |
| `CLIPBOARD_COPY` | Copy to clipboard |
| `CLIPBOARD_PASTE` | Paste from clipboard |

---

# 3. Event Examples

## 3.1 WEBSITE_VISIT - Normal Page Visit

```json
{
  "events": [
    {
      "url": "https://www.google.com/search?q=test",
      "timeStamp": "2026-01-25T10:30:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "WEBSITE_VISIT",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "Google Search - test",
      "durationSeconds": 45,
      "category": "search_engine",
      "details": "{\"referrer\": \"https://google.com\", \"tab_id\": 123456}",
      "isPolicyViolation": false,
      "isBlocked": false,
      "isSecurityEvent": false,
      "processingStatus": "Pending"
    }
  ]
}
```

---

## 3.2 FILE_OPERATION - Download (Allowed)

```json
{
  "events": [
    {
      "url": "https://drive.google.com/file/d/abc123",
      "timeStamp": "2026-01-25T11:00:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "FILE_OPERATION",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "File Download - quarterly_report.pdf",
      "category": "cloud_storage",
      "details": "{\"source\": \"Google Drive\", \"download_id\": 789}",
      "fileOperationType": "DOWNLOAD",
      "fileName": "quarterly_report.pdf",
      "fileSize": 2048576,
      "fileType": "application/pdf",
      "isBlocked": false,
      "isPolicyViolation": false,
      "isSecurityEvent": false,
      "processingStatus": "Pending"
    }
  ]
}
```

---

## 3.3 FILE_OPERATION - Download (Blocked by DLP)

```json
{
  "events": [
    {
      "url": "https://external-site.com/files/data.exe",
      "timeStamp": "2026-01-25T11:15:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "FILE_OPERATION",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "Blocked Download - Executable File",
      "category": "unknown",
      "details": "{\"reason\": \"Executable files are blocked\"}",
      "fileOperationType": "DOWNLOAD",
      "fileName": "data.exe",
      "fileSize": 5242880,
      "fileType": "application/x-msdownload",
      "isBlocked": true,
      "isSecurityEvent": true,
      "severity": "high",
      "threatType": "dlp_violation",
      "threatLevel": "high",
      "actionTaken": "blocked",
      "riskLevel": "High",
      "isPolicyViolation": true,
      "policyRuleId": "dlp-download-001",
      "policyName": "DLP - Block Executables",
      "policyType": "DLP",
      "complianceImpact": "Medium",
      "processingStatus": "Pending"
    }
  ]
}
```

---

## 3.4 FILE_OPERATION - Upload (Blocked - PII Detected)

```json
{
  "events": [
    {
      "url": "https://dropbox.com/upload",
      "timeStamp": "2026-01-25T11:30:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "FILE_OPERATION",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "Blocked Upload - PII Data Detected",
      "category": "cloud_storage",
      "details": "{\"reason\": \"PII data detected\", \"patterns_matched\": [\"SSN\", \"Credit Card\", \"Email\"]}",
      "fileOperationType": "UPLOAD",
      "fileName": "employee_data.xlsx",
      "fileSize": 512000,
      "fileType": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "isBlocked": true,
      "isSecurityEvent": true,
      "severity": "critical",
      "threatType": "dlp_pii",
      "threatLevel": "critical",
      "actionTaken": "blocked",
      "riskLevel": "Critical",
      "isPolicyViolation": true,
      "policyRuleId": "dlp-pii-001",
      "policyName": "DLP - PII Protection Policy",
      "policyType": "DLP",
      "complianceImpact": "High",
      "processingStatus": "Pending"
    }
  ]
}
```

---

## 3.5 FILE_OPERATION - Print (Blocked)

```json
{
  "events": [
    {
      "url": "https://confidential.company.com/reports/financial",
      "timeStamp": "2026-01-25T11:45:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "FILE_OPERATION",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "Blocked Print - Confidential Document",
      "category": "internal",
      "details": "{\"document_type\": \"financial_report\", \"page_count\": 15}",
      "fileOperationType": "PRINT",
      "fileName": "Q4_Financial_Report.html",
      "isBlocked": true,
      "isSecurityEvent": true,
      "severity": "high",
      "threatType": "dlp_print",
      "actionTaken": "blocked",
      "riskLevel": "High",
      "isPolicyViolation": true,
      "policyRuleId": "dlp-print-001",
      "policyName": "DLP - Block Printing",
      "policyType": "DLP",
      "complianceImpact": "Medium",
      "processingStatus": "Pending"
    }
  ]
}
```

---

## 3.6 FILE_OPERATION - Clipboard Copy (Blocked)

```json
{
  "events": [
    {
      "url": "https://crm.company.com/customers/details",
      "timeStamp": "2026-01-25T12:00:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "FILE_OPERATION",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "Blocked Clipboard Copy - PII Detected",
      "category": "crm",
      "details": "{\"content_type\": \"text\", \"content_length\": 256, \"pii_types\": [\"email\", \"phone\", \"address\"]}",
      "fileOperationType": "CLIPBOARD_COPY",
      "isBlocked": true,
      "isSecurityEvent": true,
      "severity": "medium",
      "threatType": "dlp_clipboard",
      "threatLevel": "medium",
      "actionTaken": "blocked",
      "riskLevel": "High",
      "isPolicyViolation": true,
      "policyRuleId": "dlp-clipboard-001",
      "policyName": "DLP - Clipboard Protection",
      "policyType": "DLP",
      "complianceImpact": "Medium",
      "processingStatus": "Pending"
    }
  ]
}
```

---

## 3.7 FILE_OPERATION - Clipboard Paste (Allowed)

```json
{
  "events": [
    {
      "url": "https://docs.google.com/document/d/abc123",
      "timeStamp": "2026-01-25T12:15:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "FILE_OPERATION",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "Clipboard Paste",
      "category": "productivity",
      "details": "{\"content_type\": \"text\", \"content_length\": 128}",
      "fileOperationType": "CLIPBOARD_PASTE",
      "isBlocked": false,
      "isPolicyViolation": false,
      "isSecurityEvent": false,
      "processingStatus": "Pending"
    }
  ]
}
```

---

## 3.8 POLICY_VIOLATION - URL Blacklist Block

```json
{
  "events": [
    {
      "url": "https://facebook.com",
      "timeStamp": "2026-01-25T12:30:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "POLICY_VIOLATION",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "Blocked Site - Social Media",
      "category": "social_media",
      "details": "{\"block_reason\": \"Category blocked by policy\", \"redirect_url\": \"https://company.com/blocked\"}",
      "isBlocked": true,
      "actionTaken": "blocked",
      "riskLevel": "Medium",
      "isPolicyViolation": true,
      "policyRuleId": "url-filter-001",
      "policyName": "Block Social Media",
      "policyType": "NETWORK_POLICY",
      "filterType": "BLACKLIST",
      "patternType": "DOMAIN",
      "matchedPattern": "facebook.com",
      "isSecurityEvent": false,
      "complianceImpact": "Low",
      "processingStatus": "Pending"
    }
  ]
}
```

---

## 3.9 POLICY_VIOLATION - URL Whitelist Block

```json
{
  "events": [
    {
      "url": "https://unknown-site.com/page",
      "timeStamp": "2026-01-25T12:45:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "POLICY_VIOLATION",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "Blocked Site - Not in Whitelist",
      "category": "unknown",
      "details": "{\"block_reason\": \"Site not in approved whitelist\"}",
      "isBlocked": true,
      "actionTaken": "blocked",
      "riskLevel": "Medium",
      "isPolicyViolation": true,
      "policyRuleId": "url-filter-002",
      "policyName": "Whitelist Only Policy",
      "policyType": "NETWORK_POLICY",
      "filterType": "WHITELIST",
      "patternType": "DOMAIN",
      "matchedPattern": "*.company.com",
      "isSecurityEvent": false,
      "complianceImpact": "Low",
      "processingStatus": "Pending"
    }
  ]
}
```

---

## 3.10 SECURITY_THREAT - CSP Violation

```json
{
  "events": [
    {
      "url": "https://www.newindianexpress.com/",
      "timeStamp": "2026-01-25T13:00:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "SECURITY_THREAT",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "CSP Violation Detected",
      "category": "news",
      "details": "{\"violated_directive\": \"media-src\", \"effective_directive\": \"media-src\", \"blocked_uri\": \"data:\", \"source_file\": \"https://www.newindianexpress.com/scripts/main.js\", \"line_number\": 245}",
      "isSecurityEvent": true,
      "severity": "high",
      "threatType": "csp_violation",
      "threatLevel": "medium",
      "actionTaken": "blocked",
      "riskLevel": "High",
      "isPolicyViolation": true,
      "policyName": "Content Security Policy",
      "policyType": "BROWSER_POLICY",
      "complianceImpact": "Low",
      "processingStatus": "Pending"
    }
  ]
}
```

---

## 3.11 SECURITY_THREAT - Malware Detection

```json
{
  "events": [
    {
      "url": "https://malicious-download.com/file.exe",
      "timeStamp": "2026-01-25T13:15:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "SECURITY_THREAT",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "Malware Detected - Download Blocked",
      "category": "malicious",
      "details": "{\"detection_source\": \"safe_browsing\", \"threat_name\": \"Trojan.Generic\", \"confidence\": 0.98, \"hash\": \"abc123def456\"}",
      "fileOperationType": "DOWNLOAD",
      "fileName": "setup.exe",
      "fileSize": 4194304,
      "fileType": "application/x-msdownload",
      "isBlocked": true,
      "isSecurityEvent": true,
      "severity": "critical",
      "threatType": "malware",
      "threatLevel": "critical",
      "actionTaken": "blocked",
      "riskLevel": "Critical",
      "isPolicyViolation": true,
      "policyName": "Malware Protection",
      "policyType": "BROWSER_POLICY",
      "complianceImpact": "High",
      "processingStatus": "Pending"
    }
  ]
}
```

---

## 3.12 SECURITY_THREAT - Phishing Detection

```json
{
  "events": [
    {
      "url": "https://login-microsoft-secure.fake-site.com/signin",
      "timeStamp": "2026-01-25T13:30:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "SECURITY_THREAT",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "Phishing Site Blocked",
      "category": "phishing",
      "details": "{\"detection_source\": \"safe_browsing\", \"impersonated_brand\": \"Microsoft\", \"confidence\": 0.95}",
      "isBlocked": true,
      "isSecurityEvent": true,
      "severity": "critical",
      "threatType": "phishing",
      "threatLevel": "critical",
      "actionTaken": "blocked",
      "riskLevel": "Critical",
      "isPolicyViolation": true,
      "policyName": "Phishing Protection",
      "policyType": "BROWSER_POLICY",
      "complianceImpact": "High",
      "processingStatus": "Pending"
    }
  ]
}
```

---

## 3.13 SECURITY_THREAT - XSS Detection

```json
{
  "events": [
    {
      "url": "https://vulnerable-site.com/search?q=<script>alert('xss')</script>",
      "timeStamp": "2026-01-25T13:45:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "SECURITY_THREAT",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "XSS Attack Detected",
      "category": "unknown",
      "details": "{\"attack_type\": \"reflected_xss\", \"payload\": \"<script>alert('xss')</script>\", \"parameter\": \"q\"}",
      "isBlocked": true,
      "isSecurityEvent": true,
      "severity": "high",
      "threatType": "xss",
      "threatLevel": "high",
      "actionTaken": "blocked",
      "riskLevel": "High",
      "isPolicyViolation": true,
      "policyName": "XSS Protection",
      "policyType": "BROWSER_POLICY",
      "complianceImpact": "Medium",
      "processingStatus": "Pending"
    }
  ]
}
```

---

## 3.14 USER_BEHAVIOR - Idle Detection

```json
{
  "events": [
    {
      "url": "https://app.company.com/dashboard",
      "timeStamp": "2026-01-25T14:00:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "USER_BEHAVIOR",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "User Idle Detected",
      "category": "internal",
      "durationSeconds": 600,
      "details": "{\"idle_state\": \"idle\", \"last_activity\": \"2026-01-25T13:50:00\", \"idle_duration_seconds\": 600}",
      "isPolicyViolation": false,
      "isSecurityEvent": false,
      "processingStatus": "Pending"
    }
  ]
}
```

---

## 3.15 USER_BEHAVIOR - Screenshot Attempt (Blocked)

```json
{
  "events": [
    {
      "url": "https://confidential.company.com/reports",
      "timeStamp": "2026-01-25T14:15:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "USER_BEHAVIOR",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "Screenshot Attempt Blocked",
      "category": "internal",
      "details": "{\"action\": \"screenshot\", \"method\": \"keyboard_shortcut\", \"key_combination\": \"PrintScreen\"}",
      "isBlocked": true,
      "actionTaken": "blocked",
      "isPolicyViolation": true,
      "policyRuleId": "dlp-screenshot-001",
      "policyName": "DLP - Block Screenshots",
      "policyType": "DLP",
      "isSecurityEvent": true,
      "severity": "medium",
      "threatType": "dlp_screenshot",
      "riskLevel": "Medium",
      "complianceImpact": "Medium",
      "processingStatus": "Pending"
    }
  ]
}
```

---

## 3.16 TRACKING_ACTIVITY - Third-Party Trackers

```json
{
  "events": [
    {
      "url": "https://news-site.com/article/12345",
      "timeStamp": "2026-01-25T14:30:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "TRACKING_ACTIVITY",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "Third-Party Trackers Detected",
      "category": "news",
      "details": "{\"trackers_found\": 12, \"tracker_domains\": [\"doubleclick.net\", \"facebook.com\", \"google-analytics.com\"], \"blocked_count\": 8}",
      "isBlocked": false,
      "actionTaken": "partial_block",
      "isPolicyViolation": false,
      "isSecurityEvent": false,
      "processingStatus": "Pending"
    }
  ]
}
```

---

## 3.17 SYSTEM_CONFIGURATION - Policy Sync

```json
{
  "events": [
    {
      "url": "https://api.secufusion.com/api/tenants/policy",
      "timeStamp": "2026-01-25T14:45:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "SYSTEM_CONFIGURATION",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "Policy Sync Completed",
      "category": "system",
      "details": "{\"action\": \"policy_sync\", \"policy_version\": \"1.5\", \"policy_id\": \"policy-001\", \"sync_status\": \"success\"}",
      "isPolicyViolation": false,
      "isSecurityEvent": false,
      "processingStatus": "Processed"
    }
  ]
}
```

---

## 3.18 SYSTEM_CONFIGURATION - Extension Settings Changed

```json
{
  "events": [
    {
      "url": "chrome-extension://abc123/options.html",
      "timeStamp": "2026-01-25T15:00:00",
      "browserType": "Chrome",
      "deviceType": "Desktop",
      "ipAddress": "192.168.1.100",
      "eventType": "SYSTEM_CONFIGURATION",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "Extension Settings Updated",
      "category": "system",
      "details": "{\"action\": \"settings_change\", \"setting_name\": \"notification_enabled\", \"old_value\": true, \"new_value\": false}",
      "isPolicyViolation": false,
      "isSecurityEvent": false,
      "processingStatus": "Processed"
    }
  ]
}
```

---

# 4. Batch Events Example

```json
{
  "events": [
    {
      "url": "https://gmail.com",
      "timeStamp": "2026-01-25T10:00:00",
      "eventType": "WEBSITE_VISIT",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "Gmail - Inbox",
      "durationSeconds": 300,
      "category": "email",
      "processingStatus": "Pending"
    },
    {
      "url": "https://gmail.com/attachment",
      "timeStamp": "2026-01-25T10:05:00",
      "eventType": "FILE_OPERATION",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "fileOperationType": "DOWNLOAD",
      "fileName": "invoice.pdf",
      "fileSize": 102400,
      "fileType": "application/pdf",
      "isBlocked": false,
      "processingStatus": "Pending"
    },
    {
      "url": "https://facebook.com",
      "timeStamp": "2026-01-25T10:10:00",
      "eventType": "POLICY_VIOLATION",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "Blocked - Social Media",
      "isBlocked": true,
      "isPolicyViolation": true,
      "policyType": "NETWORK_POLICY",
      "filterType": "BLACKLIST",
      "matchedPattern": "facebook.com",
      "processingStatus": "Pending"
    },
    {
      "url": "https://malicious.com",
      "timeStamp": "2026-01-25T10:15:00",
      "eventType": "SECURITY_THREAT",
      "deviceId": "bdr_1760096758998_yypjdj7by",
      "title": "Phishing Site Blocked",
      "isSecurityEvent": true,
      "severity": "critical",
      "threatType": "phishing",
      "actionTaken": "blocked",
      "riskLevel": "Critical",
      "processingStatus": "Pending"
    }
  ]
}
```

---

# 5. Response

## Success Response (200 OK)

```
Empty body (HTTP 200)
```

## Error Responses

| Status | Description |
|--------|-------------|
| 400 | Bad Request - Invalid data |
| 401 | Unauthorized - Invalid token |
| 500 | Internal Server Error |

---

# 6. Field Reference

## All EventDto Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `url` | String | Yes | URL of the event |
| `timeStamp` | String | Yes | ISO format: 2026-01-25T10:30:00 |
| `browserType` | String | No | Chrome, Firefox, Edge, Safari |
| `deviceType` | String | No | Desktop, Mobile, Tablet |
| `ipAddress` | String | No | IP address |
| `eventType` | String | Yes | Event type enum |
| `deviceId` | String | Yes | Device ID from registration |
| `title` | String | No | Page title or event description |
| `durationSeconds` | Long | No | Time spent (WEBSITE_VISIT) |
| `category` | String | No | URL category |
| `details` | String | No | JSON string for additional data |
| `isPolicyViolation` | Boolean | No | True if policy violated |
| `policyRuleId` | String | No | Policy rule ID |
| `policyName` | String | No | Policy name |
| `policyType` | String | No | BROWSER_POLICY, NETWORK_POLICY, DLP |
| `filterType` | String | No | WHITELIST, BLACKLIST |
| `patternType` | String | No | DOMAIN, REGEX, URL |
| `matchedPattern` | String | No | Pattern that matched |
| `fileOperationType` | String | No | DOWNLOAD, UPLOAD, PRINT, CLIPBOARD_* |
| `fileName` | String | No | File name |
| `fileSize` | Long | No | File size in bytes |
| `fileType` | String | No | MIME type |
| `isBlocked` | Boolean | No | True if blocked |
| `isSecurityEvent` | Boolean | No | True if security event |
| `severity` | String | No | critical, high, medium, low |
| `threatType` | String | No | csp_violation, malware, phishing, xss |
| `threatLevel` | String | No | Threat level |
| `actionTaken` | String | No | blocked, allowed, warned |
| `riskLevel` | String | No | Critical, High, Medium, Low |
| `complianceImpact` | String | No | High, Medium, Low |
| `processingStatus` | String | No | Pending, Processed, Reviewed |

---

# 7. Quick Reference Tables

## Event Type Summary

| eventType | fileOperationType | Use Case |
|-----------|-------------------|----------|
| `WEBSITE_VISIT` | - | Normal page visits |
| `FILE_OPERATION` | `DOWNLOAD` | File downloads |
| `FILE_OPERATION` | `UPLOAD` | File uploads |
| `FILE_OPERATION` | `PRINT` | Print operations |
| `FILE_OPERATION` | `CLIPBOARD_COPY` | Copy to clipboard |
| `FILE_OPERATION` | `CLIPBOARD_PASTE` | Paste from clipboard |
| `POLICY_VIOLATION` | - | URL blocked by policy |
| `SECURITY_THREAT` | - | CSP, malware, phishing, XSS |
| `USER_BEHAVIOR` | - | Idle, screenshot, etc. |
| `TRACKING_ACTIVITY` | - | Third-party trackers |
| `SYSTEM_CONFIGURATION` | - | Policy sync, settings |

## Severity Levels

| Severity | Description |
|----------|-------------|
| `critical` | Immediate action required |
| `high` | High priority |
| `medium` | Medium priority |
| `low` | Low priority |

## Risk Levels

| Risk Level | Description |
|------------|-------------|
| `Critical` | Critical risk |
| `High` | High risk |
| `Medium` | Medium risk |
| `Low` | Low risk |

## Policy Types

| Policy Type | Description |
|-------------|-------------|
| `BROWSER_POLICY` | Browser-level policies |
| `NETWORK_POLICY` | URL filtering policies |
| `DLP` | Data Loss Prevention policies |

---

# 8. Database Schema

## Events Table Columns

| Column | Type | Description |
|--------|------|-------------|
| `pk_event_id` | UUID | Primary key |
| `url` | TEXT | Event URL |
| `time_stamp` | TIMESTAMPTZ | Event timestamp |
| `fk_tenant_id` | VARCHAR | Tenant ID (FK) |
| `fk_device_id` | VARCHAR | Device ID (FK) |
| `user_name` | VARCHAR | Username |
| `event_type` | VARCHAR(50) | Event type enum |
| `browser_type` | TEXT | Browser type |
| `device_type` | TEXT | Device type |
| `ip_address` | VARCHAR(45) | IP address |
| `title` | VARCHAR(500) | Page title |
| `domain` | VARCHAR(255) | Extracted domain |
| `duration_seconds` | BIGINT | Duration |
| `details` | JSONB | Additional data |
| `category` | VARCHAR(100) | URL category |
| `is_policy_violation` | BOOLEAN | Policy violation flag |
| `policy_rule_id` | VARCHAR(50) | Policy rule ID |
| `file_operation_type` | VARCHAR(20) | File operation enum |
| `file_name` | VARCHAR(500) | File name |
| `file_size` | BIGINT | File size |
| `file_type` | VARCHAR(100) | MIME type |
| `is_blocked` | BOOLEAN | Blocked flag |
| `is_security_event` | BOOLEAN | Security event flag |
| `severity` | VARCHAR(20) | Severity level |
| `threat_type` | VARCHAR(50) | Threat type |
| `threat_level` | VARCHAR(20) | Threat level |
| `action_taken` | VARCHAR(50) | Action taken |
| `risk_level` | VARCHAR(20) | Risk level |
| `policy_name` | VARCHAR(200) | Policy name |
| `policy_type` | VARCHAR(50) | Policy type |
| `filter_type` | VARCHAR(20) | Filter type |
| `pattern_type` | VARCHAR(20) | Pattern type |
| `matched_pattern` | VARCHAR(500) | Matched pattern |
| `compliance_impact` | VARCHAR(20) | Compliance impact |
| `processing_status` | VARCHAR(20) | Processing status |

---

# 9. Version History

| Version | Date | Changes |
|---------|------|---------|
| V1 | 2026-01-20 | Initial devices table |
| V2 | 2026-01-21 | Enhanced events table |
| V3 | 2026-01-22 | Aggregation views |
| V4 | 2026-01-23 | Data backfill |
| V5 | 2026-01-24 | Composite indexes |
| V6 | 2026-01-25 | File operation columns |
| V7 | 2026-01-25 | Security & policy columns |
