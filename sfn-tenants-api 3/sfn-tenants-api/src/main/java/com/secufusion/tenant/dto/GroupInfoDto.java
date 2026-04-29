package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupInfoDto {
    private String pkEventsGroupId;    // EventsGroup.pkEventsGroupId
    private String name;  // EventsGroup.name
    private String groupType;          // "APIKEY_GROUP" or "AZURE_GROUP"
}
