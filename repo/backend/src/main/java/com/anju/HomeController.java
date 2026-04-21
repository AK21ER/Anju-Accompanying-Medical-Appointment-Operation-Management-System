package com.anju;

import com.anju.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "System", description = "Service status and root endpoint")
public class HomeController {

    @GetMapping("/")
    @Operation(summary = "Service home", description = "Returns service status and helpful endpoint hints.")
    public Result<Map<String, Object>> home() {
        return Result.success(Map.of(
                "service", "Anju Medical Appointment System API",
                "status", "running",
                "health", "/actuator/health",
                "auth", Map.of(
                        "register", "POST /auth/register",
                        "login", "POST /auth/login"
                )
        ));
    }
}
