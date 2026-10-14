package com.ethioloadai.history.dto;

import java.util.List;

public record HistoryPageResponse<T>(
        List<T> content,
        int number,
        int size,
        boolean hasNext) {}
