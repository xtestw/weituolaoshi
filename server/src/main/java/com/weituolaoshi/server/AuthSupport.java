package com.weituolaoshi.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuthSupport {
    private final JdbcTemplate db;
    public AuthSupport(JdbcTemplate db) { this.db = db; }
    public String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format("%02x", b & 255));
            return result.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private String token(String header) {
        if (header == null || !header.startsWith("Bearer ") || header.substring(7).isBlank()) throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        return header.substring(7);
    }
    public String user(String header) {
        List<String> ids = db.queryForList("SELECT user_id FROM user_session WHERE token_hash=? AND expires_at>CURRENT_TIMESTAMP", String.class, hash(token(header)));
        if (ids.isEmpty()) throw new ApiException(HttpStatus.UNAUTHORIZED, "登录已过期");
        return ids.get(0);
    }
    public String admin(String header) {
        List<String> ids = db.queryForList("SELECT s.user_id FROM admin_session s JOIN admin_user u ON s.user_id=u.id WHERE s.token_hash=? AND s.expires_at>CURRENT_TIMESTAMP AND u.enabled=TRUE", String.class, hash(token(header)));
        if (ids.isEmpty()) throw new ApiException(HttpStatus.UNAUTHORIZED, "管理员登录已过期");
        return ids.get(0);
    }
    public void require(String admin, String permission) {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM admin_user_role ur JOIN admin_role_permission rp ON ur.role_id=rp.role_id WHERE ur.user_id=? AND rp.permission_code=?", Integer.class, admin, permission);
        if (count == null || count == 0) throw new ApiException(HttpStatus.FORBIDDEN, "权限不足");
    }
}
