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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:coser-workbench;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class CoserWorkbenchTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    private JsonNode parse(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String login(String role) throws Exception {
        String body = "coser".equals(role) ? "{\"role\":\"coser\"}" : "{}";
        return parse(mvc.perform(post("/v1/dev/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn()).get("token").asText();
    }

    @Test void dashboardReputationSettlementAndSupportTicket() throws Exception {
        String customer = login("customer");
        String coser = login("coser");
        String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(6).toString();
        db.update("INSERT INTO availability_slot(id,coser_id,start_at,end_at,status) VALUES (?,'demo-coser-f',?,?,'open')",
            "slot-"+UUID.randomUUID(), java.sql.Timestamp.valueOf(date+" 10:00:00"), java.sql.Timestamp.valueOf(date+" 22:00:00"));

        JsonNode quote = parse(mvc.perform(post("/v1/orders/quote").header("Authorization","Bearer "+customer)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"serviceId\":\"svc-1\",\"startAt\":\""+date+"T14:00:00\",\"durationHours\":2}"))
            .andExpect(status().isOk()).andReturn());
        JsonNode order = parse(mvc.perform(post("/v1/orders").header("Authorization","Bearer "+customer)
            .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"quoteId\":\""+quote.get("quoteId").asText()+"\",\"venueId\":\"venue-demo-expo\"}"))
            .andExpect(status().isOk()).andReturn());
        String id = order.get("id").asText();
        mvc.perform(post("/v1/dev/orders/"+id+"/pay").header("Authorization","Bearer "+customer))
            .andExpect(status().isOk());

        JsonNode dashboard = parse(mvc.perform(get("/v1/coser/dashboard").header("Authorization","Bearer "+coser))
            .andExpect(status().isOk()).andReturn());
        org.junit.jupiter.api.Assertions.assertEquals(1, dashboard.get("pendingConfirmCount").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(id, dashboard.get("spotlight").get("id").asText());
        org.junit.jupiter.api.Assertions.assertEquals("verified", dashboard.get("verificationStatus").asText());
        org.junit.jupiter.api.Assertions.assertFalse(dashboard.get("displayName").asText().isBlank());

        mvc.perform(post("/v1/orders/"+id+"/confirm").header("Authorization","Bearer "+coser))
            .andExpect(status().isOk());
        String code = parse(mvc.perform(get("/v1/orders/"+id).header("Authorization","Bearer "+customer))
            .andExpect(status().isOk()).andReturn()).get("checkInCode").asText();
        mvc.perform(multipart("/v1/orders/"+id+"/check-in")
            .file(new MockMultipartFile("photo","scene.jpg","image/jpeg", new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,(byte)0xd9}))
            .param("code", code).param("latitude","31.2").param("longitude","121.5")
            .header("Authorization","Bearer "+coser)).andExpect(status().isOk());
        mvc.perform(post("/v1/orders/"+id+"/complete-confirmations").header("Authorization","Bearer "+customer))
            .andExpect(status().isOk());
        mvc.perform(post("/v1/orders/"+id+"/complete-confirmations").header("Authorization","Bearer "+coser))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("completed"));

        mvc.perform(post("/v1/orders/"+id+"/reviews").header("Authorization","Bearer "+customer)
            .contentType(MediaType.APPLICATION_JSON).content("{\"score\":5,\"body\":\"很好\"}"))
            .andExpect(status().isOk());

        JsonNode reputation = parse(mvc.perform(get("/v1/cosers/me/reputation").header("Authorization","Bearer "+coser))
            .andExpect(status().isOk()).andReturn());
        org.junit.jupiter.api.Assertions.assertEquals(1, reputation.get("scoredOrders").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(5.0, reputation.get("averageScore").asDouble());

        JsonNode summary = parse(mvc.perform(get("/v1/cosers/me/settlements/summary").header("Authorization","Bearer "+coser))
            .andExpect(status().isOk()).andReturn());
        org.junit.jupiter.api.Assertions.assertTrue(summary.get("pendingAmount").asLong() > 0);
        org.junit.jupiter.api.Assertions.assertEquals(1, summary.get("monthOrderCount").asInt());

        mvc.perform(get("/v1/cosers/me/settlements").header("Authorization","Bearer "+coser))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].orderId").value(id));

        mvc.perform(post("/v1/support/tickets").header("Authorization","Bearer "+coser)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"category\":\"地点存在安全风险\",\"subject\":\"履约异常\",\"body\":\"地点存在安全风险\",\"orderId\":\""+id+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("open"));

        mvc.perform(post("/v1/orders/"+id+"/exceptions").header("Authorization","Bearer "+coser)
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"用户未到场\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/v1/coser/exceptions").header("Authorization","Bearer "+coser))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].reason").value("用户未到场"));
    }
}
