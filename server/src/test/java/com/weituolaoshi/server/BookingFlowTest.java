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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:booking-flow;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class BookingFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private JsonNode parse(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test void createsHeldAppointmentAndRejectsOverlappingOrder() throws Exception {
        String token=parse(mvc.perform(post("/v1/dev/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andReturn()).get("token").asText();
        mvc.perform(get("/v1/services")).andExpect(status().isOk());
        String date=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(3).toString();
        String quoteBody="{\"serviceId\":\"svc-1\",\"startAt\":\""+date+"T14:00:00\",\"durationHours\":3}";
        JsonNode quote=parse(mvc.perform(post("/v1/orders/quote").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(quoteBody)).andExpect(status().isOk()).andReturn());
        org.junit.jupiter.api.Assertions.assertEquals(56400,quote.get("payableAmount").asInt());
        String body="{\"quoteId\":\""+quote.get("quoteId").asText()+"\",\"venueId\":\"venue-demo-expo\"}";
        JsonNode order=parse(mvc.perform(post("/v1/orders").header("Authorization","Bearer "+token)
            .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn());
        org.junit.jupiter.api.Assertions.assertEquals("awaiting_payment",order.get("status").asText());
        JsonNode quote2=parse(mvc.perform(post("/v1/orders/quote").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(quoteBody)).andExpect(status().isOk()).andReturn());
        String body2="{\"quoteId\":\""+quote2.get("quoteId").asText()+"\",\"venueId\":\"venue-demo-expo\"}";
        mvc.perform(post("/v1/orders").header("Authorization","Bearer "+token)
            .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body2))
            .andExpect(status().isConflict());
    }
}
