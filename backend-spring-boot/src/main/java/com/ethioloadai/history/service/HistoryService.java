package com.ethioloadai.history.service;

import com.ethioloadai.freight.dto.FreightResponse;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.mapper.FreightMapper;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.history.dto.FreightHistoryResponse;
import com.ethioloadai.history.repository.HistoryBookingProjection;
import com.ethioloadai.history.repository.HistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HistoryService {
    private final FreightRepository freightRepository;
    private final HistoryRepository historyRepository;
    private final FreightMapper freightMapper;

    @Transactional(readOnly = true)
    public Slice<FreightHistoryResponse> getShipperFreightHistory(Long shipperId, Pageable pageable) {
        Slice<Freight> freightSlice = freightRepository.findShipperHistorySlice(shipperId, pageable);
        if (freightSlice.isEmpty()) {
            return freightSlice.map(freight ->
                    new FreightHistoryResponse(freightMapper.toResponse(freight), null, null, null));
        }

        Map<Long, HistoryBookingProjection> bookingsByFreight =
                historyRepository.findRepresentativeBookings(
                                freightSlice.getContent().stream().map(Freight::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(
                                HistoryBookingProjection::getFreightId,
                                Function.identity()));

        return freightSlice.map(freight -> toResponse(freight, bookingsByFreight.get(freight.getId())));
    }

    private FreightHistoryResponse toResponse(Freight freight, HistoryBookingProjection selected) {
        FreightResponse freightResponse = freightMapper.toResponse(freight);
        if (selected == null) {
            return new FreightHistoryResponse(freightResponse, null, null, null);
        }

        FreightHistoryResponse.BookingSummary booking =
                new FreightHistoryResponse.BookingSummary(
                        selected.getBookingId(),
                        selected.getBookingStatus(),
                        selected.getDriverName(),
                        selected.getBookingUpdatedAt());
        FreightHistoryResponse.TripSummary trip = selected.getTripId() == null
                ? null
                : new FreightHistoryResponse.TripSummary(
                        selected.getTripId(),
                        selected.getTripStatus(),
                        selected.getTripUpdatedAt(),
                        selected.getTripDriverName() == null
                                ? null
                                : new FreightHistoryResponse.DriverSummary(selected.getTripDriverName()));
        FreightHistoryResponse.PaymentSummary payment = selected.getPaymentId() == null
                ? null
                : new FreightHistoryResponse.PaymentSummary(
                        selected.getPaymentId(),
                        selected.getPaymentStatus(),
                        selected.getPaymentUpdatedAt());
        return new FreightHistoryResponse(freightResponse, booking, trip, payment);
    }
}
