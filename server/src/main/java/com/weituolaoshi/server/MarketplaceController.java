package com.weituolaoshi.server;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class MarketplaceController {
    private final JdbcTemplate db;
    public MarketplaceController(JdbcTemplate db) { this.db = db; }

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

    private void role(String user, String expected) {
        List<String> roles = db.queryForList("SELECT role FROM app_user WHERE id = ?", String.class, user);
        if (!user.startsWith("demo-") || roles.isEmpty() || !expected.equals(roles.get(0)))
            throw new ApiException(HttpStatus.FORBIDDEN, "仅允许匹配角色的本地演示用户");
    }

    private Map<String, Object> service(String id) {
        List<Map<String, Object>> rows = db.queryForList("SELECT s.id, s.coser_id, c.display_name AS coser, s.title, " +
            "s.city, s.price_per_hour, s.min_hours, s.status, c.deposit_qualified FROM service_post s " +
            "JOIN coser_profile c ON c.user_id = s.coser_id WHERE s.id = ?", id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "服务不存在");
        return rows.get(0);
    }

    @GetMapping("/services")
    public List<Map<String, Object>> services() {
        return db.queryForList("SELECT s.id, s.title, c.display_name AS coser, s.city, " +
            "s.price_per_hour AS \"pricePerHour\", s.min_hours AS \"minHours\" FROM service_post s " +
            "JOIN coser_profile c ON c.user_id = s.coser_id WHERE s.status = 'published' ORDER BY s.created_at DESC");
    }

    @PostMapping("/services")
    @Transactional
    public Map<String, Object> createService(@RequestHeader("X-Demo-User") String user,
            @RequestBody Map<String, Object> body) {
        role(user, "coser");
        int price = integer(body, "pricePerHour");
        int minHours = integer(body, "minHours");
        if (price <= 0 || minHours < 2 || minHours > 8)
            throw new ApiException(HttpStatus.BAD_REQUEST, "价格或最小时长不合法");
        String id = "svc-" + UUID.randomUUID();
        db.update("INSERT INTO service_post(id, coser_id, title, description, city, price_per_hour, min_hours, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, 'pending_review')", id, user, required(body, "title"),
            body.getOrDefault("description", ""), required(body, "city"), price, minHours);
        return service(id);
    }

    @GetMapping("/cosers/{id}/availability")
    public Map<String, Object> availability(@PathVariable String id) {
        Map<String, Object> svc = service(id);
        List<Map<String, Object>> slots = db.queryForList("SELECT id, start_at AS \"startAt\", end_at AS \"endAt\" " +
            "FROM availability_slot WHERE coser_id = ? AND status = 'open' AND end_at > CURRENT_TIMESTAMP ORDER BY start_at",
            svc.get("coser_id"));
        return Map.of("serviceId", id, "slots", slots);
    }

    @PostMapping("/orders/quote")
    @Transactional
    public Map<String, Object> quote(@RequestHeader("X-Demo-User") String user,
            @RequestBody Map<String, Object> body) {
        role(user, "customer");
        Map<String, Object> svc = service(required(body, "serviceId"));
        if (!"published".equals(svc.get("status")) || !Boolean.TRUE.equals(svc.get("deposit_qualified")))
            throw new ApiException(HttpStatus.CONFLICT, "服务不可交易");
        int hours = integer(body, "durationHours");
        if (hours < ((Number) svc.get("min_hours")).intValue() || hours > 8)
            throw new ApiException(HttpStatus.BAD_REQUEST, "预约时长不符合要求");
        LocalDateTime start;
        try { start = LocalDateTime.parse(required(body, "startAt")); }
        catch (Exception ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "startAt格式应为2026-11-16T14:00:00"); }
        LocalDateTime end = start.plusHours(hours);
        if (!start.toLocalDate().equals(end.toLocalDate()) || start.getHour() < 10 || end.getHour() > 22 ||
                start.getMinute() != 0 || start.isBefore(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))
            throw new ApiException(HttpStatus.BAD_REQUEST, "只接受上海时间10:00至22:00内的非过夜整点时段");
        long amount = Math.multiplyExact(((Number) svc.get("price_per_hour")).longValue(), hours);
        String id = "qt-" + UUID.randomUUID();
        LocalDateTime expiry = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10);
        db.update("INSERT INTO price_quote(id, customer_id, service_id, start_at, end_at, service_amount, " +
            "customer_platform_fee, protection_fee, payable_amount, expires_at) VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?, ?)",
            id, user, svc.get("id"), Timestamp.valueOf(start), Timestamp.valueOf(end), amount, amount, Timestamp.valueOf(expiry));
        return Map.of("quoteId", id, "serviceId", svc.get("id"), "serviceAmount", amount,
            "customerPlatformFee", 0, "protectionFee", 0, "payableAmount", amount,
            "currency", "CNY", "expiresAt", expiry.toString() + "Z");
    }

    @PostMapping("/orders")
    @Transactional
    public Object createOrder(@RequestHeader("X-Demo-User") String user, @RequestHeader("Idempotency-Key") String key,
            @RequestBody Map<String, Object> body) {
        role(user, "customer");
        if (key.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key不能为空");
        List<String> previous = db.queryForList("SELECT result_id FROM idempotency_record WHERE actor_id = ? " +
            "AND operation = 'create_order' AND request_key = ?", String.class, user, key);
        if (!previous.isEmpty()) return order(previous.get(0), user);
        String quoteId = required(body, "quoteId");
        String venueId = required(body, "venueId");
        List<Map<String, Object>> quotes = db.queryForList("SELECT * FROM price_quote WHERE id = ? AND customer_id = ?", quoteId, user);
        if (quotes.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "报价不存在");
        Map<String, Object> quote = quotes.get(0);
        if (((Timestamp) quote.get("expires_at")).toLocalDateTime().isBefore(LocalDateTime.now(ZoneOffset.UTC)))
            throw new ApiException(HttpStatus.CONFLICT, "报价已过期");
        Map<String, Object> svc = service((String) quote.get("service_id"));
        String coser = (String) svc.get("coser_id");
        // Serialize overlapping bookings for the same Coser, including requests with different keys.
        db.queryForList("SELECT user_id FROM coser_profile WHERE user_id = ? FOR UPDATE", coser);
        if (!"published".equals(svc.get("status")) || !Boolean.TRUE.equals(svc.get("deposit_qualified")))
            throw new ApiException(HttpStatus.CONFLICT, "服务已停止交易");
        List<Map<String, Object>> identities = db.queryForList("SELECT user_id, legal_gender FROM identity_verification " +
            "WHERE user_id IN (?, ?) AND status = 'verified' AND adult = TRUE", user, coser);
        if (identities.size() != 2 || !Objects.equals(identities.get(0).get("legal_gender"), identities.get(1).get("legal_gender")))
            throw new ApiException(HttpStatus.FORBIDDEN, "双方下单资格不匹配");
        Timestamp start = (Timestamp) quote.get("start_at");
        Timestamp end = (Timestamp) quote.get("end_at");
        Integer available = db.queryForObject("SELECT COUNT(*) FROM availability_slot WHERE coser_id = ? " +
            "AND status = 'open' AND start_at <= ? AND end_at >= ?", Integer.class, coser, start, end);
        if (available == null || available == 0) throw new ApiException(HttpStatus.CONFLICT, "档期不可约");
        Integer conflicts = db.queryForObject("SELECT COUNT(*) FROM booking_order WHERE coser_id = ? " +
            "AND start_at < ? AND end_at > ? AND status NOT IN ('cancelled', 'refunded')", Integer.class, coser, end, start);
        if (conflicts != null && conflicts > 0) throw new ApiException(HttpStatus.CONFLICT, "档期已被其他订单占用");
        List<Map<String, Object>> venues = db.queryForList("SELECT city FROM venue WHERE id = ? " +
            "AND review_status = 'approved' AND category IN ('exhibition','museum','mall','cafe','park')", venueId);
        if (venues.isEmpty() || !Objects.equals(venues.get(0).get("city"), svc.get("city")))
            throw new ApiException(HttpStatus.BAD_REQUEST, "地点不是该城市允许的公共场所");
        if (start.toLocalDateTime().isBefore(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(24))) {
            Integer confirmed = db.queryForObject("SELECT COUNT(*) FROM time_confirmation t JOIN conversation c " +
                "ON c.id = t.conversation_id WHERE c.customer_id = ? AND c.coser_id = ? " +
                "AND t.start_at = ? AND t.end_at = ? AND t.expires_at > CURRENT_TIMESTAMP",
                Integer.class, user, coser, start, end);
            if (confirmed == null || confirmed == 0)
                throw new ApiException(HttpStatus.CONFLICT, "24小时内预约需要Coser聊天确认时间");
        }
        String id = "ord-" + UUID.randomUUID();
        db.update("INSERT INTO booking_order(id, customer_id, coser_id, service_id, quote_id, venue_id, " +
            "start_at, end_at, payable_amount, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'awaiting_payment')",
            id, user, coser, svc.get("id"), quoteId, venueId, start, end, quote.get("payable_amount"));
        db.update("INSERT INTO idempotency_record(actor_id, operation, request_key, result_id) VALUES (?, 'create_order', ?, ?)",
            user, key, id);
        db.update("INSERT INTO order_event(id, order_id, actor_id, action, to_status) VALUES (?, ?, ?, 'create', 'awaiting_payment')",
            "evt-" + UUID.randomUUID(), id, user);
        return order(id, user);
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> orders(@RequestHeader("X-Demo-User") String user) {
        role(user, "customer");
        return db.queryForList("SELECT id, service_id AS \"serviceId\", status, payable_amount AS \"payableAmount\" " +
            "FROM booking_order WHERE customer_id = ? ORDER BY created_at DESC", user);
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> order(@PathVariable String id, @RequestHeader("X-Demo-User") String user) {
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM booking_order WHERE id = ? AND " +
            "(customer_id = ? OR coser_id = ?)", id, user, user);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "订单不存在");
        return rows.get(0);
    }

    @GetMapping("/orders/{id}/events")
    public List<Map<String, Object>> events(@PathVariable String id, @RequestHeader("X-Demo-User") String user) {
        order(id, user);
        return db.queryForList("SELECT action, from_status AS \"fromStatus\", to_status AS \"toStatus\", created_at AS \"createdAt\" " +
            "FROM order_event WHERE order_id = ? ORDER BY created_at", id);
    }

    private ApiException unavailable(String message) { return new ApiException(HttpStatus.NOT_IMPLEMENTED, message); }
    @PostMapping("/orders/{id}/payment") public Object payment(@PathVariable String id) { throw unavailable("微信支付尚未接入"); }
    @PostMapping("/orders/{id}/confirm") public Object confirm(@PathVariable String id) { throw unavailable("支付回调与身份校验尚未接入"); }
    @PostMapping("/orders/{id}/cancel-requests") public Object cancel(@PathVariable String id) { throw unavailable("退款与责任协商尚未接入"); }
    @PostMapping("/orders/{id}/complete-confirmations") public Object complete(@PathVariable String id) { throw unavailable("双方确认与结算尚未接入"); }
    @PostMapping("/uploads/presign") public Object upload() { throw unavailable("对象存储与内容审核尚未接入"); }
    @GetMapping("/messages/conversations") public List<Object> conversations() { return List.of(); }
    @PostMapping("/messages/conversations/{id}/messages") public Object message(@PathVariable String id) { throw unavailable("消息存储与风控尚未接入"); }
}
