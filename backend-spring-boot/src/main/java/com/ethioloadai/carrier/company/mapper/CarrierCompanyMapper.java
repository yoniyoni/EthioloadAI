package com.ethioloadai.carrier.company.mapper;

import com.ethioloadai.carrier.company.dto.*;
import com.ethioloadai.carrier.company.entity.*;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CarrierCompanyMapper {
    CarrierCompanyResponse toResponse(CarrierCompany company);
    CarrierCompanyDocumentResponse toResponse(CarrierCompanyDocument document);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "companyName", source = "companyName")
    @Mapping(target = "legalName", source = "legalName")
    @Mapping(target = "tinNumber", source = "tinNumber")
    @Mapping(target = "businessRegistrationNumber", source = "businessRegistrationNumber")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "businessAddress", source = "businessAddress")
    @Mapping(target = "city", source = "city")
    void update(CarrierCompanyRequest request, @MappingTarget CarrierCompany company);
}

