# Extension Policy Change Handoff

## Overall Summary

- The Extension Policy managed-extension block was not persisting correctly because the backend used `managedExtensions`, while request payloads and Browser Policy used `managedExtension`.
- `extensionPolicyType` also needed explicit JSON mapping to the backend field `action`.
- Repository fetch queries and the versioned update cloning flow were aligned to use `managedExtension`.
- Default, global-default, and tenant-default policy creation now guarantee a complete `ManagedExtension` block.
- Default `enforcementAction` and `warningMessage` values were moved out of hardcoded service literals into config.

## Files Touched


- [sfn-tenants-api 3/sfn-tenants-api/src/main/java/com/secufusion/tenant/entity/ExtensionPolicy.java](/C:/Users/Yash/OneDrive%20-%20Motivity%20Labs/Desktop/Secufusionn/sfn-tenants-api%203/sfn-tenants-api/src/main/java/com/secufusion/tenant/entity/ExtensionPolicy.java:58)
- [sfn-tenants-api 3/sfn-tenants-api/src/main/java/com/secufusion/tenant/entity/ManagedExtension.java](/C:/Users/Yash/OneDrive%20-%20Motivity%20Labs/Desktop/Secufusionn/sfn-tenants-api%203/sfn-tenants-api/src/main/java/com/secufusion/tenant/entity/ManagedExtension.java:21)
- [sfn-tenants-api 3/sfn-tenants-api/src/main/java/com/secufusion/tenant/dto/ManagedExtensionDto.java](/C:/Users/Yash/OneDrive%20-%20Motivity%20Labs/Desktop/Secufusionn/sfn-tenants-api%203/sfn-tenants-api/src/main/java/com/secufusion/tenant/dto/ManagedExtensionDto.java:10)
- [sfn-tenants-api 3/sfn-tenants-api/src/main/java/com/secufusion/tenant/dto/ManagedExtensionResponseDto.java](/C:/Users/Yash/OneDrive%20-%20Motivity%20Labs/Desktop/Secufusionn/sfn-tenants-api%203/sfn-tenants-api/src/main/java/com/secufusion/tenant/dto/ManagedExtensionResponseDto.java:9)
- [sfn-tenants-api 3/sfn-tenants-api/src/main/java/com/secufusion/tenant/dto/ExtensionPolicyRequestDto.java](/C:/Users/Yash/OneDrive%20-%20Motivity%20Labs/Desktop/Secufusionn/sfn-tenants-api%203/sfn-tenants-api/src/main/java/com/secufusion/tenant/dto/ExtensionPolicyRequestDto.java:18)
- [sfn-tenants-api 3/sfn-tenants-api/src/main/java/com/secufusion/tenant/repository/ExtensionPolicyRepository.java](/C:/Users/Yash/OneDrive%20-%20Motivity%20Labs/Desktop/Secufusionn/sfn-tenants-api%203/sfn-tenants-api/src/main/java/com/secufusion/tenant/repository/ExtensionPolicyRepository.java:39)
- [sfn-tenants-api 3/sfn-tenants-api/src/main/java/com/secufusion/tenant/service/ExtensionPolicyService.java](/C:/Users/Yash/OneDrive%20-%20Motivity%20Labs/Desktop/Secufusionn/sfn-tenants-api%203/sfn-tenants-api/src/main/java/com/secufusion/tenant/service/ExtensionPolicyService.java:42)
- [sfn-tenants-api 3/sfn-tenants-api/src/main/java/com/secufusion/tenant/config/ExtensionPolicyDefaults.java](/C:/Users/Yash/OneDrive%20-%20Motivity%20Labs/Desktop/Secufusionn/sfn-tenants-api%203/sfn-tenants-api/src/main/java/com/secufusion/tenant/config/ExtensionPolicyDefaults.java:1)
- [sfn-tenants-api 3/sfn-tenants-api/src/main/resources/application.properties](/C:/Users/Yash/OneDrive%20-%20Motivity%20Labs/Desktop/Secufusionn/sfn-tenants-api%203/sfn-tenants-api/src/main/resources/application.properties:118)

## Code Changes

### `ExtensionPolicy.java`

```java
@OneToOne(cascade = CascadeType.ALL)
@JoinColumn(name = "fk_managed_extensions_id")
@JsonProperty("managedExtension")
@JsonAlias("managedExtensions")
private ManagedExtension managedExtension;
```

### `ManagedExtension.java`

```java
@Column(name = "action")
@Enumerated(EnumType.STRING)
@com.fasterxml.jackson.annotation.JsonProperty("extensionPolicyType")
@com.fasterxml.jackson.annotation.JsonAlias("extensionPolicyType")
private ExtensionAction action = ExtensionAction.ALLOW_ALL;

@Column(name = "enforcement_action")
private String enforcementAction = "WARN_USER";

@Column(name = "warning_message", length = 1000)
private String warningMessage = "A prohibited extension has been detected. Please uninstall it to comply with company policy.";
```

### `ManagedExtensionDto.java`

```java
@com.fasterxml.jackson.annotation.JsonProperty("extensionPolicyType")
@com.fasterxml.jackson.annotation.JsonAlias("extensionPolicyType")
private String action;
private String enforcementAction;
private String warningMessage;
```

### `ManagedExtensionResponseDto.java`

```java
@com.fasterxml.jackson.annotation.JsonProperty("extensionPolicyType")
@com.fasterxml.jackson.annotation.JsonAlias("extensionPolicyType")
private String action;
private String enforcementAction;
private String warningMessage;
```

### `ExtensionPolicyRequestDto.java`

```java
@JsonAlias("managedExtensions")
private ManagedExtensionDto managedExtension;
```

### `ExtensionPolicyRepository.java`

```java
@Query("SELECT DISTINCT e FROM ExtensionPolicy e " +
        "LEFT JOIN FETCH e.urlFilters " +
        "LEFT JOIN FETCH e.dlp " +
        "LEFT JOIN FETCH e.managedExtension " +
        "LEFT JOIN FETCH e.complianceRules " +
        "WHERE e.fkTenantId = :tenantId " +
        "AND e.isActive = true " +
        "ORDER BY e.createdAt DESC")
List<ExtensionPolicy> findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtDesc(@Param("tenantId") String tenantId);

@Query("SELECT e FROM ExtensionPolicy e " +
        "LEFT JOIN FETCH e.urlFilters " +
        "LEFT JOIN FETCH e.dlp " +
        "LEFT JOIN FETCH e.managedExtension " +
        "LEFT JOIN FETCH e.complianceRules " +
        "WHERE e.pkExtensionPolicyId = :id")
Optional<ExtensionPolicy> findByIdWithRelations(@Param("id") String id);
```

### `ExtensionPolicyService.java`

```java
import com.secufusion.tenant.config.ExtensionPolicyDefaults;
```

```java
private final ExtensionPolicyDefaults extensionPolicyDefaults;
```

```java
ManagedExtension me = new ManagedExtension();
me.setAction(ExtensionAction.ALLOW_ALL);
me.setEnforcementAction(extensionPolicyDefaults.getEnforcementAction());
me.setWarningMessage(extensionPolicyDefaults.getWarningMessage());
policy.setManagedExtension(me);
```

```java
ManagedExtension srcMe = updatedPolicy.getManagedExtension() != null
        ? updatedPolicy.getManagedExtension() : current.getManagedExtension();
if (srcMe != null) {
    ManagedExtension newMe = new ManagedExtension();
    newMe.setAction(srcMe.getAction());
    newMe.setEnforcementAction(srcMe.getEnforcementAction());
    newMe.setWarningMessage(srcMe.getWarningMessage());
    if (srcMe.getExtensions() != null) {
        List<ExtensionDetail> newDetails = srcMe.getExtensions().stream().map(e -> {
            ExtensionDetail d = new ExtensionDetail();
            d.setExtensionId(e.getExtensionId());
            d.setExtensionName(e.getExtensionName());
            d.setPublisher(e.getPublisher());
            return d;
        }).toList();
        newMe.setExtensions(newDetails);
    }
    newVersion.setManagedExtension(newMe);
}
```

```java
ManagedExtension me = new ManagedExtension();
if (global.getManagedExtension() != null) {
    me.setAction(global.getManagedExtension().getAction());
    me.setEnforcementAction(global.getManagedExtension().getEnforcementAction());
    me.setWarningMessage(global.getManagedExtension().getWarningMessage());
} else {
    me.setAction(ExtensionAction.ALLOW_ALL);
    me.setEnforcementAction(extensionPolicyDefaults.getEnforcementAction());
    me.setWarningMessage(extensionPolicyDefaults.getWarningMessage());
}
tenantDefault.setManagedExtension(me);
```

### `ExtensionPolicyDefaults.java`

```java
@Data
@Component
@ConfigurationProperties(prefix = "extension-policy.defaults")
public class ExtensionPolicyDefaults {

    private String enforcementAction;
    private String warningMessage;
}
```

### `application.properties`

```properties
extension-policy.defaults.enforcement-action=${EXTENSION_POLICY_DEFAULT_ENFORCEMENT_ACTION:WARN_USER}
extension-policy.defaults.warning-message=${EXTENSION_POLICY_DEFAULT_WARNING_MESSAGE:A prohibited extension has been detected. Please uninstall it to comply with company policy.}
```

