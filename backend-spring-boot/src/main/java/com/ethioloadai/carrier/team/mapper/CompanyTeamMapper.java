package com.ethioloadai.carrier.team.mapper;
import com.ethioloadai.carrier.team.dto.*;
import com.ethioloadai.carrier.team.entity.*;
import org.mapstruct.*;
@Mapper(componentModel="spring", unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface CompanyTeamMapper {
 InvitationResponse invitation(CompanyInvitation entity);
 @Mapping(target="fullName", ignore=true) @Mapping(target="email", ignore=true) @Mapping(target="companyName", ignore=true)
 MembershipResponse membership(CompanyMembership entity);
}

