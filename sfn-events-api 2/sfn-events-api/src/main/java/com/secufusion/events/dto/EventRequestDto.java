package com.secufusion.events.dto;


import lombok.Data;

import java.util.List;

@Data
public class EventRequestDto {
    private List<EventDto> events;
}
