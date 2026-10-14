package com.ethioloadai.message.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessagePageMetadata(
        String mode,
        String olderCursor,
        String afterCursor,
        Boolean hasOlder,
        Boolean hasMoreAfter
) {}
