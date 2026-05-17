package com.app.controller;

import com.app.dto.response.OcrResponse;
import lombok.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/ocr")
public class OcrController
{
    @GetMapping
    public ResponseEntity<@NonNull OcrResponse> get() {
        return ResponseEntity.ok(new OcrResponse("Hello world"));
    }
}
