package com.app.dto.response;

import lombok.Builder;

@Builder
public record GenericResponse(
        String message
) {
}
