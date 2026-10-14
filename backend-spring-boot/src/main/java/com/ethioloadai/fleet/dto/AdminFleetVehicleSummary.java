package com.ethioloadai.fleet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminFleetVehicleSummary {

    private Long id;
    private String plateNumber;
    private String truckType;
    private String status;

    @JsonProperty("plate_number")
    public String getPlateNumberSnake() {
        return plateNumber;
    }

    @JsonProperty("truck_type")
    public String getTruckTypeSnake() {
        return truckType;
    }
}
