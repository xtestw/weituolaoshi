package com.weituolaoshi.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:booking-flow;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class BookingFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    private JsonNode parse(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test void createsHeldAppointmentAndRejectsOverlappingOrder() throws Exception {
        String token=parse(mvc.perform(post("/v1/dev/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andReturn()).get("token").asText();
        mvc.perform(get("/v1/services")).andExpect(status().isOk());
        String date=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(3).toString();
        db.update("INSERT INTO availability_slot(id,coser_id,start_at,end_at,status) VALUES (?,'demo-coser-f',?,?,'open')",
            "slot-"+UUID.randomUUID(),java.sql.Timestamp.valueOf(date+" 10:00:00"),java.sql.Timestamp.valueOf(date+" 22:00:00"));
        String quoteBody="{\"serviceId\":\"svc-1\",\"startAt\":\""+date+"T14:00:00\",\"durationHours\":3}";
        JsonNode quote=parse(mvc.perform(post("/v1/orders/quote").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(quoteBody)).andExpect(status().isOk()).andReturn());
        org.junit.jupiter.api.Assertions.assertEquals(56400,quote.get("payableAmount").asInt());
        String body="{\"quoteId\":\""+quote.get("quoteId").asText()+"\",\"venueId\":\"venue-demo-expo\"}";
        JsonNode order=parse(mvc.perform(post("/v1/orders").header("Authorization","Bearer "+token)
            .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn());
        org.junit.jupiter.api.Assertions.assertEquals("awaiting_payment",order.get("status").asText());
        mvc.perform(post("/v1/orders/quote").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(quoteBody))
            .andExpect(status().isConflict());
    }

    @Test void unpaidOrderCanBeCancelledAndReleasesAppointment() throws Exception {
        String token=parse(mvc.perform(post("/v1/dev/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andReturn()).get("token").asText();
        String date=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(4).toString();
        db.update("INSERT INTO availability_slot(id,coser_id,start_at,end_at,status) VALUES (?,'demo-coser-f',?,?,'open')",
            "slot-"+UUID.randomUUID(),java.sql.Timestamp.valueOf(date+" 10:00:00"),java.sql.Timestamp.valueOf(date+" 22:00:00"));
        String quoteBody="{\"serviceId\":\"svc-1\",\"startAt\":\""+date+"T14:00:00\",\"durationHours\":2}";
        JsonNode quote=parse(mvc.perform(post("/v1/orders/quote").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(quoteBody)).andExpect(status().isOk()).andReturn());
        String body="{\"quoteId\":\""+quote.get("quoteId").asText()+"\",\"venueId\":\"venue-demo-expo\"}";
        JsonNode order=parse(mvc.perform(post("/v1/orders").header("Authorization","Bearer "+token)
            .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn());
        String id=order.get("id").asText();
        mvc.perform(post("/v1/orders/"+id+"/cancel-requests").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("cancelled"));
        org.junit.jupiter.api.Assertions.assertEquals("cancelled",db.queryForObject(
            "SELECT status FROM order_appointment WHERE order_id=?",String.class,id));
        mvc.perform(post("/v1/orders/"+id+"/cancel-requests").header("Authorization","Bearer "+token))
            .andExpect(status().isConflict());
    }

    @Test void localPaymentToCompletionAndReview() throws Exception {
        String customer=parse(mvc.perform(post("/v1/dev/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andReturn()).get("token").asText();
        String coser=parse(mvc.perform(post("/v1/dev/login").contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"coser\"}"))
            .andExpect(status().isOk()).andReturn()).get("token").asText();
        String date=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(5).toString();
        db.update("INSERT INTO availability_slot(id,coser_id,start_at,end_at,status) VALUES (?,'demo-coser-f',?,?,'open')",
            "slot-"+UUID.randomUUID(),java.sql.Timestamp.valueOf(date+" 10:00:00"),java.sql.Timestamp.valueOf(date+" 22:00:00"));
        JsonNode quote=parse(mvc.perform(post("/v1/orders/quote").header("Authorization","Bearer "+customer)
            .contentType(MediaType.APPLICATION_JSON).content("{\"serviceId\":\"svc-1\",\"startAt\":\""+date+"T14:00:00\",\"durationHours\":2}"))
            .andExpect(status().isOk()).andReturn());
        JsonNode order=parse(mvc.perform(post("/v1/orders").header("Authorization","Bearer "+customer)
            .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"quoteId\":\""+quote.get("quoteId").asText()+"\",\"venueId\":\"venue-demo-expo\"}"))
            .andExpect(status().isOk()).andReturn());
        String id=order.get("id").asText();
        mvc.perform(post("/v1/dev/orders/"+id+"/pay").header("Authorization","Bearer "+customer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("awaiting_coser"));
        mvc.perform(post("/v1/orders/"+id+"/confirm").header("Authorization","Bearer "+customer))
            .andExpect(status().isForbidden());
        mvc.perform(post("/v1/orders/"+id+"/confirm").header("Authorization","Bearer "+coser))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("confirmed"));
        String code=parse(mvc.perform(get("/v1/orders/"+id).header("Authorization","Bearer "+customer))
            .andExpect(status().isOk()).andReturn()).get("checkInCode").asText();
        mvc.perform(multipart("/v1/orders/"+id+"/check-in")
            .file(new MockMultipartFile("photo","scene.jpg","image/jpeg",new byte[]{(byte)0xff,(byte)0xd8,1}))
            .param("code",code).param("latitude","31.2").param("longitude","121.4")
            .header("Authorization","Bearer "+coser))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("in_service"));
        mvc.perform(post("/v1/orders/"+id+"/complete-confirmations").header("Authorization","Bearer "+customer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("in_service"));
        mvc.perform(post("/v1/orders/"+id+"/complete-confirmations").header("Authorization","Bearer "+coser))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("completed"));
        mvc.perform(post("/v1/orders/"+id+"/reviews").header("Authorization","Bearer "+customer)
            .contentType(MediaType.APPLICATION_JSON).content("{\"score\":5}"))
            .andExpect(status().isOk());
    }

    @Test void maleUserCannotQuoteEvenIfBothPartiesCouldBeSameGender() throws Exception {
        String user="demo-male-"+UUID.randomUUID();
        db.update("INSERT INTO app_user(id,nickname,role) VALUES (?,?,'customer')",user,"测试用户");
        db.update("INSERT INTO identity_verification(id,user_id,legal_gender,adult,status) VALUES (?,?,'M',TRUE,'verified')",
            "iv-"+UUID.randomUUID(),user);
        String date=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(3).toString();
        mvc.perform(post("/v1/orders/quote").header("X-Demo-User",user)
            .contentType(MediaType.APPLICATION_JSON).content("{\"serviceId\":\"svc-1\",\"startAt\":\""+date+"T14:00:00\",\"durationHours\":3}"))
            .andExpect(status().isForbidden());
    }

    @Test void shortNoticeNeedsMatchingCoserConfirmation() throws Exception {
        String token=parse(mvc.perform(post("/v1/dev/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andReturn()).get("token").asText();
        LocalDateTime start=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(3).withMinute(0).withSecond(0).withNano(0);
        while(start.getHour()<10 || start.getHour()>19) start=start.plusHours(1);
        db.update("INSERT INTO availability_slot(id,coser_id,start_at,end_at,status) VALUES (?,'demo-coser-f',?,?,'open')",
            "slot-"+UUID.randomUUID(),java.sql.Timestamp.valueOf(start.toLocalDate().atTime(10,0)),
            java.sql.Timestamp.valueOf(start.toLocalDate().atTime(22,0)));
        LocalDateTime end=start.plusHours(2);
        String body="{\"serviceId\":\"svc-1\",\"startAt\":\""+start+":00\",\"durationHours\":2}";
        mvc.perform(post("/v1/orders/quote").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        String conversation="conv-"+UUID.randomUUID();
        db.update("INSERT INTO conversation(id,customer_id,coser_id,service_id) VALUES (?,'demo-customer','demo-coser-f','svc-1')",conversation);
        db.update("INSERT INTO time_confirmation(id,conversation_id,coser_id,start_at,end_at,expires_at) VALUES (?,?,'demo-coser-f',?,?,?)",
            "tc-"+UUID.randomUUID(),conversation,java.sql.Timestamp.valueOf(start),java.sql.Timestamp.valueOf(end),
            java.sql.Timestamp.valueOf(LocalDateTime.now().plusMinutes(30)));
        mvc.perform(post("/v1/orders/quote").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }
}
