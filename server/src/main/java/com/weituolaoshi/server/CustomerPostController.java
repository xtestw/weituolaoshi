package com.weituolaoshi.server;

import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Community posts are intentionally independent of bookable Coser services. */
@RestController
@RequestMapping("/v1/posts")
public class CustomerPostController {
    private final JdbcTemplate db;
    private final AuthSupport auth;
    @Value("${posts.media-directory:./data/posts}") private String postMediaDirectory;
    public CustomerPostController(JdbcTemplate db, AuthSupport auth) { this.db=db; this.auth=auth; }

    private String text(Map<String,Object> body, String key, int max) {
        String value=body.get(key) instanceof String ? ((String)body.get(key)).trim() : "";
        if (value.isEmpty() || value.length()>max) throw new ApiException(HttpStatus.BAD_REQUEST,key+"长度不合法");
        return value;
    }
    private String optionalUser(String bearer) {
        if (bearer==null || !bearer.startsWith("Bearer ") || bearer.length()<=7) return null;
        try { return auth.user(bearer); } catch (ApiException ex) { return null; }
    }
    private String cityValue(Map<String,Object> body) {
        String city=body.get("city") instanceof String ? ((String)body.get("city")).trim() : "";
        if(city.length()>80) throw new ApiException(HttpStatus.BAD_REQUEST,"城市长度不合法");
        return city;
    }
    private void requirePublished(String id) {
        Integer count=db.queryForObject("SELECT COUNT(*) FROM customer_post p JOIN app_user u ON u.id=p.author_id WHERE p.id=? AND p.status='published' AND p.deleted_at IS NULL AND u.account_status='active'",Integer.class,id);
        if(count==null || count==0) throw new ApiException(HttpStatus.NOT_FOUND,"帖子不存在");
    }
    private void requireOwner(String id, String user) {
        Integer count=db.queryForObject("SELECT COUNT(*) FROM customer_post WHERE id=? AND author_id=? AND deleted_at IS NULL",Integer.class,id,user);
        if(count==null || count==0) throw new ApiException(HttpStatus.NOT_FOUND,"帖子不存在");
    }
    private List<Map<String,Object>> mediaRows(String postId) {
        return db.queryForList("SELECT id,media_url AS url FROM customer_post_media WHERE post_id=? AND deleted_at IS NULL ORDER BY sort_order,created_at",postId);
    }
    private void attachEngagement(List<Map<String,Object>> rows, String viewer) {
        for (Map<String,Object> row: rows) {
            String id=(String)row.get("id");
            Integer likes=db.queryForObject("SELECT COUNT(*) FROM customer_post_like WHERE post_id=?",Integer.class,id);
            Integer comments=db.queryForObject("SELECT COUNT(*) FROM customer_post_comment WHERE post_id=? AND deleted_at IS NULL",Integer.class,id);
            row.put("likeCount", likes==null?0:likes);
            row.put("commentCount", comments==null?0:comments);
            row.put("images", mediaRows(id));
            if (viewer==null) row.put("liked", false);
            else {
                Integer liked=db.queryForObject("SELECT COUNT(*) FROM customer_post_like WHERE post_id=? AND user_id=?",Integer.class,id,viewer);
                row.put("liked", liked!=null && liked>0);
            }
        }
    }
    private List<Map<String,Object>> select(String where, Object... args) {
        return db.queryForList("SELECT p.id,p.title,p.body,p.city,p.status,p.created_at AS \"createdAt\",p.updated_at AS \"updatedAt\", " +
            "u.nickname AS author,u.avatar_url AS \"avatarUrl\",p.author_id AS \"authorId\" FROM customer_post p JOIN app_user u ON u.id=p.author_id WHERE " + where, args);
    }
    @GetMapping
    public List<Map<String,Object>> feed(@RequestParam(defaultValue="") String q, @RequestParam(defaultValue="") String city,
            @RequestHeader(value="Authorization",required=false) String bearer) {
        String pattern="%"+q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")+"%";
        List<Map<String,Object>> rows=select("p.status='published' AND p.deleted_at IS NULL AND u.account_status='active' AND " +
            "(LOWER(p.title) LIKE ? ESCAPE '\\' OR LOWER(p.body) LIKE ? ESCAPE '\\' OR LOWER(u.nickname) LIKE ? ESCAPE '\\') " +
            "AND (?='' OR p.city=?) ORDER BY p.created_at DESC LIMIT 100",pattern,pattern,pattern,city,city);
        attachEngagement(rows, optionalUser(bearer));
        return rows;
    }
    @GetMapping("/by-user/{userId}")
    public Map<String,Object> timeline(@PathVariable String userId,
            @RequestParam(defaultValue="0") int offset, @RequestParam(defaultValue="20") int limit,
            @RequestHeader(value="Authorization",required=false) String bearer) {
        if (offset < 0 || limit < 1 || limit > 50) throw new ApiException(HttpStatus.BAD_REQUEST,"分页参数不合法");
        Integer exists = db.queryForObject("SELECT COUNT(*) FROM app_user WHERE id=? AND account_status='active'", Integer.class, userId);
        if (exists == null || exists == 0) throw new ApiException(HttpStatus.NOT_FOUND,"主页不存在或暂不可访问");
        List<Map<String,Object>> rows = select("p.author_id=? AND p.status='published' AND p.deleted_at IS NULL AND u.account_status='active' ORDER BY p.created_at DESC,p.id DESC LIMIT ? OFFSET ?", userId, limit + 1, offset);
        boolean hasMore = rows.size() > limit;
        if (hasMore) rows = new ArrayList<>(rows.subList(0, limit));
        attachEngagement(rows, optionalUser(bearer));
        return Map.of("items", rows, "hasMore", hasMore);
    }

    @GetMapping("/mine")
    public List<Map<String,Object>> mine(@RequestHeader("Authorization") String bearer) {
        String user=auth.user(bearer);
        List<Map<String,Object>> rows=select("p.author_id=? AND p.deleted_at IS NULL ORDER BY p.created_at DESC",user);
        attachEngagement(rows, user);
        return rows;
    }
    @GetMapping("/{id}")
    public Map<String,Object> detail(@PathVariable String id, @RequestHeader(value="Authorization",required=false) String bearer) {
        List<Map<String,Object>> rows=select("p.id=? AND p.status='published' AND p.deleted_at IS NULL AND u.account_status='active'",id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"帖子不存在");
        attachEngagement(rows, optionalUser(bearer));
        return rows.get(0);
    }
    @PostMapping
    @Transactional
    public Map<String,Object> create(@RequestHeader("Authorization") String bearer,@RequestBody Map<String,Object> body) {
        String user=auth.user(bearer), id="post-"+UUID.randomUUID();
        String status="draft".equals(body.get("status")) ? "draft" : "published";
        String title=text(body,"title",200), content=text(body,"body",5000);
        String city=cityValue(body);
        db.update("INSERT INTO customer_post(id,author_id,title,body,city,status) VALUES(?,?,?,?,?,?)",id,user,title,content,city,status);
        List<Map<String,Object>> rows=select("p.id=?",id);
        attachEngagement(rows, user);
        return rows.get(0);
    }
    @PutMapping("/{id}")
    @Transactional
    public Map<String,Object> update(@RequestHeader("Authorization") String bearer,@PathVariable String id,@RequestBody Map<String,Object> body) {
        String user=auth.user(bearer);
        String status="draft".equals(body.get("status")) ? "draft" : "published";
        String city=cityValue(body);
        int changed=db.update("UPDATE customer_post SET title=?,body=?,city=?,status=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND author_id=? AND deleted_at IS NULL",
            text(body,"title",200),text(body,"body",5000),city,status,id,user);
        if(changed==0) throw new ApiException(HttpStatus.NOT_FOUND,"帖子不存在");
        List<Map<String,Object>> rows=select("p.id=?",id);
        attachEngagement(rows, user);
        return rows.get(0);
    }
    @DeleteMapping("/{id}")
    public Map<String,Object> delete(@RequestHeader("Authorization") String bearer,@PathVariable String id) {
        if(db.update("UPDATE customer_post SET deleted_at=CURRENT_TIMESTAMP WHERE id=? AND author_id=? AND deleted_at IS NULL",id,auth.user(bearer))==0)
            throw new ApiException(HttpStatus.NOT_FOUND,"帖子不存在");
        db.update("UPDATE customer_post_media SET deleted_at=CURRENT_TIMESTAMP WHERE post_id=? AND deleted_at IS NULL",id);
        return Map.of("deleted",true);
    }

    @PostMapping(value="/{id}/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public Map<String,Object> uploadImage(@RequestHeader("Authorization") String bearer,@PathVariable String id,@RequestPart("file") MultipartFile file) {
        String user=auth.user(bearer);
        requireOwner(id,user);
        if(file.isEmpty() || file.getSize()>5_000_000) throw new ApiException(HttpStatus.BAD_REQUEST,"图片不得超过5MB");
        String mime=file.getContentType();
        if(!List.of("image/jpeg","image/png","image/webp").contains(mime)) throw new ApiException(HttpStatus.BAD_REQUEST,"仅支持JPEG、PNG或WebP");
        try {
            byte[] bytes=file.getBytes();
            boolean jpeg=bytes.length>3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255;
            boolean png=bytes.length>8&&(bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71;
            boolean webp=bytes.length>12&&new String(bytes,0,4).equals("RIFF")&&new String(bytes,8,4).equals("WEBP");
            if(!(mime.equals("image/jpeg")&&jpeg||mime.equals("image/png")&&png||mime.equals("image/webp")&&webp)) throw new ApiException(HttpStatus.BAD_REQUEST,"图片内容无效");
            Integer count=db.queryForObject("SELECT COUNT(*) FROM customer_post_media WHERE post_id=? AND deleted_at IS NULL",Integer.class,id);
            if(count!=null && count>=9) throw new ApiException(HttpStatus.BAD_REQUEST,"最多上传9张图片");
            Integer maxOrder=db.queryForObject("SELECT COALESCE(MAX(sort_order),-1) FROM customer_post_media WHERE post_id=?",Integer.class,id);
            String mediaId="cpm-"+UUID.randomUUID();
            String name=UUID.randomUUID()+(mime.equals("image/png")?".png":mime.equals("image/webp")?".webp":".jpg");
            Path dir=Paths.get(postMediaDirectory).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Files.write(dir.resolve(name),bytes,StandardOpenOption.CREATE_NEW);
            String url="/v1/post-images/"+name;
            int order=(maxOrder==null?-1:maxOrder)+1;
            db.update("INSERT INTO customer_post_media(id,post_id,media_url,object_key,sort_order) VALUES(?,?,?,?,?)",mediaId,id,url,name,order);
            return Map.of("id",mediaId,"url",url);
        } catch(ApiException ex) { throw ex; }
        catch(Exception ex) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"图片保存失败"); }
    }

    @DeleteMapping("/{id}/images/{mediaId}")
    @Transactional
    public Map<String,Object> deleteImage(@RequestHeader("Authorization") String bearer,@PathVariable String id,@PathVariable String mediaId) {
        requireOwner(id,auth.user(bearer));
        if(db.update("UPDATE customer_post_media SET deleted_at=CURRENT_TIMESTAMP WHERE id=? AND post_id=? AND deleted_at IS NULL",mediaId,id)==0)
            throw new ApiException(HttpStatus.NOT_FOUND,"图片不存在");
        return Map.of("deleted",true);
    }

    @PostMapping("/{id}/likes")
    @Transactional
    public Map<String,Object> like(@RequestHeader("Authorization") String bearer,@PathVariable String id) {
        String user=auth.user(bearer);
        requirePublished(id);
        Integer exists=db.queryForObject("SELECT COUNT(*) FROM customer_post_like WHERE post_id=? AND user_id=?",Integer.class,id,user);
        if(exists==null || exists==0) db.update("INSERT INTO customer_post_like(post_id,user_id) VALUES(?,?)",id,user);
        List<Map<String,Object>> rows=select("p.id=?",id);
        attachEngagement(rows, user);
        return rows.get(0);
    }
    @DeleteMapping("/{id}/likes")
    @Transactional
    public Map<String,Object> unlike(@RequestHeader("Authorization") String bearer,@PathVariable String id) {
        String user=auth.user(bearer);
        requirePublished(id);
        db.update("DELETE FROM customer_post_like WHERE post_id=? AND user_id=?",id,user);
        List<Map<String,Object>> rows=select("p.id=?",id);
        attachEngagement(rows, user);
        return rows.get(0);
    }
    @GetMapping("/{id}/comments")
    public List<Map<String,Object>> comments(@PathVariable String id) {
        requirePublished(id);
        return db.queryForList("SELECT c.id,c.body,c.created_at AS \"createdAt\",c.author_id AS \"authorId\",u.nickname AS author,u.avatar_url AS \"avatarUrl\" " +
            "FROM customer_post_comment c JOIN app_user u ON u.id=c.author_id WHERE c.post_id=? AND c.deleted_at IS NULL AND u.account_status='active' ORDER BY c.created_at ASC",id);
    }
    @PostMapping("/{id}/comments")
    @Transactional
    public Map<String,Object> createComment(@RequestHeader("Authorization") String bearer,@PathVariable String id,@RequestBody Map<String,Object> body) {
        String user=auth.user(bearer);
        requirePublished(id);
        String content=text(body,"body",1000);
        String commentId="cmt-"+UUID.randomUUID();
        db.update("INSERT INTO customer_post_comment(id,post_id,author_id,body) VALUES(?,?,?,?)",commentId,id,user,content);
        return db.queryForList("SELECT c.id,c.body,c.created_at AS \"createdAt\",c.author_id AS \"authorId\",u.nickname AS author,u.avatar_url AS \"avatarUrl\" " +
            "FROM customer_post_comment c JOIN app_user u ON u.id=c.author_id WHERE c.id=?",commentId).get(0);
    }
    @DeleteMapping("/{id}/comments/{commentId}")
    public Map<String,Object> deleteComment(@RequestHeader("Authorization") String bearer,@PathVariable String id,@PathVariable String commentId) {
        String user=auth.user(bearer);
        requirePublished(id);
        if(db.update("UPDATE customer_post_comment SET deleted_at=CURRENT_TIMESTAMP WHERE id=? AND post_id=? AND author_id=? AND deleted_at IS NULL",commentId,id,user)==0)
            throw new ApiException(HttpStatus.NOT_FOUND,"评论不存在");
        return Map.of("deleted",true);
    }
}
