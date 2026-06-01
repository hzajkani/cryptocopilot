package com.cryptocopilot.controller;

import com.cryptocopilot.dto.SignalDto;
import com.cryptocopilot.service.SignalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The fused ML + TA signals for the whole universe. */
@RestController
@Tag(name = "Signals", description = "Fused ML forecast + deterministic TA verdict per coin")
public class SignalsController {

    private final SignalService signalService;

    public SignalsController(SignalService signalService) {
        this.signalService = signalService;
    }

    @Operation(summary = "All signals",
            description = "10 coins, each with ML class + calibrated confidence + top-3 drivers + a TA verdict.")
    @GetMapping("/api/signals")
    public List<SignalDto> signals() {
        return signalService.signals();
    }
}
