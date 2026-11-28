package com.weituolaoshi.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:wechat_phone;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class WechatPhoneTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountController account;

    @Test void bindsOnlyPhoneReturnedByWechat() throws Exception {
        ReflectionTestUtils.setField(account, "appId", "test-app");
        ReflectionTestUtils.setField(account, "appSecret", "test-secret");
        RestTemplate http = (RestTemplate) ReflectionTestUtils.getField(account, "http");
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/cgi-bin/token?")))
            .andRespond(withSuccess("{\"access_token\":\"test-token\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/wxa/business/getuserphonenumber?access_token=test-token")))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andRespond(withSuccess("{\"errcode\":0,\"phone_info\":{\"purePhoneNumber\":\"13800138000\",\"countryCode\":\"86\"}}", MediaType.APPLICATION_JSON));
        JsonNode login = json.readTree(mvc.perform(post("/v1/dev/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String token = "Bearer " + login.get("token").asText();
        mvc.perform(post("/v1/users/me/wechat-phone").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"one-time-code\",\"phone\":\"19999999999\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("13800138000"))
            .andExpect(jsonPath("$.phoneVerified").value(true));
        mvc.perform(patch("/v1/users/me").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"测试用户\",\"bio\":\"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.phoneVerified").value(true));
        server.verify();
    }
}
