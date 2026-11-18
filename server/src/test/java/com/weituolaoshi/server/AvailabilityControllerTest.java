package com.weituolaoshi.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:availability-flow;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class AvailabilityControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private JsonNode body(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test void defaultsClosedAndCanOpenThenCloseOneDay() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(4);
        String path = "/v1/cosers/me/availability/" + date;
        JsonNode before = body(mvc.perform(get("/v1/cosers/me/availability")
            .header("X-Demo-User", "demo-coser-f").param("month", date.toString().substring(0, 7)))
            .andExpect(status().isOk()).andReturn());
        assertFalse(openFor(before, date));
        mvc.perform(put(path).header("X-Demo-User", "demo-coser-f")
            .contentType(MediaType.APPLICATION_JSON).content("{\"open\":true}"))
            .andExpect(status().isOk());
        JsonNode opened = body(mvc.perform(get("/v1/cosers/me/availability")
            .header("X-Demo-User", "demo-coser-f").param("month", date.toString().substring(0, 7)))
            .andExpect(status().isOk()).andReturn());
        assertTrue(openFor(opened, date));
        mvc.perform(put(path).header("X-Demo-User", "demo-coser-f")
            .contentType(MediaType.APPLICATION_JSON).content("{\"open\":false}"))
            .andExpect(status().isOk());
    }

    @Test void batchSettingsAreAuthorizedAndStored() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusMonths(2).withDayOfMonth(1);
        String month = date.toString().substring(0, 7);
        mvc.perform(post("/v1/cosers/me/availability/batch")
            .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"" + month + "\",\"type\":\"all_open\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/cosers/me/availability/batch").header("X-Demo-User", "demo-customer")
            .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"" + month + "\",\"type\":\"all_open\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/cosers/me/availability/batch").header("X-Demo-User", "demo-coser-f")
            .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"" + month + "\",\"type\":\"all_open\"}"))
            .andExpect(status().isOk());
        JsonNode result = body(mvc.perform(get("/v1/cosers/me/availability")
            .header("X-Demo-User", "demo-coser-f").param("month", month))
            .andExpect(status().isOk()).andReturn());
        assertTrue(openFor(result, date));
    }

    @Test void cannotCloseDayHeldByOrder() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(6);
        mvc.perform(put("/v1/cosers/me/availability/" + date).header("X-Demo-User", "demo-coser-f")
            .contentType(MediaType.APPLICATION_JSON).content("{\"open\":true}"))
            .andExpect(status().isOk());
        JsonNode quote = body(mvc.perform(post("/v1/orders/quote").header("X-Demo-User", "demo-customer")
            .contentType(MediaType.APPLICATION_JSON).content("{\"serviceId\":\"svc-1\",\"startAt\":\"" + date +
                "T10:00:00\",\"durationHours\":2}"))
            .andExpect(status().isOk()).andReturn());
        mvc.perform(post("/v1/orders").header("X-Demo-User", "demo-customer")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON).content("{\"quoteId\":\"" + quote.get("quoteId").asText() +
                "\",\"venueId\":\"venue-demo-expo\"}"))
            .andExpect(status().isOk());
        mvc.perform(put("/v1/cosers/me/availability/" + date).header("X-Demo-User", "demo-coser-f")
            .contentType(MediaType.APPLICATION_JSON).content("{\"open\":false}"))
            .andExpect(status().isConflict());
    }

    private boolean openFor(JsonNode result, LocalDate date) {
        for (JsonNode day : result.get("days"))
            if (day.get("date").asText().equals(date.toString())) return day.get("open").asBoolean();
        throw new AssertionError("日期不在响应中: " + date);
    }
}
