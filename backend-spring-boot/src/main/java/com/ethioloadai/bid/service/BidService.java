package com.ethioloadai.bid.service;

import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface BidService {

    /**
     * Create a new bid on a freight request.
     *
     * @param freightId ID of the target freight
     * @param bidder the driver/fleet owner placing the bid
     * @param vehicleId ID of the vehicle for this bid
     * @param amount bid amount
     * @param note optional bid note
     * @param availableDateTime required for intracity bids
     * @return created bid
     */
    Bid createBid(Long freightId, User bidder, Long vehicleId, BigDecimal amount, 
                  String note, LocalDateTime availableDateTime);

    /**
     * Get all bids for a freight (shipper/admin view).
     *
     * @param freightId freight ID
     * @param shipper the freight owner (for authorization)
     * @return list of bids with details
     */
    List<Bid> getBidsForFreight(Long freightId, User shipper);

    /**
     * Get driver's own bids with pagination.
     *
     * @param driverId driver ID
     * @param pageable pagination info
     * @return page of driver's bids
     */
    Page<Bid> getDriverBids(Long driverId, Pageable pageable);

    /**
     * Get a specific bid with authorization check.
     *
     * @param bidId bid ID
     * @param user requesting user
     * @return bid if authorized
     */
    Bid getBidWithAuthorization(Long bidId, User user);

    /**
     * Create a counter-offer on an existing bid.
     *
     * @param bid the target bid
     * @param counterBy who is making the counter-offer
     * @param counterAmount new proposed amount
     * @param counterNote optional counter-offer note
     * @return updated bid
     */
    Bid createCounterOffer(Bid bid, User counterBy, BigDecimal counterAmount, String counterNote);

    /**
     * Withdraw a bid (driver only).
     *
     * @param bid the bid to withdraw
     * @param driver the bid owner
     * @return updated bid
     */
    Bid withdrawBid(Bid bid, User driver);

    /**
     * Reject a bid (shipper only).
     *
     * @param bid the bid to reject
     * @param shipper the freight owner
     * @return updated bid
     */
    Bid rejectBid(Bid bid, User shipper);

    /**
     * Rank/sort bids according to business rules.
     * - Intercity: amount ASC, then avg driver rating DESC
     * - Intracity: amount ASC only
     *
     * @param bids list of bids to rank
     * @return sorted list with top bid marked as recommended
     */
    List<Bid> rankBids(List<Bid> bids);

    /**
     * Check if bid status transition is valid.
     *
     * @param from current status
     * @param to target status
     * @return true if transition is allowed
     */
    boolean isValidStatusTransition(Bid.BidStatus from, Bid.BidStatus to);

    /**
     * Get active (pending/countered) bids for a freight.
     *
     * @param freightId freight ID
     * @return list of active bids
     */
    List<Bid> getActiveBidsForFreight(Long freightId);
}