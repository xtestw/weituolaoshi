package com.weituolaoshi.server;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.security.SecureRandom;
import java.nio.file.*;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class MarketplaceController {
    private final JdbcTemplate db;
    private final AuthSupport auth;
    private final IdentityGenderPolicy genderPolicy;
    private final AvailabilitySupport availabilitySupport;
    private final Environment environment;
    @Value("${orders.checkin-directory:./data/checkins}") private String checkinDirectory;
    @Value("${posts.media-directory:./data/posts}") private String postMediaDirectory;
    public MarketplaceController(JdbcTemplate db, AuthSupport auth, Environment environment, IdentityGenderPolicy genderPolicy, AvailabilitySupport availabilitySupport) { this.db = db; this.auth = auth; this.environment = environment; this.genderPolicy = genderPolicy; this.availabilitySupport = availabilitySupport; }

    private String required(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof String) || ((String) value).isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, key + "不能为空");
        return ((String) value).trim();
    }

    private int integer(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof Number) || ((Number) value).intValue() != ((Number) value).doubleValue())
            throw new ApiException(HttpStatus.BAD_REQUEST, key + "必须是整数");
        return ((Number) value).intValue();
    }

    private String actor(String bearer, String demo) {
        if (bearer != null && !bearer.isBlank()) return auth.user(bearer);
        if (Arrays.asList(environment.getActiveProfiles()).contains("local") && demo != null && demo.startsWith("demo-")) return demo;
        throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
    }

    private void role(String user, String expected) {
        List<String> roles = db.queryForList("SELECT role FROM app_user WHERE id=? AND account_status='active'", String.class, user);
        if (roles.isEmpty() || !expected.equals(roles.get(0))) throw new ApiException(HttpStatus.FORBIDDEN, "当前账号无权操作");
    }

    private Map<String, Object> service(String id) {
        List<Map<String, Object>> rows = db.queryForList("SELECT s.id, s.coser_id, c.display_name AS coser, s.title, " +
            "s.city, s.price_per_hour, s.price_per_day, s.pricing_unit, s.min_hours, s.status, s.audit_status, s.visibility, s.deleted_at, " +
            "c.verification_status, c.service_status FROM service_post s " +
            "JOIN coser_profile c ON c.user_id = s.coser_id WHERE s.id = ?", id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "服务不存在");
        return rows.get(0);
    }

    private void requireTradable(Map<String, Object> svc) {
        if (!"published".equals(svc.get("status")) || !"approved".equals(svc.get("audit_status")) ||
                !"public".equals(svc.get("visibility")) || svc.get("deleted_at") != null ||
                !"verified".equals(svc.get("verification_status")) || !"active".equals(svc.get("service_status")))
            throw new ApiException(HttpStatus.CONFLICT, "服务不可交易");
    }

    private void requireFemaleAdults(String customer, String coser) {
        Integer eligible = db.queryForObject("SELECT COUNT(*) FROM identity_verification WHERE user_id IN (?, ?) " +
            "AND status='verified' AND adult=TRUE AND " + genderPolicy.sql(""), Integer.class, customer, coser);
        if (eligible == null || eligible != 2)
            throw new ApiException(HttpStatus.FORBIDDEN, "仅已完成实名核验的成年女性可下单和接单");
    }

    private void requireFemaleAdult(String user) {
        Integer eligible = db.queryForObject("SELECT COUNT(*) FROM identity_verification WHERE user_id=? " +
            "AND status='verified' AND adult=TRUE AND " + genderPolicy.sql(""), Integer.class, user);
        if (eligible == null || eligible != 1)
            throw new ApiException(HttpStatus.FORBIDDEN, "仅已完成实名核验的成年女性可发布服务");
    }

    private void saveLines(String id, Object source, String table, int max) {
        if (!(source instanceof List)) return;
        List<?> lines=(List<?>)source;
        if(lines.size()>8) throw new ApiException(HttpStatus.BAD_REQUEST,"最多设置8条内容");
        db.update("DELETE FROM "+table+" WHERE service_id=?",id);
        int order=0;
        for(Object raw:lines) {
            Object value=raw instanceof Map ? ((Map<?,?>)raw).get("text") : raw;
            String line=value instanceof String ? ((String)value).trim() : "";
            if(line.isEmpty() || line.length()>max) throw new ApiException(HttpStatus.BAD_REQUEST,"条目长度不合法");
            db.update("INSERT INTO "+table+"(id,service_id,text,sort_order) VALUES(?,?,?,?)",UUID.randomUUID().toString(),id,line,order++);
        }
    }

    private void requireShortNoticeConfirmation(String customer, Map<String, Object> svc,
            Timestamp start, Timestamp end) {
        if (!start.toLocalDateTime().isBefore(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(24))) return;
        Integer confirmed = db.queryForObject("SELECT COUNT(*) FROM time_confirmation t " +
            "JOIN conversation c ON c.id=t.conversation_id WHERE c.customer_id=? AND c.coser_id=? " +
            "AND c.service_id=? AND t.coser_id=? AND t.start_at=? AND t.end_at=? " +
            "AND t.expires_at>CURRENT_TIMESTAMP", Integer.class, customer, svc.get("coser_id"),
            svc.get("id"), svc.get("coser_id"), start, end);
        if (confirmed == null || confirmed == 0)
            throw new ApiException(HttpStatus.CONFLICT, "24小时内预约须先在聊天中由Coser确认所选时间");
    }

    @GetMapping("/services")
    public List<Map<String, Object>> services(@RequestParam(defaultValue="") String q, @RequestParam(defaultValue="") String city) {
        String pattern="%"+q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")+"%";
        return db.queryForList("SELECT s.id, s.title, c.display_name AS coser, s.city, " +
            "s.price_per_hour AS \"pricePerHour\", s.price_per_day AS \"pricePerDay\", s.pricing_unit AS \"pricingUnit\", s.min_hours AS \"minHours\", s.max_hours AS \"maxHours\", " +
            "s.cover_url AS \"coverUrl\", s.ip_name_snapshot AS \"ipName\", s.character_name_snapshot AS \"characterName\", " +
            "s.service_mode AS \"serviceMode\", " +
            "CASE WHEN c.verification_status='verified' THEN TRUE ELSE FALSE END AS verified, " +
            "CASE WHEN EXISTS (SELECT 1 FROM availability_slot a WHERE a.coser_id=s.coser_id AND a.status='open' AND a.end_at>?) THEN TRUE ELSE FALSE END AS available FROM service_post s " +
            "JOIN coser_profile c ON c.user_id = s.coser_id JOIN app_user u ON u.id=s.coser_id " +
            "WHERE s.status = 'published' AND c.verification_status='verified' AND c.service_status='active' AND u.account_status='active' " +
            "AND EXISTS (SELECT 1 FROM identity_verification v WHERE v.user_id=s.coser_id " +
            "AND v.status='verified' AND v.adult=TRUE AND " + genderPolicy.sql("v.") + ") " +
            "AND s.content_kind = 'commission' AND s.visibility = 'public' AND s.audit_status = 'approved' " +
            "AND s.deleted_at IS NULL AND (?='' OR s.city=?) AND " +
            "(LOWER(s.title) LIKE ? ESCAPE '\\' OR LOWER(s.description) LIKE ? ESCAPE '\\' OR LOWER(c.display_name) LIKE ? ESCAPE '\\') " +
            "ORDER BY s.created_at DESC", Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))),city,city,pattern,pattern,pattern);
    }

    @GetMapping("/services/{id}")
    public Map<String, Object> serviceDetail(@PathVariable String id) {
        List<Map<String, Object>> rows = db.queryForList("SELECT s.id, s.title, s.description, s.city, s.price_per_hour AS \"pricePerHour\", " +
            "s.price_per_day AS \"pricePerDay\", s.pricing_unit AS \"pricingUnit\", s.min_hours AS \"minHours\", s.max_hours AS \"maxHours\", s.service_terms AS \"serviceTerms\", s.place_name AS \"place\", " +
            "s.cover_url AS \"coverUrl\", s.ip_name_snapshot AS \"ipName\", s.character_name_snapshot AS \"characterName\", " +
            "s.service_mode AS \"serviceMode\", c.display_name AS coser, " +
            "CASE WHEN c.verification_status='verified' THEN TRUE ELSE FALSE END AS verified FROM service_post s " +
            "JOIN coser_profile c ON c.user_id=s.coser_id JOIN app_user u ON u.id=s.coser_id " +
            "WHERE s.id=? AND s.status='published' AND c.service_status='active' AND u.account_status='active' " +
            "AND s.content_kind='commission' AND s.visibility='public' AND s.audit_status='approved' AND s.deleted_at IS NULL " +
            "AND EXISTS (SELECT 1 FROM identity_verification v WHERE v.user_id=s.coser_id " +
            "AND v.status='verified' AND v.adult=TRUE AND " + genderPolicy.sql("v.") + ")", id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "服务不存在");
        Map<String, Object> result = new HashMap<>(rows.get(0));
        result.put("boundaries", db.queryForList("SELECT text FROM service_boundary WHERE service_id=? ORDER BY sort_order", String.class, id));
        result.put("questions", db.queryForList("SELECT text FROM service_question WHERE service_id=? ORDER BY sort_order", String.class, id));
        result.put("media", db.queryForList("SELECT media_type AS \"type\",media_url AS \"url\",thumbnail_url AS \"thumbnailUrl\" " +
            "FROM service_media WHERE service_id=? AND deleted_at IS NULL AND processing_status='ready' AND audit_status='approved' ORDER BY sort_order", id));
        return result;
    }

    @PostMapping("/services")
    @Transactional
    public Map<String, Object> createService(@RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo,
            @RequestBody Map<String, Object> body) {
        String user=actor(bearer,demo);
        role(user, "coser");
        requireFemaleAdult(user);
        Integer certified = db.queryForObject("SELECT COUNT(*) FROM coser_profile WHERE user_id=? AND verification_status='verified' AND service_status='active'",Integer.class,user);
        if(certified==null||certified==0) throw new ApiException(HttpStatus.FORBIDDEN,"请先完成Coser认证");
        String pricingUnit = String.valueOf(body.getOrDefault("pricingUnit", "hour"));
        if (!List.of("hour", "day", "both").contains(pricingUnit)) throw new ApiException(HttpStatus.BAD_REQUEST, "计费单位不合法");
        int hourlyPrice = pricingUnit.equals("day") ? 1 : integer(body, "pricePerHour");
        Integer dailyPrice = pricingUnit.equals("hour") ? null : integer(body, "pricePerDay");
        int minHours = integer(body, "minHours");
        if (hourlyPrice <= 0 || (dailyPrice != null && dailyPrice <= 0) || minHours < 2 || minHours > 8)
            throw new ApiException(HttpStatus.BAD_REQUEST, "价格或最小时长不合法");
        String id = "svc-" + UUID.randomUUID();
        db.update("INSERT INTO service_post(id, coser_id, title, description, city, place_name, price_per_hour, price_per_day, pricing_unit, min_hours, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'draft')", id, user, required(body, "title"),
            body.getOrDefault("description", ""), required(body, "city"), body.getOrDefault("place", ""), hourlyPrice,
            dailyPrice, pricingUnit, minHours);
        saveLines(id,body.get("boundaries"),"service_boundary",300);
        saveLines(id,body.get("questions"),"service_question",60);
        return service(id);
    }

    @GetMapping("/services/mine")
    public List<Map<String,Object>> myServices(@RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo) {
        String user=actor(bearer,demo);
        List<Map<String,Object>> rows=db.queryForList("SELECT id,title,description,city,place_name AS \"place\",cover_url AS \"coverUrl\",price_per_hour AS \"pricePerHour\",price_per_day AS \"pricePerDay\",pricing_unit AS \"pricingUnit\",min_hours AS \"minHours\",status FROM service_post WHERE coser_id=? AND deleted_at IS NULL ORDER BY created_at DESC",user);
        for(Map<String,Object> row:rows) {
            String id=(String)row.get("id");
            row.put("boundaries",db.queryForList("SELECT text FROM service_boundary WHERE service_id=? ORDER BY sort_order",String.class,id));
            row.put("questions",db.queryForList("SELECT text FROM service_question WHERE service_id=? ORDER BY sort_order",String.class,id));
            row.put("images",db.queryForList("SELECT id,media_url AS url FROM service_media WHERE service_id=? AND deleted_at IS NULL ORDER BY sort_order",id));
        }
        return rows;
    }

    @PutMapping("/services/{id}")
    @Transactional
    public Map<String,Object> updateService(@PathVariable String id,@RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo,@RequestBody Map<String,Object> body) {
        String user=actor(bearer,demo);
        String pricingUnit = String.valueOf(body.getOrDefault("pricingUnit", "hour"));
        if (!List.of("hour", "day", "both").contains(pricingUnit)) throw new ApiException(HttpStatus.BAD_REQUEST, "计费单位不合法");
        int hourlyPrice=pricingUnit.equals("day") ? 1 : integer(body,"pricePerHour"), minHours=integer(body,"minHours");
        Integer dailyPrice=pricingUnit.equals("hour") ? null : integer(body,"pricePerDay");
        if(hourlyPrice<=0 || (dailyPrice != null && dailyPrice<=0) || minHours<2 || minHours>8) throw new ApiException(HttpStatus.BAD_REQUEST,"价格或时长不合法");
        int changed=db.update("UPDATE service_post SET title=?,description=?,city=?,place_name=?,price_per_hour=?,price_per_day=?,pricing_unit=?,min_hours=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND coser_id=? AND deleted_at IS NULL AND NOT EXISTS (SELECT 1 FROM booking_order o WHERE o.service_id=service_post.id AND o.status NOT IN ('cancelled','completed'))",
            required(body,"title"),body.getOrDefault("description",""),required(body,"city"),body.getOrDefault("place",""),hourlyPrice,dailyPrice,pricingUnit,minHours,id,user);
        if(changed==0) throw new ApiException(HttpStatus.CONFLICT,"帖子不存在或已有进行中委托");
        saveLines(id,body.get("boundaries"),"service_boundary",300);
        saveLines(id,body.get("questions"),"service_question",60);
        return service(id);
    }

    @DeleteMapping("/services/{id}")
    public Map<String,Object> deleteService(@PathVariable String id,@RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo) {
        String user=actor(bearer,demo);
        int changed=db.update("UPDATE service_post SET deleted_at=CURRENT_TIMESTAMP,status='unpublished' WHERE id=? AND coser_id=? AND deleted_at IS NULL AND NOT EXISTS (SELECT 1 FROM booking_order o WHERE o.service_id=service_post.id AND o.status NOT IN ('cancelled','completed'))",id,user);
        if(changed==0) throw new ApiException(HttpStatus.CONFLICT,"帖子不存在或已有进行中委托");
        return Map.of("deleted",true);
    }

    @PostMapping(value="/services/{id}/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> serviceImage(@PathVariable String id,@RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo,@RequestPart("file") MultipartFile file) {
        String user=actor(bearer,demo);
        Map<String,Object> svc=service(id);
        if(!user.equals(svc.get("coser_id"))) throw new ApiException(HttpStatus.FORBIDDEN,"只能上传自己的帖子图片");
        if(file.isEmpty() || file.getSize()>5_000_000) throw new ApiException(HttpStatus.BAD_REQUEST,"图片不得超过5MB");
        String mime=file.getContentType();
        if(!List.of("image/jpeg","image/png","image/webp").contains(mime)) throw new ApiException(HttpStatus.BAD_REQUEST,"仅支持JPEG、PNG或WebP");
        try {
            byte[] bytes=file.getBytes();
            boolean jpeg=bytes.length>3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255;
            boolean png=bytes.length>8&&(bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71;
            boolean webp=bytes.length>12&&new String(bytes,0,4).equals("RIFF")&&new String(bytes,8,4).equals("WEBP");
            if(!(mime.equals("image/jpeg")&&jpeg||mime.equals("image/png")&&png||mime.equals("image/webp")&&webp)) throw new ApiException(HttpStatus.BAD_REQUEST,"图片内容无效");
            Integer count=db.queryForObject("SELECT COUNT(*) FROM service_media WHERE service_id=? AND deleted_at IS NULL",Integer.class,id);
            if(count!=null && count>=9) throw new ApiException(HttpStatus.BAD_REQUEST,"最多上传9张图片");
            String name=UUID.randomUUID()+(mime.equals("image/png")?".png":mime.equals("image/webp")?".webp":".jpg");
            Path dir=Paths.get(postMediaDirectory).toAbsolutePath().normalize(); Files.createDirectories(dir); Files.write(dir.resolve(name),bytes,StandardOpenOption.CREATE_NEW);
            String url="/v1/post-images/"+name;
            int order=count==null?0:count;
            db.update("INSERT INTO service_media(id,service_id,media_type,object_key,media_url,processing_status,audit_status,sort_order) VALUES(?,?,'image',?,?,'ready','approved',?)",UUID.randomUUID().toString(),id,name,url,order);
            if(order==0) db.update("UPDATE service_post SET cover_object_key=?,cover_url=? WHERE id=?",name,url,id);
            return Map.of("url",url);
        } catch(ApiException ex) { throw ex; }
        catch(Exception ex) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"图片保存失败"); }
    }

    @GetMapping("/post-images/{name:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> postImage(@PathVariable String name) {
        if(!name.matches("[0-9a-f-]{36}\\.(jpg|png|webp)")) throw new ApiException(HttpStatus.NOT_FOUND,"图片不存在");
        Path path=Paths.get(postMediaDirectory).toAbsolutePath().normalize().resolve(name);
        if(!Files.isRegularFile(path)) throw new ApiException(HttpStatus.NOT_FOUND,"图片不存在");
        String mime=name.endsWith(".png")?"image/png":name.endsWith(".webp")?"image/webp":"image/jpeg";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime)).body(new org.springframework.core.io.FileSystemResource(path));
    }

    @PostMapping("/services/{id}/publish")
    public Map<String,Object> publishService(@PathVariable String id,@RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo) {
        String user=actor(bearer,demo);
        int changed=db.update("UPDATE service_post SET status='published',updated_at=CURRENT_TIMESTAMP WHERE id=? AND coser_id=? AND deleted_at IS NULL AND EXISTS (SELECT 1 FROM coser_profile c WHERE c.user_id=service_post.coser_id AND c.verification_status='verified' AND c.service_status='active') AND EXISTS (SELECT 1 FROM service_media m WHERE m.service_id=service_post.id AND m.deleted_at IS NULL AND m.processing_status='ready' AND m.audit_status='approved')",id,user);
        if(changed==0) throw new ApiException(HttpStatus.CONFLICT,"请先上传至少一张图片");
        return service(id);
    }

    @GetMapping("/cosers/{id}/availability")
    public Map<String, Object> availability(@PathVariable String id) {
        Map<String, Object> svc = service(id);
        List<Map<String, Object>> slots = availabilitySupport.availableSlots((String) svc.get("coser_id"), LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        return Map.of("serviceId", id, "slots", slots);
    }

    @GetMapping("/venues")
    public List<Map<String,Object>> venues(@RequestParam String city) {
        return db.queryForList("SELECT id,name,city,district,address,category FROM venue WHERE city=? AND review_status='approved' " +
            "AND category IN ('exhibition','museum','mall','cafe','park') ORDER BY name",city);
    }

    @PostMapping("/orders/quote")
    @Transactional
    public Map<String, Object> quote(@RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo,
            @RequestBody Map<String, Object> body) {
        String user=actor(bearer,demo);
        Map<String, Object> svc = service(required(body, "serviceId"));
        requireTradable(svc);
        requireFemaleAdults(user, (String) svc.get("coser_id"));
        int hours = integer(body, "durationHours");
        String selectedUnit = String.valueOf(body.getOrDefault("pricingUnit", "both".equals(svc.get("pricing_unit")) ? "hour" : svc.get("pricing_unit")));
        boolean daily = "day".equals(selectedUnit);
        if ((!selectedUnit.equals("hour") && !daily) || (daily && "hour".equals(svc.get("pricing_unit"))) ||
                (!daily && "day".equals(svc.get("pricing_unit"))) || (daily && hours != 24) ||
                (!daily && (hours < ((Number) svc.get("min_hours")).intValue() || hours > 24)))
            throw new ApiException(HttpStatus.BAD_REQUEST, "预约时长不符合要求");
        LocalDateTime start;
        try { start = LocalDateTime.parse(required(body, "startAt")); }
        catch (Exception ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "startAt格式应为2026-11-16T14:00:00"); }
        LocalDateTime end = start.plusHours(hours);
        if (start.getMinute() != 0 || start.getSecond() != 0 || !start.isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))
            throw new ApiException(HttpStatus.BAD_REQUEST, "仅支持未来上海时间的整点时段");
        Timestamp startAt = Timestamp.valueOf(start), endAt = Timestamp.valueOf(end);
        requireShortNoticeConfirmation(user, svc, startAt, endAt);
        String coser = (String) svc.get("coser_id");
        List<Map<String,Object>> windows = db.queryForList("SELECT start_at,end_at FROM availability_slot " +
            "WHERE coser_id=? AND status='open' AND start_at<? AND end_at>? ORDER BY start_at", coser, endAt, startAt);
        LocalDateTime covered = start;
        for (Map<String,Object> window : windows) {
            LocalDateTime windowStart = ((Timestamp) window.get("start_at")).toLocalDateTime();
            LocalDateTime windowEnd = ((Timestamp) window.get("end_at")).toLocalDateTime();
            if (windowStart.isAfter(covered)) break;
            if (windowEnd.isAfter(covered)) covered = windowEnd;
            if (!covered.isBefore(end)) break;
        }
        if (covered.isBefore(end)) throw new ApiException(HttpStatus.CONFLICT, "档期不可约");
        Integer conflicts = db.queryForObject("SELECT COUNT(*) FROM booking_order WHERE coser_id=? " +
            "AND start_at<? AND end_at>? AND status NOT IN ('cancelled','refunded') " +
            "AND NOT (status='awaiting_payment' AND payment_expires_at<=CURRENT_TIMESTAMP)",
            Integer.class, coser, endAt, startAt);
        if (conflicts != null && conflicts > 0) throw new ApiException(HttpStatus.CONFLICT, "档期已被其他订单占用");
        Map<String,Object> policy = db.queryForMap("SELECT buffer_before_minutes,buffer_after_minutes FROM coser_profile WHERE user_id=?", coser);
        Timestamp occupiedStart = Timestamp.valueOf(start.minusMinutes(((Number) policy.get("buffer_before_minutes")).intValue()));
        Timestamp occupiedEnd = Timestamp.valueOf(end.plusMinutes(((Number) policy.get("buffer_after_minutes")).intValue()));
        Integer overlaps = db.queryForObject("SELECT COUNT(*) FROM order_appointment WHERE coser_id=? AND occupied_start_at<? AND occupied_end_at>? " +
            "AND (status IN ('confirmed','in_service') OR (status='held' AND hold_expires_at>CURRENT_TIMESTAMP) " +
            "OR (status='awaiting_coser' AND confirm_expires_at>CURRENT_TIMESTAMP))", Integer.class, coser, occupiedEnd, occupiedStart);
        if (overlaps != null && overlaps > 0) throw new ApiException(HttpStatus.CONFLICT, "档期已被占用");
        long amount = daily ? ((Number) svc.get("price_per_day")).longValue() : Math.multiplyExact(((Number) svc.get("price_per_hour")).longValue(), hours);
        String id = "qt-" + UUID.randomUUID();
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(15);
        db.update("INSERT INTO price_quote(id, customer_id, service_id, start_at, end_at, service_amount, " +
            "customer_platform_fee, protection_fee, payable_amount, expires_at, pricing_unit, unit_price_cents) VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?)",
            id, user, svc.get("id"), Timestamp.valueOf(start), Timestamp.valueOf(end), amount, amount, Timestamp.valueOf(expiry),
            selectedUnit, daily ? svc.get("price_per_day") : svc.get("price_per_hour"));
        return Map.of("quoteId", id, "serviceId", svc.get("id"), "serviceAmount", amount,
            "customerPlatformFee", 0, "protectionFee", 0, "payableAmount", amount,
            "currency", "CNY", "expiresAt", expiry.toString());
    }

    @PostMapping("/orders")
    @Transactional
    public Object createOrder(@RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo,@RequestHeader("Idempotency-Key") String key,
            @RequestBody Map<String, Object> body) {
        String user=actor(bearer,demo);
        if (key.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key不能为空");
        List<String> previous = db.queryForList("SELECT result_id FROM idempotency_record WHERE actor_id = ? " +
            "AND operation = 'create_order' AND request_key = ?", String.class, user, key);
        if (!previous.isEmpty()) return orderForUser(previous.get(0), user);
        String quoteId = required(body, "quoteId");
        String venueId = required(body, "venueId");
        List<Map<String, Object>> quotes = db.queryForList("SELECT * FROM price_quote WHERE id = ? AND customer_id = ?", quoteId, user);
        if (quotes.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "报价不存在");
        Map<String, Object> quote = quotes.get(0);
        if (((Timestamp) quote.get("expires_at")).toLocalDateTime().isBefore(LocalDateTime.now()))
            throw new ApiException(HttpStatus.CONFLICT, "报价已过期");
        Map<String, Object> svc = service((String) quote.get("service_id"));
        String quotedUnit = String.valueOf(quote.get("pricing_unit"));
        if ((quotedUnit.equals("day") && "hour".equals(svc.get("pricing_unit"))) ||
                (quotedUnit.equals("hour") && "day".equals(svc.get("pricing_unit"))))
            throw new ApiException(HttpStatus.CONFLICT, "服务计费方式已变更，请重新获取报价");
        long currentUnitPrice = ((Number) (quotedUnit.equals("day") ? svc.get("price_per_day") : svc.get("price_per_hour"))).longValue();
        if (
                (quote.get("unit_price_cents") != null && ((Number) quote.get("unit_price_cents")).longValue() != currentUnitPrice))
            throw new ApiException(HttpStatus.CONFLICT, "服务价格已变更，请重新获取报价");
        String coser = (String) svc.get("coser_id");
        // Serialize overlapping bookings for the same Coser, including requests with different keys.
        db.queryForList("SELECT user_id FROM coser_profile WHERE user_id = ? FOR UPDATE", coser);
        requireTradable(svc);
        requireFemaleAdults(user, coser);
        Timestamp start = (Timestamp) quote.get("start_at");
        Timestamp end = (Timestamp) quote.get("end_at");
        List<Map<String,Object>> windows = db.queryForList("SELECT id,start_at,end_at FROM availability_slot WHERE coser_id=? AND status='open' " +
            "AND start_at<? AND end_at>? ORDER BY start_at",coser,end,start);
        LocalDateTime covered = start.toLocalDateTime();
        String firstWindow = null;
        for (Map<String,Object> window : windows) {
            LocalDateTime windowStart = ((Timestamp) window.get("start_at")).toLocalDateTime();
            LocalDateTime windowEnd = ((Timestamp) window.get("end_at")).toLocalDateTime();
            if (windowStart.isAfter(covered)) break;
            if (windowEnd.isAfter(covered)) { if (firstWindow == null) firstWindow = (String) window.get("id"); covered = windowEnd; }
            if (!covered.isBefore(end.toLocalDateTime())) break;
        }
        if (covered.isBefore(end.toLocalDateTime())) throw new ApiException(HttpStatus.CONFLICT, "档期不可约");
        Integer conflicts = db.queryForObject("SELECT COUNT(*) FROM booking_order WHERE coser_id = ? " +
            "AND start_at < ? AND end_at > ? AND status NOT IN ('cancelled', 'refunded') " +
            "AND NOT (status='awaiting_payment' AND payment_expires_at<=CURRENT_TIMESTAMP)", Integer.class, coser, end, start);
        if (conflicts != null && conflicts > 0) throw new ApiException(HttpStatus.CONFLICT, "档期已被其他订单占用");
        List<Map<String, Object>> venues = db.queryForList("SELECT city FROM venue WHERE id = ? " +
            "AND review_status = 'approved' AND category IN ('exhibition','museum','mall','cafe','park')", venueId);
        if (venues.isEmpty() || !Objects.equals(venues.get(0).get("city"), svc.get("city")))
            throw new ApiException(HttpStatus.BAD_REQUEST, "地点不是该城市允许的公共场所");
        requireShortNoticeConfirmation(user, svc, start, end);
        Map<String,Object> policy=db.queryForMap("SELECT buffer_before_minutes,buffer_after_minutes,min_lead_minutes FROM coser_profile WHERE user_id=?",coser);
        int before=((Number)policy.get("buffer_before_minutes")).intValue(),after=((Number)policy.get("buffer_after_minutes")).intValue();
        Timestamp occupiedStart=Timestamp.valueOf(start.toLocalDateTime().minusMinutes(before));
        Timestamp occupiedEnd=Timestamp.valueOf(end.toLocalDateTime().plusMinutes(after));
        Integer overlaps=db.queryForObject("SELECT COUNT(*) FROM order_appointment WHERE coser_id=? AND occupied_start_at<? AND occupied_end_at>? " +
            "AND (status IN ('confirmed','in_service') OR (status='held' AND hold_expires_at>CURRENT_TIMESTAMP) " +
            "OR (status='awaiting_coser' AND confirm_expires_at>CURRENT_TIMESTAMP))",Integer.class,coser,occupiedEnd,occupiedStart);
        if(overlaps!=null&&overlaps>0) throw new ApiException(HttpStatus.CONFLICT,"档期已被占用");
        LocalDateTime paymentExpiry=LocalDateTime.now().plusMinutes(15);
        if (!paymentExpiry.plusHours(2).isBefore(occupiedStart.toLocalDateTime())) throw new ApiException(HttpStatus.CONFLICT,"无法在服务开始前完成付款与确认");
        String id = "ord-" + UUID.randomUUID();
        db.update("INSERT INTO booking_order(id, customer_id, coser_id, service_id, quote_id, venue_id, " +
            "start_at, end_at, payable_amount, status,request_key,service_title_snapshot,seller_name_snapshot,unit_price_cents,unit_price_unit," +
            "duration_minutes,payment_expires_at,min_lead_minutes_snapshot) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'awaiting_payment',?,?,?,?,?,?,?,?)",
            id, user, coser, svc.get("id"), quoteId, venueId, start, end, quote.get("payable_amount"),key,svc.get("title"),svc.get("coser"),
            quotedUnit.equals("day") ? svc.get("price_per_day") : svc.get("price_per_hour"),quotedUnit,java.time.Duration.between(start.toLocalDateTime(),end.toLocalDateTime()).toMinutes(),
            Timestamp.valueOf(paymentExpiry),policy.get("min_lead_minutes"));
        db.update("INSERT INTO order_appointment(id,order_id,coser_id,availability_id,service_start_at,service_end_at," +
            "buffer_before_minutes,buffer_after_minutes,occupied_start_at,occupied_end_at,timezone_name,status,hold_expires_at) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,'held',?)","apt-"+UUID.randomUUID(),id,coser,firstWindow,start,end,before,after,
            occupiedStart,occupiedEnd,"Asia/Shanghai",Timestamp.valueOf(paymentExpiry));
        db.update("INSERT INTO idempotency_record(actor_id, operation, request_key, result_id) VALUES (?, 'create_order', ?, ?)",
            user, key, id);
        db.update("INSERT INTO order_event(id, order_id, actor_id, action, to_status) VALUES (?, ?, ?, 'create', 'awaiting_payment')",
            "evt-" + UUID.randomUUID(), id, user);
        return orderForUser(id, user);
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> orders(@RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo) {
        String user=actor(bearer,demo);
        expireUnpaidOrders();
        return db.queryForList("SELECT o.id, o.service_id AS \"serviceId\", o.service_title_snapshot AS \"title\", " +
            "o.seller_name_snapshot AS \"coser\", o.start_at AS \"startAt\", o.end_at AS \"endAt\", " +
            "v.name AS \"place\", o.status, o.payable_amount AS \"payableAmount\" FROM booking_order o " +
            "JOIN venue v ON v.id=o.venue_id WHERE o.customer_id = ? ORDER BY o.created_at DESC", user);
    }

    @GetMapping("/coser/orders")
    public List<Map<String, Object>> coserOrders(@RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo) {
        String user=actor(bearer,demo);
        role(user,"coser");
        expireUnpaidOrders();
        return db.queryForList("SELECT o.id, o.customer_id AS \"customerId\", u.nickname AS \"customer\", " +
            "o.service_title_snapshot AS \"title\", o.start_at AS \"startAt\", o.end_at AS \"endAt\", " +
            "v.name AS \"place\", o.status, o.payable_amount AS \"payableAmount\" FROM booking_order o " +
            "JOIN app_user u ON u.id=o.customer_id JOIN venue v ON v.id=o.venue_id " +
            "WHERE o.coser_id=? ORDER BY o.created_at DESC",user);
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> order(@PathVariable String id,@RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo) {
        return orderForUser(id,actor(bearer,demo));
    }

    private Map<String, Object> orderForUser(String id,String user) {
        expireUnpaidOrders();
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM booking_order WHERE id = ? AND " +
            "(customer_id = ? OR coser_id = ?)", id, user, user);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "订单不存在");
        Map<String,Object> result=rows.get(0);
        result.put("place",db.queryForObject("SELECT name FROM venue WHERE id=?",String.class,result.get("venue_id")));
        result.put("customer",db.queryForObject("SELECT nickname FROM app_user WHERE id=?",String.class,result.get("customer_id")));
        if (user.equals(result.get("customer_id")) && "confirmed".equals(result.get("status"))) {
            List<String> codes=db.queryForList("SELECT customer_code FROM order_checkin WHERE order_id=? AND used_at IS NULL",
                String.class,id);
            if (!codes.isEmpty()) result.put("checkInCode",codes.get(0));
        }
        return result;
    }

    @GetMapping("/orders/{id}/events")
    public List<Map<String, Object>> events(@PathVariable String id,@RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo) {
        String user=actor(bearer,demo);
        orderForUser(id, user);
        return db.queryForList("SELECT action, from_status AS \"fromStatus\", to_status AS \"toStatus\", created_at AS \"createdAt\" " +
            "FROM order_event WHERE order_id = ? ORDER BY created_at", id);
    }

    private ApiException unavailable(String message) { return new ApiException(HttpStatus.NOT_IMPLEMENTED, message); }
    private void expireUnpaidOrders() {
        List<Map<String,Object>> expired=db.queryForList("SELECT id FROM booking_order WHERE status='awaiting_payment' " +
            "AND payment_expires_at<=CURRENT_TIMESTAMP");
        for (Map<String,Object> row: expired) {
            String id=(String)row.get("id");
            if (db.update("UPDATE booking_order SET status='cancelled',cancel_reason_code='payment_timeout' " +
                "WHERE id=? AND status='awaiting_payment'",id)==0) continue;
            db.update("UPDATE order_appointment SET status='expired',released_at=CURRENT_TIMESTAMP WHERE order_id=? AND status='held'",id);
            db.update("INSERT INTO order_event(id,order_id,action,from_status,to_status) " +
                "VALUES (?,?,'payment_timeout','awaiting_payment','cancelled')","evt-"+UUID.randomUUID(),id);
        }
    }

    @PostMapping("/orders/{id}/cancel-requests")
    @Transactional
    public Map<String,Object> cancel(@PathVariable String id,
            @RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo,
            @RequestBody(required=false) Map<String,Object> body) {
        String user=actor(bearer,demo);
        Map<String,Object> order=orderForUser(id,user);
        String state=(String)order.get("status");
        if ("awaiting_payment".equals(state)) {
            if (!user.equals(order.get("customer_id"))) throw new ApiException(HttpStatus.FORBIDDEN,"仅下单用户可取消待支付订单");
            db.update("UPDATE booking_order SET status='cancelled',cancel_reason_code='customer_request'," +
                "cancellation_requested_at=CURRENT_TIMESTAMP WHERE id=?",id);
        } else if (List.of("awaiting_coser","confirmed").contains(state)) {
            String reason=body==null?"user_request":required(body,"reason");
            long paid=((Number)order.get("paid_amount")).longValue();
            if (paid<=0) throw new ApiException(HttpStatus.CONFLICT,"付款状态异常");
            long hours=java.time.Duration.between(LocalDateTime.now(ZoneId.of("Asia/Shanghai")),
                ((Timestamp)order.get("start_at")).toLocalDateTime()).toHours();
            int rate=user.equals(order.get("coser_id"))?10000:hours>=48?10000:hours>=24?5000:0;
            long refund=paid*rate/10000;
            String next=refund>0?"refund_pending":"cancelled";
            db.update("UPDATE booking_order SET status=?,cancelled_from_status=?,cancel_reason_code=?," +
                "cancellation_requested_at=CURRENT_TIMESTAMP,cancel_refund_rate_bps=?,refund_amount_due=?," +
                "cancellation_fee_amount=? WHERE id=?",next,state,reason,rate,refund,paid-refund,id);
            if (refund>0) db.update("INSERT INTO order_refund(id,refund_no,order_id,request_key,reason_code,actor_type,actor_id," +
                "cancellation_requested_at,service_start_snapshot,cancel_policy_code,calculation_base_amount," +
                "refund_rate_bps,refund_amount,currency) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?,?,?,?,?,'CNY')",
                "refund-"+UUID.randomUUID(),"rf-"+UUID.randomUUID(),id,"cancel-"+UUID.randomUUID(),reason,
                user.equals(order.get("coser_id"))?"coser":"customer",user,order.get("start_at"),
                order.get("cancel_policy_code"),paid,rate,refund);
        } else throw new ApiException(HttpStatus.CONFLICT,"订单当前不可取消");
        db.update("UPDATE order_appointment SET status='cancelled',released_at=CURRENT_TIMESTAMP WHERE order_id=? AND status='held'",id);
        db.update("UPDATE order_appointment SET status='cancelled',released_at=CURRENT_TIMESTAMP WHERE order_id=? AND status IN ('awaiting_coser','confirmed')",id);
        String nextStatus=db.queryForObject("SELECT status FROM booking_order WHERE id=?",String.class,id);
        db.update("INSERT INTO order_event(id,order_id,actor_id,action,from_status,to_status) " +
            "VALUES (?,?,?,'cancel',?,?)","evt-"+UUID.randomUUID(),id,user,state,nextStatus);
        return orderForUser(id,user);
    }
    @PostMapping("/orders/{id}/confirm")
    @Transactional
    public Map<String,Object> confirm(@PathVariable String id,
            @RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo) {
        String user=actor(bearer,demo);
        Map<String,Object> order=orderForUser(id,user);
        if (!user.equals(order.get("coser_id"))) throw new ApiException(HttpStatus.FORBIDDEN,"仅Coser可确认订单");
        if (!"awaiting_coser".equals(order.get("status"))) throw new ApiException(HttpStatus.CONFLICT,"订单当前不可确认");
        if (((Timestamp)order.get("confirm_expires_at")).before(new Timestamp(System.currentTimeMillis())))
            throw new ApiException(HttpStatus.CONFLICT,"接单已超时");
        db.update("UPDATE booking_order SET status='confirmed',updated_at=CURRENT_TIMESTAMP WHERE id=?",id);
        db.update("UPDATE order_appointment SET status='confirmed',confirmed_at=CURRENT_TIMESTAMP WHERE order_id=?",id);
        String code=String.format("%04d",new SecureRandom().nextInt(10000));
        db.update("INSERT INTO order_checkin(order_id,code_hash,customer_code,code_expires_at) VALUES (?,?,?,?)",id,auth.hash(code),code,order.get("end_at"));
        db.update("INSERT INTO order_event(id,order_id,actor_id,action,from_status,to_status) VALUES (?,?,?,'confirm','awaiting_coser','confirmed')",
            "evt-"+UUID.randomUUID(),id,user);
        return orderForUser(id,user);
    }

    @PostMapping("/orders/{id}/reject")
    @Transactional
    public Map<String,Object> reject(@PathVariable String id,
            @RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo,@RequestBody Map<String,Object> body) {
        String user=actor(bearer,demo);
        Map<String,Object> order=orderForUser(id,user);
        if (!user.equals(order.get("coser_id"))) throw new ApiException(HttpStatus.FORBIDDEN,"仅Coser可拒单");
        if (!"awaiting_coser".equals(order.get("status"))) throw new ApiException(HttpStatus.CONFLICT,"订单当前不可拒绝");
        String reason=required(body,"reason");
        db.update("UPDATE booking_order SET status='refund_pending',cancel_reason_code=?,refund_amount_due=paid_amount," +
            "cancellation_requested_at=CURRENT_TIMESTAMP WHERE id=?",reason,id);
        db.update("UPDATE order_appointment SET status='cancelled',released_at=CURRENT_TIMESTAMP WHERE order_id=?",id);
        db.update("INSERT INTO order_event(id,order_id,actor_id,action,from_status,to_status,reason_code) " +
            "VALUES (?,?,?,'reject','awaiting_coser','refund_pending',?)","evt-"+UUID.randomUUID(),id,user,reason);
        return orderForUser(id,user);
    }

    @PostMapping("/orders/{id}/complete-confirmations")
    @Transactional
    public Map<String,Object> complete(@PathVariable String id,
            @RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo) {
        String user=actor(bearer,demo);
        Map<String,Object> order=orderForUser(id,user);
        if (!"in_service".equals(order.get("status"))) throw new ApiException(HttpStatus.CONFLICT,"订单尚未开始履约");
        Integer existing=db.queryForObject("SELECT COUNT(*) FROM order_completion_confirmation WHERE order_id=? AND actor_id=?",Integer.class,id,user);
        if (existing==null || existing==0) db.update("INSERT INTO order_completion_confirmation(order_id,actor_id) VALUES (?,?)",id,user);
        Integer count=db.queryForObject("SELECT COUNT(*) FROM order_completion_confirmation WHERE order_id=?",Integer.class,id);
        if (count!=null && count==2) {
            db.update("UPDATE booking_order SET status='completed',completion_submitted_at=CURRENT_TIMESTAMP WHERE id=?",id);
            db.update("UPDATE order_appointment SET status='ended',actual_end_at=CURRENT_TIMESTAMP WHERE order_id=?",id);
            CoserWorkbenchController.createSettlement(db, id, (String) order.get("coser_id"),
                ((Number) order.get("paid_amount")).longValue());
            db.update("INSERT INTO order_event(id,order_id,actor_id,action,from_status,to_status) VALUES (?,?,?,'complete','in_service','completed')",
                "evt-"+UUID.randomUUID(),id,user);
        }
        return orderForUser(id,user);
    }

    @PostMapping("/orders/{id}/check-in")
    @Transactional
    public Map<String,Object> checkIn(@PathVariable String id,
            @RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo,
            @RequestParam String code,@RequestParam double latitude,@RequestParam double longitude,
            @RequestParam MultipartFile photo) {
        String user=actor(bearer,demo);
        Map<String,Object> order=orderForUser(id,user);
        if (!user.equals(order.get("coser_id"))) throw new ApiException(HttpStatus.FORBIDDEN,"仅Coser可签到");
        if (!"confirmed".equals(order.get("status"))) throw new ApiException(HttpStatus.CONFLICT,"订单当前不可签到");
        if (!code.matches("[0-9]{4}")) throw new ApiException(HttpStatus.BAD_REQUEST,"请输入4位履约码");
        if (!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180)
            throw new ApiException(HttpStatus.BAD_REQUEST,"位置信息无效");
        if (photo.isEmpty() || photo.getSize()>2_000_000 ||
            !List.of("image/jpeg","image/png").contains(photo.getContentType()))
            throw new ApiException(HttpStatus.BAD_REQUEST,"请上传2MB以内的JPEG或PNG现场照片");
        List<Map<String,Object>> codes=db.queryForList("SELECT code_hash,code_expires_at FROM order_checkin WHERE order_id=? AND used_at IS NULL",id);
        if (codes.isEmpty() || !auth.hash(code).equals(codes.get(0).get("code_hash")) ||
            ((Timestamp)codes.get(0).get("code_expires_at")).before(new Timestamp(System.currentTimeMillis())))
            throw new ApiException(HttpStatus.CONFLICT,"履约码错误或已过期");
        String path;
        try {
            Path directory=Paths.get(checkinDirectory).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path file=directory.resolve(UUID.randomUUID()+("image/png".equals(photo.getContentType())?".png":".jpg"));
            photo.transferTo(file);
            path=file.toString();
        } catch (IOException ex) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"现场照片保存失败"); }
        db.update("UPDATE order_checkin SET used_at=CURRENT_TIMESTAMP,latitude=?,longitude=?,photo_path=? WHERE order_id=? AND used_at IS NULL",
            latitude,longitude,path,id);
        db.update("UPDATE booking_order SET status='in_service',updated_at=CURRENT_TIMESTAMP WHERE id=?",id);
        db.update("UPDATE order_appointment SET status='in_service',actual_start_at=CURRENT_TIMESTAMP WHERE order_id=?",id);
        db.update("INSERT INTO order_event(id,order_id,actor_id,action,from_status,to_status) VALUES (?,?,?,'check_in','confirmed','in_service')",
            "evt-"+UUID.randomUUID(),id,user);
        return orderForUser(id,user);
    }

    @PostMapping("/orders/{id}/exceptions")
    @Transactional
    public Map<String,Object> reportException(@PathVariable String id,
            @RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo,@RequestBody Map<String,Object> body) {
        String user=actor(bearer,demo);
        orderForUser(id,user);
        String reason=required(body,"reason");
        if (reason.length()>200) throw new ApiException(HttpStatus.BAD_REQUEST,"异常描述过长");
        String caseId="case-"+UUID.randomUUID();
        db.update("INSERT INTO order_exception(id,order_id,actor_id,reason) VALUES (?,?,?,?)",caseId,id,user,reason);
        return Map.of("id",caseId,"status","pending");
    }

    @PostMapping("/orders/{id}/reviews")
    @Transactional
    public Map<String,Object> review(@PathVariable String id,
            @RequestHeader(value="Authorization",required=false) String bearer,
            @RequestHeader(value="X-Demo-User",required=false) String demo,@RequestBody Map<String,Object> body) {
        String user=actor(bearer,demo);
        Map<String,Object> order=orderForUser(id,user);
        if (!user.equals(order.get("customer_id"))) throw new ApiException(HttpStatus.FORBIDDEN,"仅用户可评价");
        if (!"completed".equals(order.get("status"))) throw new ApiException(HttpStatus.CONFLICT,"订单尚未完成");
        int score=integer(body,"score");
        if (score<1||score>5) throw new ApiException(HttpStatus.BAD_REQUEST,"评分必须为1至5分");
        Integer count=db.queryForObject("SELECT COUNT(*) FROM service_review WHERE order_id=?",Integer.class,id);
        if (count!=null&&count>0) throw new ApiException(HttpStatus.CONFLICT,"订单已评价");
        db.update("INSERT INTO service_review(id,order_id,customer_id,coser_id,score,body) VALUES (?,?,?,?,?,?)",
            "rev-"+UUID.randomUUID(),id,user,order.get("coser_id"),score,String.valueOf(body.getOrDefault("body","")));
        return Map.of("orderId",id,"score",score);
    }
    @PostMapping("/uploads/presign") public Object upload() { throw unavailable("对象存储与内容审核尚未接入"); }
}
