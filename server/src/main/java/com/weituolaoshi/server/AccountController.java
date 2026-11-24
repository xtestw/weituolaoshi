package com.weituolaoshi.server;

import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/v1")
public class AccountController {
    private static final Logger log = LoggerFactory.getLogger(AccountController.class);
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final RestTemplate http = new RestTemplate();
    @Value("${wechat.app-id:}") private String appId;
    @Value("${wechat.app-secret:}") private String appSecret;
    @Value("${account.avatar-directory:./data/avatars}") private String avatarDirectory;
    public AccountController(JdbcTemplate db, ObjectMapper json) { this.db = db; this.json = json; }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : digest) result.append(String.format("%02x", b & 0xff));
            return result.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private String user(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        String token = authorization.substring(7);
        if (token.isBlank()) throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        List<String> users = db.queryForList("SELECT s.user_id FROM user_session s JOIN app_user u ON u.id=s.user_id WHERE s.token_hash = ? AND s.expires_at > CURRENT_TIMESTAMP AND u.account_status='active'", String.class, hash(token));
        if (users.isEmpty()) throw new ApiException(HttpStatus.UNAUTHORIZED, "登录已过期");
        return users.get(0);
    }

    private Map<String, Object> profile(String id) {
        return db.queryForMap("SELECT id, nickname, avatar_url AS \"avatarUrl\", phone, bio, role FROM app_user WHERE id = ?", id);
    }

    @PostMapping("/auth/wechat-login")
    @Transactional
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String attemptId = UUID.randomUUID().toString();
        Object code = body.get("code");
        if (!(code instanceof String) || ((String) code).isBlank()) {
            log.warn("WeChat login rejected: attemptId={}, reason=missing_code", attemptId);
            throw new ApiException(HttpStatus.BAD_REQUEST, "code不能为空");
        }
        if (appId.isBlank() || appSecret.isBlank()) {
            log.error("WeChat login rejected: attemptId={}, reason=missing_server_config", attemptId);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "微信登录尚未配置");
        }
        log.info("WeChat login started: attemptId={}", attemptId);
        Map<?, ?> result;
        try {
            String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + java.net.URLEncoder.encode(appId, "UTF-8") +
                "&secret=" + java.net.URLEncoder.encode(appSecret, "UTF-8") + "&js_code=" +
                java.net.URLEncoder.encode((String) code, "UTF-8") + "&grant_type=authorization_code";
            String response = http.getForObject(url, String.class);
            result = json.readValue(response, Map.class);
        } catch (Exception ex) {
            log.error("WeChat code exchange failed: attemptId={}, exception={}", attemptId, ex.getClass().getSimpleName());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "微信登录服务暂不可用");
        }
        String openid = result == null ? null : (String) result.get("openid");
        if (openid == null || openid.isBlank()) {
            Object errorCode = result == null ? null : result.get("errcode");
            String errorMessage = result == null || result.get("errmsg") == null ? "" : String.valueOf(result.get("errmsg"));
            java.util.regex.Matcher rid = java.util.regex.Pattern.compile("(?:^|[,\\s])rid:\\s*([a-zA-Z0-9-]+)").matcher(errorMessage);
            log.warn("WeChat code exchange rejected: attemptId={}, errcode={}, rid={}",
                attemptId, errorCode, rid.find() ? rid.group(1) : "unavailable");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "微信登录凭证无效");
        }
        log.info("WeChat code exchange succeeded: attemptId={}", attemptId);
        List<String> users = db.queryForList("SELECT id FROM app_user WHERE wechat_openid = ?", String.class, openid);
        String id = users.isEmpty() ? "usr-" + UUID.randomUUID() : users.get(0);
        if (users.isEmpty()) db.update("INSERT INTO app_user(id, wechat_openid, identity_provider, identity_subject, nickname, role, last_login_at) VALUES (?, ?, 'wechat', ?, '微信用户', 'customer', CURRENT_TIMESTAMP)", id, openid, openid);
        else {
            String status = db.queryForObject("SELECT account_status FROM app_user WHERE id=?", String.class, id);
            if (!"active".equals(status)) throw new ApiException(HttpStatus.FORBIDDEN, "账号不可登录");
            db.update("UPDATE app_user SET identity_subject=COALESCE(identity_subject,?),last_login_at=CURRENT_TIMESTAMP WHERE id=?", openid, id);
        }
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        db.update("INSERT INTO user_session(token_hash, user_id, expires_at) VALUES (?, ?, ?)", hash(token), id,
            java.sql.Timestamp.valueOf(LocalDateTime.now().plusDays(30)));
        log.info("WeChat login succeeded: attemptId={}, accountCreated={}", attemptId, users.isEmpty());
        return Map.of("token", token, "user", profile(id));
    }

    @GetMapping("/users/me")
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String auth) {
        return profile(user(auth));
    }

    @PostMapping("/auth/logout")
    public Map<String, Object> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        user(auth);
        db.update("DELETE FROM user_session WHERE token_hash = ?", hash(auth.substring(7)));
        return Map.of("ok", true);
    }

    @PatchMapping("/users/me")
    public Map<String, Object> update(@RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body) {
        String id = user(auth);
        Object value = body.get("nickname");
        if (!(value instanceof String) || ((String) value).trim().isEmpty() || ((String) value).trim().length() > 30)
            throw new ApiException(HttpStatus.BAD_REQUEST, "昵称长度须为1至30字");
        Object phoneValue = body.get("phone");
        Object bioValue = body.get("bio");
        if (phoneValue != null && !(phoneValue instanceof String) || bioValue != null && !(bioValue instanceof String))
            throw new ApiException(HttpStatus.BAD_REQUEST, "个人资料格式不正确");
        Map<String, Object> existing = profile(id);
        String phone = body.containsKey("phone") ? (phoneValue == null ? null : ((String) phoneValue).trim()) : (String) existing.get("phone");
        String bio = body.containsKey("bio") ? (bioValue == null ? null : ((String) bioValue).trim()) : (String) existing.get("bio");
        if (phone != null && !phone.isEmpty() && !phone.matches("1[3-9]\\d{9}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "请输入有效的11位手机号");
        if (bio != null && bio.length() > 500)
            throw new ApiException(HttpStatus.BAD_REQUEST, "个人简介不能超过500字");
        db.update("UPDATE app_user SET nickname = ?, phone = ?, bio = ? WHERE id = ?", ((String) value).trim(), phone == null || phone.isEmpty() ? null : phone, bio == null || bio.isEmpty() ? null : bio, id);
        return profile(id);
    }

    @PostMapping(value = "/users/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> avatar(@RequestHeader(value = "Authorization", required = false) String auth,
            @RequestPart("file") MultipartFile file) {
        String id = user(auth);
        if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024) throw new ApiException(HttpStatus.BAD_REQUEST, "头像不得超过2MB");
        String type = file.getContentType();
        String extension;
        if ("image/jpeg".equals(type)) extension = ".jpg";
        else if ("image/png".equals(type)) extension = ".png";
        else if ("image/webp".equals(type)) extension = ".webp";
        else throw new ApiException(HttpStatus.BAD_REQUEST, "仅支持JPEG、PNG或WebP图片");
        try {
            byte[] bytes = file.getBytes();
            boolean jpeg = bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
            boolean png = bytes.length >= 8 && (bytes[0] & 255) == 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71;
            boolean webp = bytes.length >= 12 && new String(bytes, 0, 4).equals("RIFF") && new String(bytes, 8, 4).equals("WEBP");
            if (!("image/jpeg".equals(type) && jpeg || "image/png".equals(type) && png || "image/webp".equals(type) && webp))
                throw new ApiException(HttpStatus.BAD_REQUEST, "图片内容与格式不符");
            Path directory = Paths.get(avatarDirectory).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            String name = UUID.randomUUID() + extension;
            Files.write(directory.resolve(name), bytes, StandardOpenOption.CREATE_NEW);
            db.update("UPDATE app_user SET avatar_url = ? WHERE id = ?", "/v1/avatars/" + name, id);
            return profile(id);
        } catch (ApiException ex) { throw ex; }
        catch (Exception ex) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "头像保存失败"); }
    }

    @GetMapping("/avatars/{name:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> image(@PathVariable String name) {
        if (!name.matches("[0-9a-f-]{36}\\.(jpg|png|webp)")) throw new ApiException(HttpStatus.NOT_FOUND, "头像不存在");
        Path path = Paths.get(avatarDirectory).toAbsolutePath().normalize().resolve(name);
        if (!Files.isRegularFile(path)) throw new ApiException(HttpStatus.NOT_FOUND, "头像不存在");
        String mime = name.endsWith(".png") ? "image/png" : name.endsWith(".webp") ? "image/webp" : "image/jpeg";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime)).body(new org.springframework.core.io.FileSystemResource(path));
    }
}
