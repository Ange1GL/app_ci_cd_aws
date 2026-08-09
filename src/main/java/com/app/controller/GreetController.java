package com.app.controller;

import com.app.dto.response.GenericResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/greet")
@Slf4j
public class GreetController
{
    @GetMapping
    public ResponseEntity<@NonNull GenericResponse> get() {
        log.info("Entering GreetController.get()");
        return ResponseEntity.ok(new GenericResponse("Hola Miguel Nuevo"));
    }

    @PostMapping
    public ResponseEntity<@NonNull GenericResponse> create() {
        log.info("Entering GreetController.create()");
        return ResponseEntity.ok(new GenericResponse("Create - GRET"));
    }
}
