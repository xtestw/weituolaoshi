package com.weituolaoshi.server;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:admin-coser-post;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
class AdminCoserPostModerationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired AuthSupport auth;

    private String admin(String role) {
        String id="adm-"+UUID.randomUUID(),token=UUID.randomUUID().toString();
        db.update("INSERT INTO admin_user(id,username,password_hash) VALUES (?,?,?)",id,id,"unused");
        db.update("INSERT INTO admin_user_role(user_id,role_id) VALUES (?,?)",id,role);
        db.update("INSERT INTO admin_session(token_hash,user_id,expires_at) VALUES (?,?,DATEADD('HOUR',1,CURRENT_TIMESTAMP))",auth.hash(token),id);
        return "Bearer "+token;
    }

    @Test void onlyModeratorCanTakePublishedCoserPostOfflineAndRestoreIt() throws Exception {
        String superAdmin=admin("super_admin"),viewer=admin("viewer");
        mvc.perform(get("/admin/api/coser-posts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/api/coser-posts").header("Authorization",superAdmin))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='svc-1')].title").exists());
        mvc.perform(patch("/admin/api/coser-posts/svc-1/moderation").header("Authorization",viewer)
            .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"offline\",\"reason\":\"违规\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/admin/api/coser-posts/svc-1/moderation").header("Authorization",superAdmin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"offline\",\"reason\":\"违规内容\"}"))
            .andExpect(status().isOk());
        assertEquals("private",db.queryForObject("SELECT visibility FROM service_post WHERE id='svc-1'",String.class));
        mvc.perform(get("/v1/services/svc-1")).andExpect(status().isNotFound());
        mvc.perform(get("/v1/services")).andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='svc-1')]").isEmpty());
        db.update("UPDATE service_post SET status='published' WHERE id='svc-1'");
        mvc.perform(get("/v1/services/svc-1")).andExpect(status().isNotFound());
        mvc.perform(patch("/admin/api/coser-posts/svc-1/moderation").header("Authorization",superAdmin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"restore\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/v1/services/svc-1")).andExpect(status().isOk());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM admin_audit WHERE target_id='svc-1' AND action LIKE 'coser_post.%'",Integer.class));
    }
}
