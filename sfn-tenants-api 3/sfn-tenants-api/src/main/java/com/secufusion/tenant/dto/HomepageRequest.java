package com.secufusion.tenant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class HomepageRequest {

    private String title;
    private String url;

    @JsonProperty("disable_addressbar")
    private boolean disableAddressBar;
}