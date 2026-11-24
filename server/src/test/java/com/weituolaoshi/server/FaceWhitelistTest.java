package com.weituolaoshi.server;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:face-whitelist;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "identity.digest-key=test-key", "identity.gender-check-whitelist=", "faceid.secret-id=", "faceid.secret-key=", "faceid.rule-id="})
@AutoConfigureMockMvc
class FaceWhitelistTest {
    static String id(String prefix) {
        int[] weights={7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2}; int sum=0;
        for(int i=0;i<17;i++) sum+=(prefix.charAt(i)-'0')*weights[i];
        return prefix+"10X98765432".charAt(sum%11);
    }
    static final String FEMALE=id("11010119900101002"), MALE=id("11010119900101001"), MINOR=id("11010120200101002"), OTHER=id("11010119900101004");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("identity.face-check-whitelist",()->FEMALE+","+MALE+","+MINOR);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired AuthSupport auth;
    @Autowired IdentityGenderPolicy genderPolicy;
    @Autowired ObjectMapper json;
    String session() {
        String user=UUID.randomUUID().toString(), token=UUID.randomUUID().toString();
        db.update("INSERT INTO app_user(id,nickname) VALUES (?,'测试用户')",user);
        db.update("INSERT INTO user_session(token_hash,user_id,expires_at) VALUES (?,?,DATEADD('DAY',1,CURRENT_TIMESTAMP))",auth.hash(token),user);
        return "Bearer "+token;
    }
    String body(String number) throws Exception {
        return json.writeValueAsString(Map.of("name","测试人员","idNumber",number,"mode","coser","agreed",true));
    }
    @Test void testIdentityWorksWithoutProviderAndKeepsBindingProtection() throws Exception {
        String session=session();
        mvc.perform(post("/v1/identity/verifications").header("Authorization",session).contentType(MediaType.APPLICATION_JSON).content(body(FEMALE)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.faceBypassed").value(true)).andExpect(jsonPath("$.status").value("verified")).andExpect(jsonPath("$.gender").value("F"));
        mvc.perform(get("/v1/users/me").header("Authorization",session)).andExpect(jsonPath("$.user").doesNotExist()).andExpect(jsonPath("$.role").value("coser"));
        mvc.perform(post("/v1/identity/face/start").header("Authorization",session).contentType(MediaType.APPLICATION_JSON).content(body(FEMALE)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.faceBypassed").value(true)).andExpect(jsonPath("$.bizToken").doesNotExist());
        mvc.perform(post("/v1/identity/verifications").header("Authorization",session()).contentType(MediaType.APPLICATION_JSON).content(body(FEMALE))).andExpect(status().isConflict());
    }
    @Test void bypassDoesNotSkipLoginGenderAdultOrWhitelistChecks() throws Exception {
        mvc.perform(post("/v1/identity/verifications").contentType(MediaType.APPLICATION_JSON).content(body(FEMALE))).andExpect(status().isUnauthorized());
        for(String number:List.of(MALE,MINOR,OTHER)) {
            mvc.perform(post("/v1/identity/verifications").header("Authorization",session()).contentType(MediaType.APPLICATION_JSON).content(body(number))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/v1/identity/verifications").header("Authorization",session()).contentType(MediaType.APPLICATION_JSON).content(body("110101199001010020"))).andExpect(status().isBadRequest());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM face_verification_attempt",Integer.class));
    }
    @Test void consentAndRealProviderRemainRequiredOutsideTestPath() throws Exception {
        String authorization=session();
        String withoutConsent=json.writeValueAsString(Map.of("name","测试人员","idNumber",FEMALE,"mode","coser","agreed",false));
        for (String endpoint:List.of("/v1/identity/verifications","/v1/identity/face/start")) {
            mvc.perform(post(endpoint).header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON).content(withoutConsent))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/v1/identity/face/start").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON).content(body(OTHER)))
            .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/v1/identity/me").header("Authorization",authorization))
            .andExpect(jsonPath("$.status").value("unverified"));
    }

    String adminSession(String role) {
        String id=UUID.randomUUID().toString(), token=UUID.randomUUID().toString();
        db.update("INSERT INTO admin_user(id,username,password_hash) VALUES (?,?,?)",id,id,"unused");
        db.update("INSERT INTO admin_user_role(user_id,role_id) VALUES (?,?)",id,role);
        db.update("INSERT INTO admin_session(token_hash,user_id,expires_at) VALUES (?,?,DATEADD('HOUR',1,CURRENT_TIMESTAMP))",auth.hash(token),id);
        return "Bearer "+token;
    }
    @Test void adminWhitelistIsPermissionedMaskedAndImmediate() throws Exception {
        String admin=adminSession("super_admin"), user=session();
        mvc.perform(get("/admin/api/identity-whitelist")).andExpect(status().isUnauthorized());
        String payload=json.writeValueAsString(Map.of("idNumber",OTHER));
        mvc.perform(post("/admin/api/identity-whitelist").header("Authorization",adminSession("viewer")).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
        String response=mvc.perform(post("/admin/api/identity-whitelist").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains(OTHER));
        String id=json.readTree(response).get("id").asText();
        mvc.perform(post("/admin/api/identity-whitelist").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isConflict());
        String listing=mvc.perform(get("/admin/api/identity-whitelist").header("Authorization",admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(listing.contains(OTHER)); assertFalse(listing.contains("document_digest"));
        mvc.perform(post("/v1/identity/verifications").header("Authorization",user).contentType(MediaType.APPLICATION_JSON).content(body(OTHER))).andExpect(status().isOk());
        mvc.perform(delete("/admin/api/identity-whitelist/"+id).header("Authorization",adminSession("reviewer"))).andExpect(status().isForbidden());
        mvc.perform(delete("/admin/api/identity-whitelist/"+id).header("Authorization",admin)).andExpect(status().isOk());
        mvc.perform(post("/v1/identity/verifications").header("Authorization",user).contentType(MediaType.APPLICATION_JSON).content(body(OTHER))).andExpect(status().isForbidden());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM admin_audit WHERE target_id=?",Integer.class,id));
    }

    @Test void managedWhitelistBypassesGenderAndRevocationIsImmediate() throws Exception {
        String admin=adminSession("super_admin"), user=session();
        String response=mvc.perform(post("/admin/api/identity-whitelist").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("idNumber",MALE)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id=json.readTree(response).get("id").asText();
        try {
            mvc.perform(post("/v1/identity/verifications").header("Authorization",user).contentType(MediaType.APPLICATION_JSON).content(body(MALE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.gender").value("M"));
            assertTrue(genderPolicy.allowsNumber("M",MALE));
            String digest=auth.hash("test-key:"+MALE);
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM identity_verification v WHERE v.document_digest=? AND v.adult=TRUE AND v.status='verified' AND "+genderPolicy.sql("v."),Integer.class,digest));
        } finally {
            mvc.perform(delete("/admin/api/identity-whitelist/"+id).header("Authorization",admin)).andExpect(status().isOk());
        }
        assertFalse(genderPolicy.allowsNumber("M",MALE));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM identity_verification v WHERE v.document_digest=? AND "+genderPolicy.sql("v."),Integer.class,auth.hash("test-key:"+MALE)));
    }

}
