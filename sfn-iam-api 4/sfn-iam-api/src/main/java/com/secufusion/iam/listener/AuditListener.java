// java
package com.secufusion.iam.listener;

import com.secufusion.iam.entity.Auditable;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.util.JwtUtl;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

public class AuditListener {

    public AuditListener() { }

    private JwtUtl getJwtUtl() {
        return SpringContext.getBean(JwtUtl.class);
    }

    private String getCurrentUsername() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "SYSTEM";
            HttpServletRequest req = attrs.getRequest();
            JwtUtl jwtUtl = getJwtUtl();
            if (jwtUtl != null) {
                Tenant tenant = jwtUtl.getTenantFromRequest(req);
                if (tenant != null && tenant.getTenantName() != null) {
                    return tenant.getTenantName();
                }
                String pref = jwtUtl.getPreferredUsernameFromRequest(req);
                if (pref != null) return pref;
                String user = jwtUtl.getUsername(req);
                if (user != null) return user;
            }
            return "SYSTEM";
        } catch (Exception e) {
            return "SYSTEM";
        }
    }


    @PrePersist
    public void setCreatedOn(Object target) {
        if (!(target instanceof Auditable a)) return;
        Instant now = Instant.now();
        String user = getCurrentUsername();
        if (a.getCreatedAt() == null) a.setCreatedAt(now);
        if (a.getCreatedBy() == null) a.setCreatedBy(user);
        a.setUpdatedAt(now);
        a.setUpdatedBy(user);
    }

    @PreUpdate
    public void setUpdatedOn(Object target) {
        if (!(target instanceof Auditable a)) return;
        Instant now = Instant.now();
        String user = getCurrentUsername();
        a.setUpdatedAt(now);
        a.setUpdatedBy(user);
    }
    @Component
    public static class SpringContext implements ApplicationContextAware {
        private static ApplicationContext CONTEXT;

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) {
            SpringContext.CONTEXT = applicationContext;
        }

        public static <T> T getBean(Class<T> clazz) {
            return CONTEXT != null ? CONTEXT.getBean(clazz) : null;
        }
    }
}