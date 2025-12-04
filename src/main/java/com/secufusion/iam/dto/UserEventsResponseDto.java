package com.secufusion.iam.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class UserEventsResponseDto {
    private String userName;
    private List<EventDto> userEvents;
}

