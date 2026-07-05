package dev.eventcore;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Monitoring", description = "Liveness")
@RestController
class HealthController {

@Operation(summary = "Liveness check; returns OK")
    @GetMapping("/health")    String health() {
        return "OK";
    }
}