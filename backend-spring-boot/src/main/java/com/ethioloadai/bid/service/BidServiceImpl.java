package com.ethioloadai.bid.service;

import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.bid.exception.BidNotFoundException;
import com.ethioloadai.bid.exception.BidStatusException;
import com.ethioloadai.bid.exception.BidValidationException;
import com.ethioloadai.bid.repository.BidRepository;
import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BidServiceImpl implements BidService {
    private static final Logger log = LoggerFactory.getLogger(BidServiceImpl.class);

    private final BidRepository bidRepository;
    private final FreightRepository freightRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    // Valid bid status transitions
    private static final Set<Bid.BidStatus> PENDING_TRANSITIONS = EnumSet.of(
        Bid.BidStatus.ACCEPTED, Bid.BidStatus.REJECTED, 
        Bid.BidStatus.COUNTERED, Bid.BidStatus.EXPIRED
    );
    
    private static final Set<Bid.BidStatus> COUNTERED_TRANSITIONS = EnumSet.of(
        Bid.BidStatus.ACCEPTED, Bid.BidStatus.REJECTED, 
        Bid.BidStatus.COUNTERED, Bid.BidStatus.EXPIRED
    );

    private static final Set<Bid.BidStatus> FINAL_STATUSES = EnumSet.of(
        Bid.BidStatus.ACCEPTED, Bid.BidStatus.REJECTED, Bid.BidStatus.EXPIRED
    );

    @Override
    @Transactional
    public Bid createBid(Long freightId, User bidder, Long vehicleId, BigDecimal amount, 
                        String note, LocalDateTime availableDateTime) {
        log.info("Creating bid for freightId: {}, driverId: {}, vehicleId: {}", 
                freightId, bidder.getId(), vehicleId);

        // Get and validate freight
        Freight freight = freightRepository.findById(freightId)
                .orElseThrow(() -> new BidValidationException("Freight not found with ID: " + freightId));

        // Validate freight eligibility
        validateFreightEligibility(freight);

        // Get and validate vehicle
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new BidValidationException("Vehicle not found with ID: " + vehicleId));

        validateVehicleOwnership(vehicle, bidder);
        validateBidderEligibility(bidder, vehicle);

        // Check for duplicate bids
        if (bidRepository.existsByCargoRequest_IdAndVehicle_Id(freight.getId(), vehicleId)) {
            throw BidValidationException.duplicateBid(freight.getId(), vehicleId);
        }

        // Validate fixed-price freight exact amount
        if (freight.getPriceType() == Freight.PriceType.FIXED) {
            if (freight.getBudget() != null) {
                if (amount == null) {
                    amount = freight.getBudget();
                } else if (amount.compareTo(freight.getBudget()) != 0) {
                    throw BidValidationException.fixedPriceMismatch(freight.getBudget());
                }
                amount = freight.getBudget();
            }
        }

        // Validate intracity requirements
        if (freight.getServiceType() == Freight.ServiceType.INTRACITY) {
            if (availableDateTime == null) {
                throw BidValidationException.availableDateTimeRequired();
            }
            if (availableDateTime.isBefore(LocalDateTime.now())) {
                throw BidValidationException.availableDateTimeFuture();
            }
        }

        // Create and save bid
        Bid bid = Bid.builder()
                .cargoRequest(freight)
                .driver(bidder)
                .vehicle(vehicle)
                .amount(amount)
                .note(note)
                .availableDatetime(availableDateTime)
                .status(Bid.BidStatus.PENDING)
                .build();

        bid = bidRepository.save(bid);
        log.info("Bid created — bidId: {}, freightId: {}, driverId: {}", 
                bid.getId(), freight.getId(), bidder.getId());
        
        return bid;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Bid> getBidsForFreight(Long freightId, User shipper) {
        log.info("Fetching bids for freightId: {}, shipperId: {}", freightId, shipper.getId());

        // Verify freight ownership
        Freight freight = freightRepository.findById(freightId)
                .orElseThrow(() -> new BidNotFoundException(freightId));

        if (!freight.getShipper().getId().equals(shipper.getId()) && 
            shipper.getRole() != User.Role.ADMIN) {
            throw new AuthorizationException("You can only view bids for your own freight");
        }

        return bidRepository.findByCargoRequestIdWithDetails(freightId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Bid> getDriverBids(Long driverId, Pageable pageable) {
        log.info("Fetching bids for driverId: {}", driverId);
        return bidRepository.findByDriver_Id(driverId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Bid getBidWithAuthorization(Long bidId, User user) {
        log.info("Fetching bid {} for user {}", bidId, user.getId());

        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId));

        // Check authorization: bid owner, freight owner, or admin
        boolean isDriver = bid.getDriver().getId().equals(user.getId());
        boolean isShipper = bid.getCargoRequest().getShipper().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == User.Role.ADMIN;

        if (!isDriver && !isShipper && !isAdmin) {
            throw new AuthorizationException("You are not authorized to view this bid");
        }

        return bid;
    }

    @Override
    @Transactional
    public Bid createCounterOffer(Bid bid, User counterBy, BigDecimal counterAmount, String counterNote) {
        log.info("Creating counter-offer for bidId: {}, counterBy: {}, amount: {}", 
                bid.getId(), counterBy.getId(), counterAmount);

        // Validate bid status
        if (bid.getStatus() != Bid.BidStatus.PENDING && bid.getStatus() != Bid.BidStatus.COUNTERED) {
            throw BidStatusException.notEligibleFor("counter", bid.getStatus());
        }

        // Validate freight is negotiable
        if (bid.getCargoRequest() != null && bid.getCargoRequest().getPriceType() == Freight.PriceType.FIXED) {
            throw new BidValidationException("Counter-offers are not allowed on fixed-price freight");
        }

        // Determine counter-by role and validate authorization
        Bid.CounterBy counterByRole;
        boolean isShipper = bid.getCargoRequest().getShipper().getId().equals(counterBy.getId());
        boolean isDriver = bid.getDriver().getId().equals(counterBy.getId());

        if (isShipper) {
            counterByRole = Bid.CounterBy.SHIPPER;
            // Shipper can counter pending bids or respond to driver counters
            if (bid.getStatus() == Bid.BidStatus.COUNTERED && bid.getCounterBy() != Bid.CounterBy.DRIVER) {
                throw new BidStatusException("No driver counter-offer to respond to");
            }
        } else if (isDriver) {
            counterByRole = Bid.CounterBy.DRIVER;
            // Driver can only respond to shipper counters
            if (bid.getStatus() == Bid.BidStatus.PENDING) {
                throw new BidStatusException("Cannot counter initial bid. Wait for shipper response.");
            }
            if (bid.getCounterBy() != Bid.CounterBy.SHIPPER) {
                throw new BidStatusException("No shipper counter-offer to respond to");
            }
        } else {
            throw new AuthorizationException("You are not authorized to counter this bid");
        }

        // Update bid with counter-offer
        bid.setCounterAmount(counterAmount);
        bid.setCounterNote(counterNote);
        bid.setCounterBy(counterByRole);
        bid.setCounterAt(LocalDateTime.now());
        bid.setStatus(Bid.BidStatus.COUNTERED);

        bid = bidRepository.save(bid);
        log.info("Counter-offer created — bidId: {}, counterBy: {}", bid.getId(), counterByRole);
        
        return bid;
    }

    @Override
    @Transactional
    public Bid withdrawBid(Bid bid, User driver) {
        log.info("Withdrawing bid {} by driver {}", bid.getId(), driver.getId());

        // Validate ownership
        if (!bid.getDriver().getId().equals(driver.getId())) {
            throw new AuthorizationException("You can only withdraw your own bids");
        }

        // Validate status
        if (FINAL_STATUSES.contains(bid.getStatus())) {
            throw BidStatusException.alreadyFinal(bid.getStatus());
        }

        bid.setStatus(Bid.BidStatus.REJECTED);  // Withdrawal sets status to REJECTED
        bid = bidRepository.save(bid);
        
        log.info("Bid withdrawn — bidId: {}, driverId: {}", bid.getId(), driver.getId());
        return bid;
    }

    @Override
    @Transactional
    public Bid rejectBid(Bid bid, User shipper) {
        log.info("Rejecting bid {} by shipper {}", bid.getId(), shipper.getId());

        // Validate ownership
        if (!bid.getCargoRequest().getShipper().getId().equals(shipper.getId()) && 
            shipper.getRole() != User.Role.ADMIN) {
            throw new AuthorizationException("You can only reject bids for your own freight");
        }

        // Validate status
        if (FINAL_STATUSES.contains(bid.getStatus())) {
            throw BidStatusException.alreadyFinal(bid.getStatus());
        }

        bid.setStatus(Bid.BidStatus.REJECTED);
        bid = bidRepository.save(bid);
        
        log.info("Bid rejected — bidId: {}, shipperId: {}", bid.getId(), shipper.getId());
        return bid;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Bid> rankBids(List<Bid> bids) {
        if (bids.isEmpty()) {
            return bids;
        }

        // Determine service type from first bid
        Freight.ServiceType serviceType = bids.get(0).getCargoRequest().getServiceType();

        // Sort according to business rules
        List<Bid> sortedBids;
        if (serviceType == Freight.ServiceType.INTRACITY) {
            // Intracity: amount ASC only
            sortedBids = bids.stream()
                    .sorted((b1, b2) -> b1.getAmount().compareTo(b2.getAmount()))
                    .toList();
        } else {
            // Intercity: amount ASC, then avg driver rating DESC (tiebreaker)
            sortedBids = bids.stream()
                    .sorted((b1, b2) -> {
                        int amountComparison = b1.getAmount().compareTo(b2.getAmount());
                        if (amountComparison != 0) {
                            return amountComparison;
                        }
                        
                        // Tiebreaker: driver rating (higher is better)
                        Double rating1 = bidRepository.findAverageRatingByDriverId(b1.getDriver().getId()).orElse(0.0);
                        Double rating2 = bidRepository.findAverageRatingByDriverId(b2.getDriver().getId()).orElse(0.0);
                        return rating2.compareTo(rating1); // DESC order
                    })
                    .toList();
        }

        // Mark the top bid as recommended (in-memory only)
        if (!sortedBids.isEmpty()) {
            sortedBids.get(0).setIsRecommended(true);
        }

        return sortedBids;
    }

    @Override
    public boolean isValidStatusTransition(Bid.BidStatus from, Bid.BidStatus to) {
        return switch (from) {
            case PENDING -> PENDING_TRANSITIONS.contains(to);
            case COUNTERED -> COUNTERED_TRANSITIONS.contains(to);
            case ACCEPTED, REJECTED, EXPIRED -> false; // Final states
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<Bid> getActiveBidsForFreight(Long freightId) {
        return bidRepository.findActiveBidsByCargoRequestId(freightId);
    }

    // Private helper methods

    private void validateFreightEligibility(Freight freight) {
        if (freight.getStatus() != Freight.FreightStatus.POSTED) {
            throw BidValidationException.freightNotAcceptingBids("status is " + freight.getStatus());
        }

        if (freight.getBidDeadline() != null && LocalDateTime.now().isAfter(freight.getBidDeadline())) {
            throw BidValidationException.bidDeadlinePassed();
        }
    }

    private void validateVehicleOwnership(Vehicle vehicle, User bidder) {
        boolean isOwner;
        
        if (bidder.getRole() == User.Role.FLEET_OWNER) {
            isOwner = vehicle.getFleetOwnerId() != null && 
                     vehicle.getFleetOwnerId().equals(bidder.getId());
            if (!isOwner) {
                throw BidValidationException.vehicleNotOwnedBy(vehicle.getId(), "fleet");
            }
        } else if (bidder.getRole() == User.Role.DRIVER) {
            isOwner = vehicle.getUserId() != null && 
                     vehicle.getUserId().equals(bidder.getId());
            if (!isOwner) {
                throw BidValidationException.vehicleNotOwnedBy(vehicle.getId(), "driver");
            }
        } else {
            throw new AuthorizationException("Only drivers and fleet owners can place bids");
        }
    }

    private void validateBidderEligibility(User bidder, Vehicle vehicle) {
        if (!Boolean.TRUE.equals(bidder.getIsActive()) ||
            !Boolean.TRUE.equals(bidder.getVerificationStatus())) {
            throw new BidValidationException("Driver or fleet owner account is not verified for bidding");
        }

        if (vehicle.getApprovalStatus() != Vehicle.VehicleApprovalStatus.APPROVED) {
            throw new BidValidationException("Vehicle approval status is " + vehicle.getApprovalStatus() + "; only APPROVED vehicles may bid");
        }

        if (!"available".equalsIgnoreCase(vehicle.getAvailabilityStatus())) {
            throw new BidValidationException("Vehicle is not available for bidding");
        }

        if (bidder.getRole() == User.Role.DRIVER) {
            User vehicleOwner = userRepository.findById(vehicle.getUserId())
                    .orElseThrow(() -> new BidValidationException("Vehicle owner not found"));
            if (!Boolean.TRUE.equals(vehicleOwner.getVerificationStatus()) ||
                !Boolean.TRUE.equals(vehicleOwner.getIsActive())) {
                throw new BidValidationException("Vehicle owner is not verified for bidding");
            }
        }
    }
}