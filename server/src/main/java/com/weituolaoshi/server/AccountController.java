package com.weituolaoshi.server;

import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1")
public class AccountController {
    private final JdbcTemplate db;
    private final RestTemplate http = new RestTemplate();
    @Value("${wechat.app-id:}") private String appId;
    @Value("${wechat.app-secret:}") private String appSecret;
    @Value("${account.avatar-directory:./data/avatars}") private String avatarDirectory;
    public AccountController(JdbcTemplate db) { this.db = db; }

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
        List<String> users = db.queryForList("SELECT user_id FROM user_session WHERE token_hash = ? AND expires_at > CURRENT_TIMESTAMP", String.class, hash(token));
        if (users.isEmpty()) throw new ApiException(HttpStatus.UNAUTHORIZED, "登录已过期");
        return users.get(0);
    }

    private Map<String, Object> profile(String id) {
        return db.queryForMap("SELECT id, nickname, avatar_url AS \"avatarUrl\" FROM app_user WHERE id = ?", id);
    }

    @PostMapping("/auth/wechat-login")
    @Transactional
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        Object code = body.get("code");
        if (!(code instanceof String) || ((String) code).isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "code不能为空");
        if (appId.isBlank() || appSecret.isBlank()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "微信登录尚未配置");
        Map<?, ?> result;
        try {
            String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + java.net.URLEncoder.encode(appId, "UTF-8") +
                "&secret=" + java.net.URLEncoder.encode(appSecret, "UTF-8") + "&js_code=" +
                java.net.URLEncoder.encode((String) code, "UTF-8") + "&grant_type=authorization_code";
            result = http.getForObject(url, Map.class);
        } catch (Exception ex) { throw new ApiException(HttpStatus.BAD_GATEWAY, "微信登录服务暂不可用"); }
        String openid = result == null ? null : (String) result.get("openid");
        if (openid == null || openid.isBlank()) throw new ApiException(HttpStatus.UNAUTHORIZED, "微信登录凭证无效");
        List<String> users = db.queryForList("SELECT id FROM app_user WHERE wechat_openid = ?", String.class, openid);
        String id = users.isEmpty() ? "usr-" + UUID.randomUUID() : users.get(0);
        if (users.isEmpty()) db.update("INSERT INTO app_user(id, wechat_openid, nickname, role) VALUES (?, ?, '微信用户', 'customer')", id, openid);
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        db.update("INSERT INTO user_session(token_hash, user_id, expires_at) VALUES (?, ?, ?)", hash(token), id,
            java.sql.Timestamp.valueOf(LocalDateTime.now().plusDays(30)));
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
        db.update("UPDATE app_user SET nickname = ? WHERE id = ?", ((String) value).trim(), id);
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
