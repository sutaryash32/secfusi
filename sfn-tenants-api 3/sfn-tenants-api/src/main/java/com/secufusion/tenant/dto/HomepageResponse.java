package com.secufusion.tenant.dto;

import lombok.Data;

@Data
public class HomepageResponse {

    private String title;
    private String url;
    private boolean disableAddressBar;
}