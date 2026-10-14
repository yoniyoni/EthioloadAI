package com.ethioloadai.carrier.team.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Phase 3/4 — Company fleet controller stub.
 *
 * <p>The full fleet management endpoints (drivers, vehicles, assignments)
 * now live in {@link com.ethioloadai.carrier.fleet.controller.CarrierFleetController}
 * under the same {@code /carrier-companies/{companyId}/fleet} base path.
 *
 * <p>This class is kept as a placeholder and to preserve the Spring component
 * scan path without creating ambiguous URL mappings.
 */
@RestController
@RequestMapping("/carrier-companies/{companyId}/fleet")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Company Fleet (Phase 3→4)",
     description = "Fleet management — see CarrierFleetController for all Phase 4 endpoints")
public class CompanyFleetController {
    // All driver/vehicle/assignment endpoints moved to CarrierFleetController (Phase 4).
    // This class is retained to avoid breaking any internal Spring wiring that references
    // the Phase 3 bean name "companyFleetController".
}
