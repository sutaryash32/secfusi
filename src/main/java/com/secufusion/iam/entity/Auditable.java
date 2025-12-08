package com.secufusion.iam.entity;


import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import com.secufusion.iam.listener.AuditListener;

import java.time.Instant;
import java.time.LocalDateTime;
import java.io.Serializable;

@Data
@MappedSuperclass
@EntityListeners(AuditListener.class)
public abstract class Auditable implements Serializable {
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

}

