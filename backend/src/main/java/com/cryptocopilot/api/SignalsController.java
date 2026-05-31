package com.cryptocopilot.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The fused ML + TA signals for the whole universe. */
@RestController
public class SignalsController {

    private final SignalService signalService;

    public SignalsController(SignalService signalService) {
        this.signalService = signalService;
    }

    /** 10 rows, each with ML class + calibrated confidence + top-3 drivers + a TA verdict. */
    @GetMapping("/api/signals")
    public List<SignalDto> signals() {
        return signalService.signals();
    }
}
