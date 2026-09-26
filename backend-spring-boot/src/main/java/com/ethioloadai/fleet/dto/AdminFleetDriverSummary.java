package com.ethioloadai.fleet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminFleetDriverSummary {

    private Long id;
    private String name;
    private String phone;
    private Boolean verified;

    @com.fasterxml.jackson.annotation.JsonProperty("is_verified")
    public Boolean getIsVerifiedSnake() {
        return verified;
    }
}
