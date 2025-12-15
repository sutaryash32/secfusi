package com.secufusion.iam.dto;

import com.secufusion.iam.entity.Groups;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UsersDto {

    private String pkUserId;
    private String userName;
    private String email;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private String status;
    private String lastUpdatedBy;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String fkTenantId;
    //set of groups
    private Set<Groups> groups;
}
