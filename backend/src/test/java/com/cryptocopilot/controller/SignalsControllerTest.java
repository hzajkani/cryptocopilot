package com.cryptocopilot.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptocopilot.dto.DriverDto;
import com.cryptocopilot.dto.SignalDto;
import com.cryptocopilot.dto.TAVerdict;
import com.cryptocopilot.service.SignalService;
import com.cryptocopilot.util.Symbols;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web-slice test for {@code /api/signals} with a mocked {@link SignalService}. */
@WebMvcTest(SignalsController.class)
class SignalsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SignalService signalService;

    private static SignalDto sampleSignal(String symbol) {
        TAVerdict ta = new TAVerdict(symbol, Instant.parse("2026-05-31T08:00:00Z"),
                "BULLISH", "MODERATE", List.of("Price above the Ichimoku cloud (+2.0)"), 2.5);
        List<DriverDto> drivers = List.of(
                new DriverDto(1, "atr_pct", 0.0082, 0.36),
                new DriverDto(2, "day_of_week", 6.0, -0.18),
                new DriverDto(3, "ret_vol_7d", 0.0050, 0.15));
        return new SignalDto(symbol, Instant.parse("2026-05-31T08:00:00Z"),
                "UP", 0.61, 0.61, 0.20, 0.19, "v1", drivers, ta);
    }

    @Test
    void returnsTenCoinsEachWithMlAndTa() throws Exception {
        when(signalService.signals()).thenReturn(Symbols.UNIVERSE.stream()
                .map(SignalsControllerTest::sampleSignal)
                .toList());

        mockMvc.perform(get("/api/signals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                // every coin carries an ML class + calibrated confidence...
                .andExpect(jsonPath("$[*].mlClass", everyItem(notNullValue())))
                .andExpect(jsonPath("$[*].mlConfidence", everyItem(notNullValue())))
                // ...and a TA verdict block with a direction
                .andExpect(jsonPath("$[*].ta.direction", everyItem(notNullValue())))
                .andExpect(jsonPath("$[0].symbol").value("BTC"))
                .andExpect(jsonPath("$[0].mlClass").value("UP"))
                .andExpect(jsonPath("$[0].mlConfidence").value(0.61))
                .andExpect(jsonPath("$[0].drivers.length()").value(3))
                .andExpect(jsonPath("$[0].ta.direction").value("BULLISH"))
                .andExpect(jsonPath("$[0].ta.score").value(2.5));
    }
}
