package com.weituolaoshi.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:post-flow;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("local") @AutoConfigureMockMvc
class PostFlowTest {
  @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate db; @Autowired AuthSupport auth;
  String token(String user) { String token=UUID.randomUUID().toString(); db.update("INSERT INTO user_session(token_hash,user_id,expires_at) VALUES(?,?,?)",auth.hash(token),user,Timestamp.valueOf(LocalDateTime.now().plusHours(1))); return "Bearer "+token; }
  JsonNode body(org.springframework.test.web.servlet.MvcResult r) throws Exception { return json.readTree(r.getResponse().getContentAsString()); }
  byte[] png() { return new byte[]{(byte)137,80,78,71,13,10,26,10,0}; }

  @Test void customerPostsPublishSearchAndStaySeparateFromServices() throws Exception {
    String customer=token("demo-customer"), coser=token("demo-coser-f");
    JsonNode created=body(mvc.perform(post("/v1/posts").header("Authorization",customer).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"测试漫展记录\",\"body\":\"今天很开心\",\"city\":\"上海\"}")).andExpect(status().isOk()).andReturn());
    String id=created.get("id").asText();
    mvc.perform(get("/v1/posts?q=漫展")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
    mvc.perform(get("/v1/posts/"+id)).andExpect(status().isOk());
    mvc.perform(get("/v1/services?q=测试漫展记录")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    mvc.perform(post("/v1/posts").header("Authorization",coser).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"不应发布\",\"body\":\"Coser内容\"}")).andExpect(status().isForbidden());
    mvc.perform(delete("/v1/posts/"+id).header("Authorization",coser)).andExpect(status().isNotFound());
    mvc.perform(delete("/v1/posts/"+id).header("Authorization",customer)).andExpect(status().isOk());
    mvc.perform(get("/v1/posts/"+id)).andExpect(status().isNotFound());
  }

  @Test void customerPostCanUploadAndServeImages() throws Exception {
    String customer=token("demo-customer");
    JsonNode created=body(mvc.perform(post("/v1/posts").header("Authorization",customer).contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"带图返图\",\"body\":\"现场照片\",\"city\":\"上海\"}")).andExpect(status().isOk()).andReturn());
    String id=created.get("id").asText();
    JsonNode uploaded=body(mvc.perform(multipart("/v1/posts/"+id+"/images").file(new MockMultipartFile("file","a.png","image/png",png())).header("Authorization",customer)).andExpect(status().isOk()).andReturn());
    assertTrue(uploaded.hasNonNull("id"));
    assertTrue(uploaded.get("url").asText().startsWith("/v1/post-images/"));
    mvc.perform(get("/v1/posts/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.images.length()").value(1));
    mvc.perform(get(uploaded.get("url").asText())).andExpect(status().isOk());
    mvc.perform(delete("/v1/posts/"+id+"/images/"+uploaded.get("id").asText()).header("Authorization",customer)).andExpect(status().isOk());
    mvc.perform(get("/v1/posts/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.images.length()").value(0));
  }

  @Test void customerPostCityUpdateFilterAndEngagement() throws Exception {
    String customer=token("demo-customer");
    JsonNode created=body(mvc.perform(post("/v1/posts").header("Authorization",customer).contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"同城活动\",\"body\":\"上海漫展见\",\"city\":\"上海\"}")).andExpect(status().isOk()).andReturn());
    String id=created.get("id").asText();
    mvc.perform(get("/v1/posts?city=上海")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='"+id+"')]").exists());
    mvc.perform(get("/v1/posts?city=北京")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='"+id+"')]").doesNotExist());
    mvc.perform(put("/v1/posts/"+id).header("Authorization",customer).contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"同城活动\",\"body\":\"改到北京\",\"city\":\"北京\",\"status\":\"published\"}")).andExpect(status().isOk())
        .andExpect(jsonPath("$.city").value("北京"));
    mvc.perform(get("/v1/posts?city=北京")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='"+id+"')]").exists());
    mvc.perform(post("/v1/posts/"+id+"/likes").header("Authorization",customer)).andExpect(status().isOk())
        .andExpect(jsonPath("$.liked").value(true)).andExpect(jsonPath("$.likeCount").value(1));
    mvc.perform(post("/v1/posts/"+id+"/likes").header("Authorization",customer)).andExpect(status().isOk())
        .andExpect(jsonPath("$.likeCount").value(1));
    JsonNode comment=body(mvc.perform(post("/v1/posts/"+id+"/comments").header("Authorization",customer).contentType(MediaType.APPLICATION_JSON)
        .content("{\"body\":\"我也去\"}")).andExpect(status().isOk()).andReturn());
    mvc.perform(get("/v1/posts/"+id+"/comments")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get("/v1/posts/"+id).header("Authorization",customer)).andExpect(status().isOk())
        .andExpect(jsonPath("$.commentCount").value(1)).andExpect(jsonPath("$.liked").value(true));
    mvc.perform(delete("/v1/posts/"+id+"/likes").header("Authorization",customer)).andExpect(status().isOk())
        .andExpect(jsonPath("$.liked").value(false)).andExpect(jsonPath("$.likeCount").value(0));
    mvc.perform(delete("/v1/posts/"+id+"/comments/"+comment.get("id").asText()).header("Authorization",customer)).andExpect(status().isOk());
    mvc.perform(get("/v1/posts/"+id+"/comments")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  }

  @Test void coserServiceIsDistributedOnlyAfterImageAndPublish() throws Exception {
    String coser=token("demo-coser-f");
    JsonNode created=body(mvc.perform(post("/v1/services").header("Authorization",coser).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"新角色服务\",\"description\":\"测试服务\",\"city\":\"上海\",\"place\":\"公共场所\",\"boundaries\":[\"不饮酒\"],\"questions\":[\"如何称呼\"],\"pricePerHour\":18800,\"minHours\":2}")).andExpect(status().isOk()).andReturn());
    String id=created.get("id").asText();
    mvc.perform(get("/v1/services?q=新角色服务")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    mvc.perform(post("/v1/services/"+id+"/publish").header("Authorization",coser)).andExpect(status().isConflict());
    JsonNode uploaded=body(mvc.perform(multipart("/v1/services/"+id+"/images").file(new MockMultipartFile("file","a.png","image/png",png())).header("Authorization",coser)).andExpect(status().isOk()).andReturn());
    assertTrue(uploaded.hasNonNull("id"));
    assertTrue(uploaded.hasNonNull("url"));
    mvc.perform(post("/v1/services/"+id+"/publish").header("Authorization",coser)).andExpect(status().isOk());
    mvc.perform(get("/v1/services?q=新角色服务")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
    mvc.perform(get("/v1/services/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.media.length()").value(1)).andExpect(jsonPath("$.boundaries[0]").value("不饮酒")).andExpect(jsonPath("$.questions[0]").value("如何称呼"));
    mvc.perform(get("/v1/posts?q=新角色服务")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    mvc.perform(delete("/v1/services/"+id).header("Authorization",coser)).andExpect(status().isOk());
    mvc.perform(get("/v1/services/"+id)).andExpect(status().isNotFound());
  }

  @Test void coserCanDeleteReorderAndLockServices() throws Exception {
    String coser=token("demo-coser-f");
    JsonNode created=body(mvc.perform(post("/v1/services").header("Authorization",coser).contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"媒体编辑服务\",\"description\":\"测试删图\",\"city\":\"上海\",\"place\":\"公共场所\",\"boundaries\":[\"不饮酒\"],\"questions\":[\"如何称呼\"],\"pricePerHour\":18800,\"minHours\":2}"))
        .andExpect(status().isOk()).andReturn());
    String id=created.get("id").asText();
    JsonNode first=body(mvc.perform(multipart("/v1/services/"+id+"/images").file(new MockMultipartFile("file","a.png","image/png",png())).header("Authorization",coser)).andExpect(status().isOk()).andReturn());
    JsonNode second=body(mvc.perform(multipart("/v1/services/"+id+"/images").file(new MockMultipartFile("file","b.png","image/png",png())).header("Authorization",coser)).andExpect(status().isOk()).andReturn());
    String firstId=first.get("id").asText(), secondId=second.get("id").asText();
    mvc.perform(put("/v1/services/"+id+"/images/layout").header("Authorization",coser).contentType(MediaType.APPLICATION_JSON)
        .content("{\"mediaIds\":[\""+secondId+"\",\""+firstId+"\"],\"coverMediaId\":\""+secondId+"\"}")).andExpect(status().isOk())
        .andExpect(jsonPath("$.coverUrl").value(second.get("url").asText()));
    mvc.perform(delete("/v1/services/"+id+"/images/"+firstId).header("Authorization",coser)).andExpect(status().isOk());
    JsonNode mine=body(mvc.perform(get("/v1/services/mine").header("Authorization",coser)).andExpect(status().isOk()).andReturn());
    JsonNode row=null;
    for (JsonNode item: mine) if (id.equals(item.get("id").asText())) row=item;
    assertNotNull(row);
    assertEquals(1, row.get("images").size());
    assertEquals(secondId, row.get("images").get(0).get("id").asText());
    assertFalse(row.get("locked").asBoolean());

    String quoteId="quote-"+UUID.randomUUID();
    Timestamp start=Timestamp.valueOf(LocalDateTime.now().plusDays(5).withHour(14).withMinute(0).withSecond(0).withNano(0));
    Timestamp end=Timestamp.valueOf(start.toLocalDateTime().plusHours(2));
    db.update("INSERT INTO price_quote(id,customer_id,service_id,start_at,end_at,service_amount,payable_amount,expires_at) VALUES(?,?,?,?,?,?,?,?)",
        quoteId,"demo-customer",id,start,end,37600L,37600L,Timestamp.valueOf(LocalDateTime.now().plusHours(1)));
    db.update("INSERT INTO booking_order(id,customer_id,coser_id,service_id,quote_id,venue_id,start_at,end_at,payable_amount,status) VALUES(?,?,?,?,?,?,?,?,?,?)",
        "ord-"+UUID.randomUUID(),"demo-customer","demo-coser-f",id,quoteId,"venue-demo-expo",start,end,37600L,"awaiting_payment");
    mine=body(mvc.perform(get("/v1/services/mine").header("Authorization",coser)).andExpect(status().isOk()).andReturn());
    for (JsonNode item: mine) if (id.equals(item.get("id").asText())) assertTrue(item.get("locked").asBoolean());
    mvc.perform(delete("/v1/services/"+id+"/images/"+secondId).header("Authorization",coser)).andExpect(status().isConflict());
    mvc.perform(delete("/v1/services/"+id).header("Authorization",coser)).andExpect(status().isConflict());
  }
}
