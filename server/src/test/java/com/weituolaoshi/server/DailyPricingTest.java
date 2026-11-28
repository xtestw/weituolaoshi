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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:daily-pricing;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class DailyPricingTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;

    @Test void coserCanCreateAndEditDailyService() throws Exception {
        JsonNode created=json.readTree(mvc.perform(post("/v1/services").header("X-Demo-User","demo-coser-f")
            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"全天服务\",\"description\":\"测试\",\"city\":\"上海\",\"pricingUnit\":\"both\",\"pricePerHour\":15000,\"pricePerDay\":90000,\"minHours\":2}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String id=created.get("id").asText();
        assertEquals("both",db.queryForObject("SELECT pricing_unit FROM service_post WHERE id=?",String.class,id));
        assertEquals(90000L,db.queryForObject("SELECT price_per_day FROM service_post WHERE id=?",Long.class,id));
        mvc.perform(put("/v1/services/"+id).header("X-Demo-User","demo-coser-f")
            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"全天服务\",\"description\":\"测试\",\"city\":\"上海\",\"pricingUnit\":\"both\",\"pricePerHour\":15000,\"pricePerDay\":100000,\"minHours\":2}"))
            .andExpect(status().isOk());
        assertEquals(100000L,db.queryForObject("SELECT price_per_day FROM service_post WHERE id=?",Long.class,id));
    }

    @Test void dualPriceServiceQuotesOneDayAndSnapshotsUnit() throws Exception {
        db.update("UPDATE service_post SET pricing_unit='both',price_per_day=120000 WHERE id='svc-1'");
        mvc.perform(get("/v1/services/svc-1")).andExpect(status().isOk())
            .andExpect(jsonPath("$.pricingUnit").value("both"))
            .andExpect(jsonPath("$.pricePerDay").value(120000));
        String date=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(7).toString();
        db.update("INSERT INTO availability_slot(id,coser_id,start_at,end_at,status) VALUES (?,'demo-coser-f',?,?,'open')",
            "slot-"+UUID.randomUUID(),java.sql.Timestamp.valueOf(date+" 00:00:00"),java.sql.Timestamp.valueOf(LocalDate.parse(date).plusDays(1).atStartOfDay()));
        mvc.perform(post("/v1/orders/quote").header("X-Demo-User","demo-customer")
            .contentType(MediaType.APPLICATION_JSON).content("{\"serviceId\":\"svc-1\",\"pricingUnit\":\"day\",\"startAt\":\""+date+"T00:00:00\",\"durationHours\":2}"))
            .andExpect(status().isBadRequest());
        JsonNode quote=json.readTree(mvc.perform(post("/v1/orders/quote").header("X-Demo-User","demo-customer")
            .contentType(MediaType.APPLICATION_JSON).content("{\"serviceId\":\"svc-1\",\"pricingUnit\":\"day\",\"startAt\":\""+date+"T00:00:00\",\"durationHours\":24}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.payableAmount").value(120000)).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/v1/orders/quote").header("X-Demo-User","demo-customer")
            .contentType(MediaType.APPLICATION_JSON).content("{\"serviceId\":\"svc-1\",\"pricingUnit\":\"hour\",\"startAt\":\""+date+"T00:00:00\",\"durationHours\":24}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.payableAmount").value(451200));
        JsonNode order=json.readTree(mvc.perform(post("/v1/orders").header("X-Demo-User","demo-customer")
            .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"quoteId\":\""+quote.get("quoteId").asText()+"\",\"venueId\":\"venue-demo-expo\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("day",db.queryForObject("SELECT unit_price_unit FROM booking_order WHERE id=?",String.class,order.get("id").asText()));
    }

    @Test void hourlyBookingCanSpanTwoOpenDaysForTwentyFourHours() throws Exception {
        String date=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(11).toString();
        LocalDate first=LocalDate.parse(date);
        for (int offset=0; offset<2; offset++) {
            LocalDate day=first.plusDays(offset);
            db.update("INSERT INTO availability_slot(id,coser_id,start_at,end_at,status) VALUES (?,'demo-coser-f',?,?,'open')",
                "slot-"+UUID.randomUUID(),java.sql.Timestamp.valueOf(day.atStartOfDay()),java.sql.Timestamp.valueOf(day.plusDays(1).atStartOfDay()));
        }
        JsonNode quote=json.readTree(mvc.perform(post("/v1/orders/quote").header("X-Demo-User","demo-customer")
            .contentType(MediaType.APPLICATION_JSON).content("{\"serviceId\":\"svc-1\",\"pricingUnit\":\"hour\",\"startAt\":\""+date+"T10:00:00\",\"durationHours\":24}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.payableAmount").value(451200)).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/v1/orders").header("X-Demo-User","demo-customer")
            .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"quoteId\":\""+quote.get("quoteId").asText()+"\",\"venueId\":\"venue-demo-expo\"}"))
            .andExpect(status().isOk());
        for (LocalDate day : new LocalDate[]{first, first.plusDays(1)}) {
            JsonNode month=json.readTree(mvc.perform(get("/v1/cosers/me/availability")
                .header("X-Demo-User","demo-coser-f").param("month",day.toString().substring(0,7)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            boolean occupied=false;
            for (JsonNode item : month.get("days"))
                if (day.toString().equals(item.get("date").asText())) occupied="occupied".equals(item.get("status").asText());
            org.junit.jupiter.api.Assertions.assertTrue(occupied, "跨日订单两天都应显示占档");
        }
    }
}
