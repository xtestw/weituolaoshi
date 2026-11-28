package com.weituolaoshi.server;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:feedback;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
class FeedbackTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired AuthSupport auth;
    @Autowired ObjectMapper json;
    String user(String id) {
        String token=UUID.randomUUID().toString();
        db.update("INSERT INTO user_session(token_hash,user_id,expires_at) VALUES(?,?,DATEADD('HOUR',1,CURRENT_TIMESTAMP))",auth.hash(token),id);
        return "Bearer "+token;
    }
    String admin(String role) {
        String id="adm-"+UUID.randomUUID(),token=UUID.randomUUID().toString();
        db.update("INSERT INTO admin_user(id,username,password_hash) VALUES(?,?,?)",id,id,"unused");
        db.update("INSERT INTO admin_user_role VALUES(?,?)",id,role);
        db.update("INSERT INTO admin_session(token_hash,user_id,expires_at) VALUES(?,?,DATEADD('HOUR',1,CURRENT_TIMESTAMP))",auth.hash(token),id);
        return "Bearer "+token;
    }
    byte[] png() { return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII="); }
    String upload(String token) throws Exception {
        return json.readTree(mvc.perform(multipart("/v1/feedback/images").file(new MockMultipartFile("file","screen.png","image/png",png())).header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText();
    }
    String payload(List<String> ids) throws Exception { return json.writeValueAsString(Map.of("body","首页图片显示异常\n请帮忙查看","contact","test@example.com","requestId",UUID.randomUUID().toString(),"imageIds",ids)); }

    @Test void savesFeedbackAndPrivateScreenshotsWithIdempotentRetry() throws Exception {
        String token=user("demo-customer"), admin=admin("super_admin"), image=upload(token), payload=payload(List.of(image));
        String id=json.readTree(mvc.perform(post("/v1/feedback").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/v1/feedback").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM user_feedback WHERE id=?",Integer.class,id));
        assertEquals(id,db.queryForObject("SELECT feedback_id FROM feedback_image WHERE id=?",String.class,image));
        mvc.perform(get("/admin/api/feedback").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.items[?(@.id=='"+id+"')].contact").value("test@example.com"));
        mvc.perform(get("/admin/api/feedback/images/"+image).header("Authorization",admin)).andExpect(status().isOk()).andExpect(content().bytes(png())).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/admin/api/feedback/images/"+image)).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/api/feedback/images/"+image).header("Authorization",admin("viewer"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/api/feedback").header("Authorization",token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/api/feedback").header("Authorization",admin("viewer"))).andExpect(status().isForbidden());
    }
    @Test void rejectsInvalidOrForeignAttachmentsAndRollsBackEntireSubmission() throws Exception {
        String token=user("demo-customer"), other=user("demo-coser-f"), own=upload(token), foreign=upload(other);
        int before=db.queryForObject("SELECT COUNT(*) FROM user_feedback",Integer.class);
        mvc.perform(post("/v1/feedback").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(payload(List.of(own,foreign)))).andExpect(status().isBadRequest());
        assertEquals(before,db.queryForObject("SELECT COUNT(*) FROM user_feedback",Integer.class));
        assertNull(db.queryForObject("SELECT feedback_id FROM feedback_image WHERE id=?",String.class,own));
        mvc.perform(post("/v1/feedback").contentType(MediaType.APPLICATION_JSON).content(payload(List.of()))).andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/feedback").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(payload(List.of(own,own)))).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/feedback").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(payload(List.of("a","b","c","d")))).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/feedback").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"  \"}")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/v1/feedback/images").file(new MockMultipartFile("file","bad.png","image/png","not an image".getBytes())).header("Authorization",token)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/v1/feedback/images").file(new MockMultipartFile("file","huge.png","image/png",new byte[5*1024*1024+1])).header("Authorization",token)).andExpect(status().isBadRequest());
        mvc.perform(delete("/v1/feedback/images/"+own).header("Authorization",other)).andExpect(status().isOk());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM feedback_image WHERE id=?",Integer.class,own));
        mvc.perform(delete("/v1/feedback/images/"+own).header("Authorization",token)).andExpect(status().isOk());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM feedback_image WHERE id=?",Integer.class,own));
    }
    @Test void textOnlyAndPagination() throws Exception {
        String token=user("demo-customer"), admin=admin("super_admin");
        for (int i=0;i<21;i++) mvc.perform(post("/v1/feedback").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(payload(List.of()))).andExpect(status().isOk());
        mvc.perform(get("/admin/api/feedback?page=1").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(20));
        mvc.perform(get("/admin/api/feedback?page=2").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").exists());
        mvc.perform(get("/admin/api/feedback?page=0").header("Authorization",admin)).andExpect(status().isBadRequest());
    }
}
