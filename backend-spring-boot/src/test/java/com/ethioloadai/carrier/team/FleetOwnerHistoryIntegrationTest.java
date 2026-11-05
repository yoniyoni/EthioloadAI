package com.ethioloadai.carrier.team;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.booking.repository.BookingVisibilitySpecifications;
import com.ethioloadai.carrier.company.entity.CarrierCompany;
import com.ethioloadai.carrier.company.entity.VerificationStatus;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.dispatch.entity.DispatchAssignment;
import com.ethioloadai.carrier.dispatch.repository.DispatchAssignmentRepository;
import com.ethioloadai.carrier.offer.entity.CarrierOffer;
import com.ethioloadai.carrier.offer.repository.CarrierOfferRepository;
import com.ethioloadai.carrier.team.entity.CompanyMembership;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import com.ethioloadai.carrier.team.entity.MembershipStatus;
import com.ethioloadai.carrier.team.repository.CompanyMembershipRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.freight.service.FreightVisibilityProperties;
import com.ethioloadai.security.jwt.JwtService;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.trip.service.TripSecurityService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class FleetOwnerHistoryIntegrationTest {

    @Autowired private BookingRepository bookings;
    @Autowired private CarrierCompanyRepository companies;
    @Autowired private CompanyMembershipRepository memberships;
    @Autowired private CarrierOfferRepository offers;
    @Autowired private DispatchAssignmentRepository dispatches;
    @Autowired private FreightRepository freightRepository;
    @Autowired private TripRepository trips;
    @Autowired private TripSecurityService tripSecurityService;
    @Autowired private FreightVisibilityProperties visibilityProperties;
    @Autowired private UserRepository users;
    @Autowired private VehicleRepository vehicles;
    @Autowired private JwtService jwt;
    @Autowired private MockMvc mvc;

    @Test
    void approvedActiveMembershipSeesHistoricalCompanyLinksButNotOtherCompanyOrLegacyRecords()
            throws Exception {
        User owner = user(User.Role.FLEET_OWNER);
        User otherOwner = user(User.Role.FLEET_OWNER);
        User driver = user(User.Role.DRIVER);
        User shipper = user(User.Role.SHIPPER);
        CarrierCompany authorizedCompany = company(owner, VerificationStatus.APPROVED);
        CarrierCompany otherCompany = company(otherOwner, VerificationStatus.APPROVED);
        membership(authorizedCompany, owner, MembershipStatus.ACTIVE);
        membership(otherCompany, otherOwner, MembershipStatus.ACTIVE);
        assertThat(memberships.findApprovedCompanyIdsByActiveMembershipUserId(owner.getId()))
                .containsExactly(authorizedCompany.getId());

        Vehicle authorizedVehicle = vehicle(driver, authorizedCompany.getId());
        Freight directFreight = freight(shipper);
        Booking directBooking = booking(directFreight, authorizedVehicle, driver);
        directBooking.setCarrierCompanyId(authorizedCompany.getId());

        Freight offerFreight = freight(shipper);
        CarrierOffer historicalOffer = offer(offerFreight, authorizedCompany, owner);
        Booking offerBooking = booking(offerFreight, authorizedVehicle, driver);
        offerBooking.setCarrierOfferId(historicalOffer.getId());

        Freight dispatchFreight = freight(shipper);
        CarrierOffer dispatchOffer = offer(dispatchFreight, authorizedCompany, owner);
        DispatchAssignment historicalDispatch = dispatch(dispatchFreight, dispatchOffer, authorizedCompany,
                owner, driver, authorizedVehicle);
        Booking dispatchBooking = booking(dispatchFreight, authorizedVehicle, driver);
        dispatchBooking.setDispatchAssignmentId(historicalDispatch.getId());

        Freight legacyFreight = freight(shipper);
        Booking legacyBooking = booking(legacyFreight, authorizedVehicle, driver);

        Vehicle otherVehicle = vehicle(driver, otherCompany.getId());
        Freight otherFreight = freight(shipper);
        Booking otherBooking = booking(otherFreight, otherVehicle, driver);
        otherBooking.setCarrierCompanyId(otherCompany.getId());

        directBooking = bookings.saveAndFlush(directBooking);
        offerBooking = bookings.saveAndFlush(offerBooking);
        dispatchBooking = bookings.saveAndFlush(dispatchBooking);
        legacyBooking = bookings.saveAndFlush(legacyBooking);
        otherBooking = bookings.saveAndFlush(otherBooking);
        Trip directTrip = trip(directBooking, driver, authorizedVehicle);
        Trip offerTrip = trip(offerBooking, driver, authorizedVehicle);
        Trip dispatchTrip = trip(dispatchBooking, driver, authorizedVehicle);
        Trip legacyTrip = trip(legacyBooking, driver, authorizedVehicle);
        Trip otherTrip = trip(otherBooking, driver, otherVehicle);
        assertThat(bookings.findAll(BookingVisibilitySpecifications.callerScope(
                owner, List.of(authorizedCompany.getId()))))
                .extracting(Booking::getId)
                .contains(directBooking.getId(), offerBooking.getId(), dispatchBooking.getId());
        assertThat(bookings.findAll(BookingVisibilitySpecifications.withinHistoryWindow(
                        visibilityProperties.getCarrierHistoryDays(), LocalDateTime.now())))
                .extracting(Booking::getId)
                .contains(directBooking.getId(), offerBooking.getId(), dispatchBooking.getId());
        assertThat(bookings.findAll(BookingVisibilitySpecifications.callerScope(
                        owner, List.of(authorizedCompany.getId()))
                .and(BookingVisibilitySpecifications.withinHistoryWindow(
                        visibilityProperties.getCarrierHistoryDays(), LocalDateTime.now()))))
                .extracting(Booking::getId)
                .contains(directBooking.getId(), offerBooking.getId(), dispatchBooking.getId());

        String authorization = bearer(owner);
        mvc.perform(get("/bookings/page").header("Authorization", authorization)
                        .queryParam("size", "100")
                        .queryParam("companyId", otherCompany.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(
                        directBooking.getId().intValue(), offerBooking.getId().intValue(),
                        dispatchBooking.getId().intValue())))
                .andExpect(jsonPath("$.content[*].id").value(not(containsInAnyOrder(
                        legacyBooking.getId().intValue(), otherBooking.getId().intValue()))));
        mvc.perform(get("/bookings").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder(
                        directBooking.getId().intValue(), offerBooking.getId().intValue(),
                        dispatchBooking.getId().intValue())))
                .andExpect(jsonPath("$[*].id").value(not(containsInAnyOrder(
                        legacyBooking.getId().intValue(), otherBooking.getId().intValue()))));
        mvc.perform(get("/bookings/" + directBooking.getId()).header("Authorization", authorization))
                .andExpect(status().isOk());
        mvc.perform(get("/bookings/" + otherBooking.getId()).header("Authorization", authorization))
                .andExpect(status().isForbidden());
        mvc.perform(get("/bookings/" + legacyBooking.getId()).header("Authorization", authorization))
                .andExpect(status().isForbidden());

        mvc.perform(get("/trips").header("Authorization", authorization)
                        .queryParam("size", "100")
                        .queryParam("companyId", otherCompany.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(
                        directTrip.getId().intValue(), offerTrip.getId().intValue(),
                        dispatchTrip.getId().intValue())))
                .andExpect(jsonPath("$.content[*].id").value(not(containsInAnyOrder(
                        legacyTrip.getId().intValue(), otherTrip.getId().intValue()))));
        mvc.perform(get("/trips/" + directTrip.getId()).header("Authorization", authorization))
                .andExpect(status().isOk());
        mvc.perform(get("/trips/" + otherTrip.getId()).header("Authorization", authorization))
                .andExpect(status().isNotFound());

        mvc.perform(get("/trips").header("Authorization", authorization)
                        .queryParam("status", "ONGOING")
                        .queryParam("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(
                        directTrip.getId().intValue(), offerTrip.getId().intValue(),
                        dispatchTrip.getId().intValue())));
        mvc.perform(get("/trips").header("Authorization", authorization)
                        .queryParam("status", "ONGOING", "ARRIVED")
                        .queryParam("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(
                        directTrip.getId().intValue(), offerTrip.getId().intValue(),
                        dispatchTrip.getId().intValue())));

        var dateRange = tripSecurityService.getAuthorizedTripsByDateRange(owner,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                PageRequest.of(0, 100));
        assertThat(dateRange.getContent()).extracting(Trip::getId)
                .containsExactlyInAnyOrder(directTrip.getId(), offerTrip.getId(), dispatchTrip.getId());
        assertThat(tripSecurityService.validateTripAccess(
                directTrip.getId(), owner, TripSecurityService.AccessType.READ).hasAccess()).isTrue();
        assertThat(tripSecurityService.validateTripAccess(
                otherTrip.getId(), owner, TripSecurityService.AccessType.READ).hasAccess()).isFalse();
    }

    @Test
    void inactiveMembershipAndUnapprovedCompanyCannotReadTheirLinkedHistory() throws Exception {
        User owner = user(User.Role.FLEET_OWNER);
        User driver = user(User.Role.DRIVER);
        User shipper = user(User.Role.SHIPPER);
        CarrierCompany inactiveCompany = company(owner, VerificationStatus.APPROVED);
        CarrierCompany unapprovedCompany = company(owner, VerificationStatus.PENDING);
        membership(inactiveCompany, owner, MembershipStatus.INACTIVE);
        membership(unapprovedCompany, owner, MembershipStatus.ACTIVE);
        Vehicle vehicle = vehicle(driver, inactiveCompany.getId());
        Booking inactiveBooking = booking(freight(shipper), vehicle, driver);
        inactiveBooking.setCarrierCompanyId(inactiveCompany.getId());
        Booking unapprovedBooking = booking(freight(shipper), vehicle, driver);
        unapprovedBooking.setCarrierCompanyId(unapprovedCompany.getId());
        bookings.saveAndFlush(inactiveBooking);
        bookings.saveAndFlush(unapprovedBooking);
        trip(inactiveBooking, driver, vehicle);
        trip(unapprovedBooking, driver, vehicle);

        mvc.perform(get("/bookings/page").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.size").value(20));
        mvc.perform(get("/trips").header("Authorization", bearer(owner)).queryParam("size", "13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.size").value(13))
                .andExpect(jsonPath("$.pageable.paged").value(true))
                .andExpect(jsonPath("$.pageable.offset").value(0));
    }

    @Test
    void fleetOwnerWithoutApprovedMembershipReceivesSerializableEmptyPages() throws Exception {
        User owner = user(User.Role.FLEET_OWNER);

        mvc.perform(get("/bookings/page").header("Authorization", bearer(owner))
                        .queryParam("page", "2").queryParam("size", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.size").value(7))
                .andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get("/trips").header("Authorization", bearer(owner))
                        .queryParam("page", "2").queryParam("size", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.size").value(7))
                .andExpect(jsonPath("$.pageable.paged").value(true))
                .andExpect(jsonPath("$.pageable.offset").value(14));
    }

    private User user(User.Role role) {
        String id = UUID.randomUUID().toString();
        return users.saveAndFlush(User.builder()
                .fullName("History " + role)
                .phone("+" + id.substring(0, 12))
                .email(id + "@history.test")
                .password("unused")
                .role(role)
                .verificationStatus(true)
                .isActive(true)
                .build());
    }

    private CarrierCompany company(User owner, VerificationStatus status) {
        return companies.saveAndFlush(CarrierCompany.builder()
                .companyName("History company " + UUID.randomUUID())
                .ownerId(owner.getId())
                .verificationStatus(status)
                .build());
    }

    private void membership(CarrierCompany company, User user, MembershipStatus status) {
        CompanyMembership membership = new CompanyMembership();
        membership.setCarrierCompanyId(company.getId());
        membership.setUserId(user.getId());
        membership.setRole(CompanyRole.CARRIER_OWNER);
        membership.setStatus(status);
        membership.setJoinedAt(LocalDateTime.now());
        memberships.saveAndFlush(membership);
    }

    private Vehicle vehicle(User driver, Long companyId) {
        return vehicles.saveAndFlush(Vehicle.builder()
                .userId(driver.getId())
                .carrierCompanyId(companyId)
                .truckType("History test truck")
                .plateNumber("HIST-" + UUID.randomUUID().toString().substring(0, 8))
                .capacity(10.0)
                .approvalStatus(Vehicle.VehicleApprovalStatus.APPROVED)
                .build());
    }

    private Freight freight(User shipper) {
        return freightRepository.saveAndFlush(Freight.builder()
                .shipper(shipper)
                .pickupLocation("History pickup")
                .destination("History destination")
                .materialType("General")
                .cargoDescription("History test freight")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("normal")
                .build());
    }

    private CarrierOffer offer(Freight freight, CarrierCompany company, User owner) {
        return offers.saveAndFlush(CarrierOffer.builder()
                .freight(freight)
                .carrierCompany(company)
                .submittedBy(owner)
                .pricingType(CarrierOffer.PricingType.FIXED)
                .currentAmount(BigDecimal.valueOf(1000))
                .status(CarrierOffer.OfferStatus.ACCEPTED)
                .acceptedAt(LocalDateTime.now())
                .agreedAmount(BigDecimal.valueOf(1000))
                .build());
    }

    private DispatchAssignment dispatch(
            Freight freight, CarrierOffer offer, CarrierCompany company,
            User owner, User driver, Vehicle vehicle) {
        return dispatches.saveAndFlush(DispatchAssignment.builder()
                .freightId(freight.getId())
                .carrierOfferId(offer.getId())
                .carrierCompanyId(company.getId())
                .driverUserId(driver.getId())
                .vehicleId(vehicle.getId())
                .assignedBy(owner.getId())
                .agreedAmount(BigDecimal.valueOf(1000))
                .build());
    }

    private Booking booking(Freight freight, Vehicle vehicle, User driver) {
        return bookings.saveAndFlush(Booking.builder()
                .cargoRequest(freight)
                .vehicle(vehicle)
                .driver(driver)
                .bookingStatus(Booking.BookingStatus.ACCEPTED)
                .estimatedPrice(BigDecimal.valueOf(1000))
                .commissionFee(BigDecimal.ZERO)
                .build());
    }

    private Trip trip(Booking booking, User driver, Vehicle vehicle) {
        return trips.saveAndFlush(Trip.builder()
                .booking(booking)
                .driver(driver)
                .vehicle(vehicle)
                .startLocation("History pickup")
                .destination("History destination")
                .startTime(LocalDateTime.now().minusHours(1))
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwt.generateToken(user);
    }
}
