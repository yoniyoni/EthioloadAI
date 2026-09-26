package com.ethioloadai.carrier.team.dto;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import jakarta.validation.constraints.NotNull;
import lombok.*;
@Getter @Setter
public class MemberRoleRequest { @NotNull private CompanyRole role; }

