package com.secufusion.tenant.dto;

public class DeleteTenantRequest {
    private Boolean confirmDelete;

    public Boolean getConfirmDelete() {
        return confirmDelete;
    }

    public void setConfirmDelete(Boolean confirmDelete) {
        this.confirmDelete = confirmDelete;
    }
}