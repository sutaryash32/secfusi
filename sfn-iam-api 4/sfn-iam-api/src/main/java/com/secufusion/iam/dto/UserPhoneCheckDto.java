package com.secufusion.iam.dto;

import java.time.LocalDateTime;

/**
 * Minimal DTO used by phone lookup endpoint to avoid returning groups/roles/scopes.
 */
public class UserPhoneCheckDto {

    private String pkUserId;
    private String userName;
    private String email;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private String status;
    private LocalDateTime createdAt;

    public UserPhoneCheckDto(String pkUserId, String userName, String email, String phoneNumber,
                             String firstName, String lastName, String status, LocalDateTime createdAt) {
        this.pkUserId = pkUserId;
        this.userName = userName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getPkUserId() { return pkUserId; }
    public String getUserName() { return userName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
