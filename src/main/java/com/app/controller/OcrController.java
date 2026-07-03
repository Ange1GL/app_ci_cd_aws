package com.app.controller;

import com.app.dto.response.OcrResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/ocr")
@Slf4j
public class OcrController
{
    @GetMapping
    public ResponseEntity<@NonNull OcrResponse> get() {
        log.info("Entering OcrController.get()");
        return ResponseEntity.ok(new OcrResponse("Hello Angel Gbariel"));
    }


    @PostMapping
    public ResponseEntity<@NonNull OcrResponse> create() {
        log.info("Entering OcrController.create()");
        return ResponseEntity.ok(new OcrResponse("Create - OCR"));
    }
}
