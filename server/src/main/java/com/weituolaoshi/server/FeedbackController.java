package com.weituolaoshi.server;

import java.util.*;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FeedbackController {
    private final JdbcTemplate db;
    private final AuthSupport auth;
    public FeedbackController(JdbcTemplate db, AuthSupport auth) { this.db = db; this.auth = auth; }

    @PostMapping("/v1/feedback/images")
    @Transactional
    public Map<String, Object> upload(@RequestHeader(value="Authorization", required=false) String header,
                                      @RequestParam("file") MultipartFile file) throws IOException {
        String user = auth.user(header);
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw bad("截图大小须在5MB以内");
        byte[] bytes = file.getBytes();
        String mime;
        if (bytes.length >= 8 && bytes[0] == (byte)137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71 && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10) mime = "image/png";
        else if (bytes.length >= 3 && bytes[0] == (byte)255 && bytes[1] == (byte)216 && bytes[2] == (byte)255) mime = "image/jpeg";
        else if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') mime = "image/webp";
        else throw bad("仅支持PNG、JPEG或WebP截图");
        // Serialize upload limits for this account and expire abandoned uploads.
        db.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE", String.class, user);
        db.update("DELETE FROM feedback_image WHERE user_id=? AND feedback_id IS NULL AND created_at < DATEADD('DAY',-1,CURRENT_TIMESTAMP)", user);
        if (db.queryForObject("SELECT COUNT(*) FROM feedback_image WHERE user_id=? AND feedback_id IS NULL", Integer.class, user) >= 12)
            throw bad("待提交截图过多，请移除不用的截图后重试");
        String id = "fbi-" + UUID.randomUUID();
        db.update("INSERT INTO feedback_image(id,user_id,mime_type,image_data) VALUES(?,?,?,?)", id,user,mime,bytes);
        return Map.of("id",id);
    }

    @DeleteMapping("/v1/feedback/images/{id}")
    public Map<String,Object> remove(@RequestHeader(value="Authorization",required=false) String header, @PathVariable String id) {
        db.update("DELETE FROM feedback_image WHERE id=? AND user_id=? AND feedback_id IS NULL", id,auth.user(header));
        return Map.of("ok",true);
    }

    @PostMapping("/v1/feedback")
    @Transactional
    public Map<String,Object> submit(@RequestHeader(value="Authorization",required=false) String header, @RequestBody Map<String,Object> body) {
        String user = auth.user(header);
        String content = field(body,"body"), contact = field(body,"contact"), requestId = field(body,"requestId");
        if (content.isBlank() || content.length() > 2000) throw bad("请填写1至2000字的反馈内容");
        if (contact.length() > 100) throw bad("联系方式不能超过100字");
        if (!requestId.matches("[A-Za-z0-9-]{16,64}")) throw bad("提交标识无效，请重新打开反馈页面");
        Object raw = body.getOrDefault("imageIds", Collections.emptyList());
        if (!(raw instanceof List)) throw bad("截图参数无效");
        List<?> ids = (List<?>)raw;
        if (ids.size() > 3 || new HashSet<>(ids).size() != ids.size() || ids.stream().anyMatch(id -> !(id instanceof String))) throw bad("最多添加3张不同的截图");
        db.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE", String.class,user);
        String id = "fb-" + auth.hash(user + ":" + requestId).substring(0, 48);
        if (!db.queryForList("SELECT id FROM user_feedback WHERE id=?", String.class,id).isEmpty()) return Map.of("id",id);
        db.update("INSERT INTO user_feedback(id,user_id,body,contact) VALUES(?,?,?,?)", id,user,content,contact);
        for (int i=0; i<ids.size(); i++) {
            if (db.update("UPDATE feedback_image SET feedback_id=?,sort_order=? WHERE id=? AND user_id=? AND feedback_id IS NULL", id,i,ids.get(i),user) != 1)
                throw bad("截图已失效，请移除后重新添加");
        }
        return Map.of("id",id);
    }

    @GetMapping("/admin/api/feedback")
    public Map<String,Object> list(@RequestHeader(value="Authorization",required=false) String header,
                                   @RequestParam(defaultValue="1") int page) {
        auth.require(auth.admin(header),"feedback:read");
        if (page < 1 || page > 1000000) throw bad("页码无效");
        List<Map<String,Object>> rows = db.queryForList("SELECT f.id,f.user_id AS \"userId\",u.nickname,f.body,f.contact,f.created_at AS \"createdAt\" FROM user_feedback f JOIN app_user u ON u.id=f.user_id ORDER BY f.created_at DESC,f.id DESC LIMIT 20 OFFSET ?", (page-1)*20);
        for (Map<String,Object> row : rows) row.put("images", db.queryForList("SELECT id FROM feedback_image WHERE feedback_id=? ORDER BY sort_order", String.class,row.get("id")));
        return Map.of("items",rows,"total",db.queryForObject("SELECT COUNT(*) FROM user_feedback",Long.class));
    }

    @GetMapping("/admin/api/feedback/images/{id}")
    public ResponseEntity<byte[]> image(@RequestHeader(value="Authorization",required=false) String header, @PathVariable String id) {
        auth.require(auth.admin(header),"feedback:read");
        List<Map<String,Object>> rows = db.queryForList("SELECT mime_type,image_data FROM feedback_image WHERE id=? AND feedback_id IS NOT NULL",id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"截图不存在");
        Map<String,Object> row = rows.get(0);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .contentType(MediaType.parseMediaType((String)row.get("mime_type"))).body((byte[])row.get("image_data"));
    }
    private String field(Map<String,Object> body, String key) {
        Object value = body.getOrDefault(key,"");
        if (!(value instanceof String)) throw bad("反馈参数无效");
        return ((String)value).trim();
    }
    private ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST,message); }
}
