-- Fix existing bogus device names generated from Sec-CH-UA Client Hints
-- (e.g., "Not:A-Brand-Windows-1536x864", "Not A;Brand-...", "Chromium-...")
-- Replaces them with friendly names built from browser_type + os_info + user_name.

UPDATE devices
SET device_name = CONCAT(
    COALESCE(
        CASE
            WHEN browser_type IS NOT NULL AND browser_type <> 'Unknown'
            THEN CONCAT(
                browser_type,
                CASE
                    WHEN os_info ILIKE '%windows%' THEN ' on Windows'
                    WHEN os_info ILIKE '%mac%' OR os_info ILIKE '%darwin%' THEN ' on macOS'
                    WHEN os_info ILIKE '%linux%' AND os_info NOT ILIKE '%android%' THEN ' on Linux'
                    WHEN os_info ILIKE '%android%' THEN ' on Android'
                    WHEN os_info ILIKE '%ios%' OR os_info ILIKE '%iphone%' OR os_info ILIKE '%ipad%' THEN ' on iOS'
                    WHEN os_info ILIKE '%chrome%os%' THEN ' on ChromeOS'
                    ELSE ''
                END
            )
            WHEN os_info IS NOT NULL AND os_info <> ''
            THEN CONCAT(
                CASE
                    WHEN os_info ILIKE '%windows%' THEN 'Windows'
                    WHEN os_info ILIKE '%mac%' OR os_info ILIKE '%darwin%' THEN 'macOS'
                    WHEN os_info ILIKE '%linux%' AND os_info NOT ILIKE '%android%' THEN 'Linux'
                    WHEN os_info ILIKE '%android%' THEN 'Android'
                    WHEN os_info ILIKE '%ios%' OR os_info ILIKE '%iphone%' OR os_info ILIKE '%ipad%' THEN 'iOS'
                    WHEN os_info ILIKE '%chrome%os%' THEN 'ChromeOS'
                    ELSE 'Unknown'
                END,
                ' Device'
            )
            WHEN device_type IS NOT NULL AND device_type <> 'Unknown'
            THEN CONCAT(device_type, ' Device')
            ELSE 'Unknown Device'
        END
    ),
    CASE
        WHEN user_name IS NOT NULL AND user_name <> ''
        THEN CONCAT(' (', user_name, ')')
        ELSE ''
    END
)
WHERE device_name IS NOT NULL
  AND (
      LOWER(device_name) LIKE 'not:a-brand%'
      OR LOWER(device_name) LIKE 'not a;brand%'
      OR LOWER(device_name) LIKE 'not.a/brand%'
      OR LOWER(device_name) LIKE 'not?a_brand%'
      OR LOWER(device_name) LIKE 'not)a;brand%'
      OR LOWER(device_name) LIKE '(not;browser%'
      OR LOWER(device_name) LIKE 'chromium-%'
  );
