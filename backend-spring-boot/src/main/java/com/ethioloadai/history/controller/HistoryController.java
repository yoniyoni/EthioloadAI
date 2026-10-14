package com.ethioloadai.history.controller;

import com.ethioloadai.history.dto.FreightHistoryResponse;
import com.ethioloadai.history.dto.HistoryPageResponse;
import com.ethioloadai.history.service.HistoryService;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
public class HistoryController {
    private static final int MAX_PAGE_SIZE = 100;
    private final HistoryService historyService;

    @GetMapping("/shipper/freight")
    @PreAuthorize("hasRole('SHIPPER')")
    public ResponseEntity<HistoryPageResponse<FreightHistoryResponse>> getShipperFreightHistory(
            @AuthenticationPrincipal User shipper,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Slice<FreightHistoryResponse> result = historyService.getShipperFreightHistory(
                shipper.getId(),
                PageRequest.of(safePage, safeSize,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return ResponseEntity.ok(new HistoryPageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(), result.hasNext()));
    }
}
