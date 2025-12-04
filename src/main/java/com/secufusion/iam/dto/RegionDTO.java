package com.secufusion.iam.dto;

import jakarta.persistence.Column;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegionDTO {

    private Long regionid;
    @Column(name = "regionname")
    private String regionname;
}
