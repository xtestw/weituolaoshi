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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:public-homepage;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "account.avatar-directory=${java.io.tmpdir}/weituo-homepage-test-images"})
@ActiveProfiles("local") @AutoConfigureMockMvc
class PublicHomepageTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate db; @Autowired AuthSupport auth;
    String user(String role) {
        String id="home-"+UUID.randomUUID();
        db.update("INSERT INTO app_user(id,nickname,role,phone,bio) VALUES(?,?,?,?,?)",id,"主页测试",role,"13800138000","我的公开简介");
        return id;
    }
    String token(String id) { String value=UUID.randomUUID().toString(); db.update("INSERT INTO user_session(token_hash,user_id,expires_at) VALUES(?,?,?)",auth.hash(value),id,Timestamp.valueOf(LocalDateTime.now().plusHours(1))); return "Bearer "+value; }
    String create(String bearer,String state) throws Exception {
        return json.readTree(mvc.perform(post("/v1/posts").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"日常\",\"body\":\"今天的记录\",\"status\":\""+state+"\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText();
    }
    @Test void publicAllowlistDraftIsolationPaginationAndOwnership() throws Exception {
        String id=user("coser"), owner=token(id), other=token(user("customer"));
        String draft=create(owner,"draft"), published=create(owner,"published");
        create(owner,"published");
        mvc.perform(get("/v1/users/"+id+"/profile")).andExpect(status().isOk())
            .andExpect(jsonPath("$.bio").value("我的公开简介")).andExpect(jsonPath("$.postCount").value(2))
            .andExpect(jsonPath("$.phone").doesNotExist()).andExpect(jsonPath("$.wechat_openid").doesNotExist()).andExpect(jsonPath("$.role").doesNotExist());
        JsonNode first=json.readTree(mvc.perform(get("/v1/posts/by-user/"+id+"?limit=1")).andExpect(status().isOk()).andExpect(jsonPath("$.hasMore").value(true)).andReturn().getResponse().getContentAsString());
        JsonNode second=json.readTree(mvc.perform(get("/v1/posts/by-user/"+id+"?limit=1&offset=1")).andExpect(status().isOk()).andExpect(jsonPath("$.hasMore").value(false)).andReturn().getResponse().getContentAsString());
        assertNotEquals(first.get("items").get(0).get("id"),second.get("items").get(0).get("id"));
        mvc.perform(get("/v1/posts/by-user/"+id).header("Authorization",owner)).andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[?(@.id=='"+draft+"')]").doesNotExist());
        mvc.perform(get("/v1/posts/by-user/"+id+"?limit=51")).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/posts/"+draft)).andExpect(status().isNotFound());
        mvc.perform(put("/v1/posts/"+published).header("Authorization",other).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"篡改\",\"body\":\"内容\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/v1/posts/"+published).header("Authorization",other)).andExpect(status().isNotFound());
        mvc.perform(post("/v1/posts/"+published+"/likes").header("Authorization",other)).andExpect(status().isOk());
        mvc.perform(get("/v1/posts/by-user/"+id).header("Authorization",other)).andExpect(jsonPath("$.items[?(@.id=='"+published+"')].liked").value(true));
        mvc.perform(delete("/v1/posts/"+published).header("Authorization",owner)).andExpect(status().isOk());
        mvc.perform(get("/v1/users/"+id+"/profile")).andExpect(jsonPath("$.postCount").value(1));
        mvc.perform(get("/v1/posts/by-user/"+id)).andExpect(jsonPath("$.items.length()").value(1));
    }
    @Test void coverBelongsToAuthenticatedUserAndIsPublic() throws Exception {
        String id=user("customer"), owner=token(id), otherId=user("customer");
        MockMultipartFile file=new MockMultipartFile("file","cover.png","image/png",new byte[]{(byte)137,80,78,71,13,10,26,10,0});
        mvc.perform(multipart("/v1/users/me/cover").file(file)).andExpect(status().isUnauthorized());
        JsonNode updated=json.readTree(mvc.perform(multipart("/v1/users/me/cover").file(file).header("Authorization",owner)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String url=updated.get("coverUrl").asText();
        mvc.perform(get("/v1/users/"+id+"/profile")).andExpect(jsonPath("$.coverUrl").value(url));
        mvc.perform(get("/v1/users/"+otherId+"/profile")).andExpect(jsonPath("$.coverUrl").doesNotExist());
        mvc.perform(get(url)).andExpect(status().isOk());
        mvc.perform(multipart("/v1/users/me/cover").file(new MockMultipartFile("file","bad.png","image/png",new byte[]{1,2,3})).header("Authorization",owner)).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/users/no-such-user/profile")).andExpect(status().isNotFound());
        db.update("UPDATE app_user SET account_status='frozen' WHERE id=?",id);
        mvc.perform(get("/v1/users/"+id+"/profile")).andExpect(status().isNotFound());
        mvc.perform(get("/v1/posts/by-user/"+id)).andExpect(status().isNotFound());
    }
}
