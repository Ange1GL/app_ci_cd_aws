package com.app.dto.response;

import lombok.Builder;

@Builder
public record OcrResponse(
        String message
) {
}
