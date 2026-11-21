package com.weituolaoshi.server;
import com.fasterxml.jackson.databind.*; import java.sql.*; import java.time.*; import java.util.*;
import org.junit.jupiter.api.Test; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.http.MediaType; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.test.context.ActiveProfiles; import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*; import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*; import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:message-flow;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE") @ActiveProfiles("local") @AutoConfigureMockMvc
class MessageControllerTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate db; @Autowired AuthSupport auth;
 String token(String user) { String t=UUID.randomUUID().toString()+UUID.randomUUID(); db.update("INSERT INTO user_session(token_hash,user_id,expires_at) VALUES (?,?,?)",auth.hash(t),user,Timestamp.valueOf(LocalDateTime.now().plusHours(1))); return "Bearer "+t; }
 JsonNode body(org.springframework.test.web.servlet.MvcResult r) throws Exception { return json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)); }
 @Test void deliveryModerationAndPermissions() throws Exception {
  String customer=token("demo-customer"), coser=token("demo-coser-f");
  String id=body(mvc.perform(post("/v1/messages/conversations").header("Authorization",customer).contentType(MediaType.APPLICATION_JSON).content("{\"serviceId\":\"svc-1\"}")).andExpect(status().isOk()).andReturn()).get("id").asText();
  mvc.perform(get("/v1/messages/conversations/"+id+"/messages")).andExpect(status().isUnauthorized());
  String payload="{\"clientId\":\"client-12345678\",\"body\":\"你好\"}";
  JsonNode first=body(mvc.perform(post("/v1/messages/conversations/"+id+"/messages").header("Authorization",customer).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk()).andReturn());
  JsonNode retry=body(mvc.perform(post("/v1/messages/conversations/"+id+"/messages").header("Authorization",customer).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk()).andReturn()); assertEquals(first.get("id"),retry.get("id")); assertTrue(first.get("createdAt").isTextual());
  assertEquals(1,body(mvc.perform(get("/v1/messages/conversations").header("Authorization",coser)).andExpect(status().isOk()).andReturn()).get(0).get("unread").asInt());
  mvc.perform(post("/v1/messages/conversations/"+id+"/read").header("Authorization",coser)).andExpect(status().isOk());
  assertEquals(0,body(mvc.perform(get("/v1/messages/conversations").header("Authorization",coser)).andExpect(status().isOk()).andReturn()).get(0).get("unread").asInt());
  JsonNode risky=body(mvc.perform(post("/v1/messages/conversations/"+id+"/messages").header("Authorization",customer).contentType(MediaType.APPLICATION_JSON).content("{\"clientId\":\"client-87654321\",\"body\":\"加微信私下交易\"}")).andExpect(status().isOk()).andReturn()); assertEquals("pending",risky.get("status").asText());
  assertEquals(1,body(mvc.perform(get("/v1/messages/conversations/"+id+"/messages").header("Authorization",coser)).andExpect(status().isOk()).andReturn()).size());
  String adminId="admin-message-test",adminToken=UUID.randomUUID().toString()+UUID.randomUUID(); db.update("INSERT INTO admin_user(id,username,password_hash) VALUES (?,'message-test','x')",adminId); db.update("INSERT INTO admin_user_role(user_id,role_id) VALUES (?,'super_admin')",adminId); db.update("INSERT INTO admin_session(token_hash,user_id,expires_at) VALUES (?,?,?)",auth.hash(adminToken),adminId,Timestamp.valueOf(LocalDateTime.now().plusHours(1)));
  mvc.perform(patch("/admin/api/messages/"+risky.get("id").asText()).header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"approved\"}")).andExpect(status().isOk());
  assertEquals(2,body(mvc.perform(get("/v1/messages/conversations/"+id+"/messages").header("Authorization",coser)).andExpect(status().isOk()).andReturn()).size());
  mvc.perform(patch("/admin/api/conversations/"+id+"/restriction").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"restricted\":true,\"reason\":\"test\"}")).andExpect(status().isOk());
  mvc.perform(post("/v1/messages/conversations/"+id+"/messages").header("Authorization",customer).contentType(MediaType.APPLICATION_JSON).content("{\"clientId\":\"client-11111111\",\"body\":\"hello\"}")).andExpect(status().isForbidden());
 }
 @Test void officialMessageReachUserAndAllowReply() throws Exception {
  String customer=token("demo-customer");
  String adminId="admin-official-test",adminToken=UUID.randomUUID().toString()+UUID.randomUUID();
  db.update("INSERT INTO admin_user(id,username,password_hash) VALUES (?,'official-test','x')",adminId);
  db.update("INSERT INTO admin_user_role(user_id,role_id) VALUES (?,'super_admin')",adminId);
  db.update("INSERT INTO admin_session(token_hash,user_id,expires_at) VALUES (?,?,?)",auth.hash(adminToken),adminId,Timestamp.valueOf(LocalDateTime.now().plusHours(1)));
  mvc.perform(post("/admin/api/official-messages").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"demo-customer\",\"body\":\"平台通知\"}")).andExpect(status().isUnauthorized());
  JsonNode sent=body(mvc.perform(post("/admin/api/official-messages").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"demo-customer\",\"body\":\"平台通知\"}")).andExpect(status().isOk()).andReturn());
  assertTrue(sent.get("conversationId").asText().startsWith("conv-"));
  JsonNode list=body(mvc.perform(get("/v1/messages/conversations").header("Authorization",customer)).andExpect(status().isOk()).andReturn());
  JsonNode official=null;
  for(JsonNode row:list) if("official".equals(row.path("kind").asText())) official=row;
  assertNotNull(official);
  assertEquals("委托助手",official.get("name").asText());
  assertEquals("平台通知",official.get("preview").asText());
  assertEquals(1,official.get("unread").asInt());
  String conversationId=official.get("id").asText();
  mvc.perform(post("/v1/messages/conversations/"+conversationId+"/messages").header("Authorization",customer).contentType(MediaType.APPLICATION_JSON).content("{\"clientId\":\"client-official1\",\"body\":\"收到，谢谢\"}")).andExpect(status().isOk());
  assertEquals(2,body(mvc.perform(get("/v1/messages/conversations/"+conversationId+"/messages").header("Authorization",customer)).andExpect(status().isOk()).andReturn()).size());
  JsonNode recent=body(mvc.perform(get("/admin/api/official-messages").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andReturn());
  assertTrue(recent.size()>=1);
  assertEquals("demo-customer",recent.get(0).get("userId").asText());
 }
}
