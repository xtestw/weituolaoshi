package com.weituolaoshi.server;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class CoserWorkbenchController {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int PLATFORM_FEE_BPS = 500;
    private static final int SETTLEMENT_HOLD_HOURS = 48;

    private final JdbcTemplate db;
    private final AuthSupport auth;
    private final Environment environment;

    public CoserWorkbenchController(JdbcTemplate db, AuthSupport auth, Environment environment) {
        this.db = db;
        this.auth = auth;
        this.environment = environment;
    }

    private String actor(String bearer, String demo) {
        if (bearer != null && !bearer.isBlank()) return auth.user(bearer);
        if (Arrays.asList(environment.getActiveProfiles()).contains("local") && demo != null && demo.startsWith("demo-")) return demo;
        throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
    }

    private void requireCoser(String user) {
        List<String> roles = db.queryForList("SELECT role FROM app_user WHERE id=? AND account_status='active'", String.class, user);
        if (roles.isEmpty() || !"coser".equals(roles.get(0)))
            throw new ApiException(HttpStatus.FORBIDDEN, "当前账号无权操作");
        Integer profile = db.queryForObject("SELECT COUNT(*) FROM coser_profile WHERE user_id=?", Integer.class, user);
        if (profile == null || profile == 0) throw new ApiException(HttpStatus.FORBIDDEN, "尚未开通 Coser 资料");
    }

    private String required(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof String) || ((String) value).isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, key + "不能为空");
        return ((String) value).trim();
    }

    private Map<String, Object> reputationOf(String coserId) {
        Map<String, Object> agg = db.queryForMap(
            "SELECT COUNT(*) AS scored, COALESCE(AVG(score),0) AS average FROM service_review " +
            "WHERE coser_id=? AND score BETWEEN 1 AND 5 AND status IN ('pending','public')", coserId);
        int scored = ((Number) agg.get("scored")).intValue();
        Integer incomplete = db.queryForObject(
            "SELECT COUNT(DISTINCT e.order_id) FROM order_exception e " +
            "JOIN booking_order o ON o.id=e.order_id WHERE o.coser_id=? AND e.status='approved' " +
            "AND e.responsible_party='coser' AND e.fulfillment_incomplete=TRUE", Integer.class, coserId);
        Map<String, Object> result = new HashMap<>();
        result.put("scoredOrders", scored);
        result.put("incompleteCount", incomplete == null ? 0 : incomplete);
        result.put("averageScore", scored == 0 ? null :
            BigDecimal.valueOf(((Number) agg.get("average")).doubleValue()).setScale(1, RoundingMode.HALF_UP).doubleValue());
        return result;
    }

    private void releaseDueSettlements(String coserId) {
        Timestamp due = Timestamp.valueOf(LocalDateTime.now(SHANGHAI).minusHours(SETTLEMENT_HOLD_HOURS));
        db.update("UPDATE settlement SET status='settled', settled_at=COALESCE(settled_at,CURRENT_TIMESTAMP) " +
            "WHERE coser_id=? AND status='pending' AND created_at<=?", coserId, due);
    }

    static void createSettlement(JdbcTemplate db, String orderId, String coserId, long paidAmount) {
        Integer existing = db.queryForObject("SELECT COUNT(*) FROM settlement WHERE order_id=?", Integer.class, orderId);
        if (existing != null && existing > 0) return;
        long fee = paidAmount * PLATFORM_FEE_BPS / 10000;
        long payable = paidAmount - fee;
        db.update("INSERT INTO settlement(id,order_id,coser_id,gross_amount,platform_fee,payable_amount,status,currency) " +
            "VALUES (?,?,?,?,?,?,'pending','CNY')",
            "stl-" + UUID.randomUUID(), orderId, coserId, paidAmount, fee, payable);
    }

    @GetMapping("/coser/dashboard")
    public Map<String, Object> dashboard(@RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestHeader(value = "X-Demo-User", required = false) String demo) {
        String user = actor(bearer, demo);
        requireCoser(user);
        releaseDueSettlements(user);

        Map<String, Object> profile = db.queryForMap(
            "SELECT display_name AS \"displayName\", verification_status AS \"verificationStatus\", " +
            "service_status AS \"serviceStatus\" FROM coser_profile WHERE user_id=?", user);

        Integer pendingConfirm = db.queryForObject(
            "SELECT COUNT(*) FROM booking_order WHERE coser_id=? AND status='awaiting_coser'", Integer.class, user);
        Integer pendingFulfill = db.queryForObject(
            "SELECT COUNT(*) FROM booking_order WHERE coser_id=? AND status IN ('confirmed','in_service')", Integer.class, user);
        Timestamp weekStart = Timestamp.valueOf(LocalDate.now(SHANGHAI).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).atStartOfDay());
        Integer weekCompleted = db.queryForObject(
            "SELECT COUNT(*) FROM booking_order WHERE coser_id=? AND status='completed' AND completion_submitted_at>=?",
            Integer.class, user, weekStart);

        Map<String, Object> reputation = reputationOf(user);
        List<Map<String, Object>> spotlightRows = db.queryForList(
            "SELECT o.id, o.service_title_snapshot AS \"title\", u.nickname AS \"customer\", " +
            "o.start_at AS \"startAt\", o.end_at AS \"endAt\", v.name AS \"place\", " +
            "o.payable_amount AS \"payableAmount\", o.paid_amount AS \"paidAmount\", o.status, " +
            "o.customer_id AS \"customerId\" FROM booking_order o " +
            "JOIN app_user u ON u.id=o.customer_id JOIN venue v ON v.id=o.venue_id " +
            "WHERE o.coser_id=? AND o.status IN ('awaiting_coser','confirmed','in_service') " +
            "ORDER BY CASE o.status WHEN 'awaiting_coser' THEN 0 WHEN 'in_service' THEN 1 ELSE 2 END, o.start_at LIMIT 1",
            user);

        Map<String, Object> result = new HashMap<>(profile);
        result.put("pendingConfirmCount", pendingConfirm == null ? 0 : pendingConfirm);
        result.put("pendingFulfillCount", pendingFulfill == null ? 0 : pendingFulfill);
        result.put("weekCompletedCount", weekCompleted == null ? 0 : weekCompleted);
        result.put("averageScore", reputation.get("averageScore"));
        result.put("scoredOrders", reputation.get("scoredOrders"));
        result.put("incompleteCount", reputation.get("incompleteCount"));
        result.put("spotlight", spotlightRows.isEmpty() ? null : spotlightRows.get(0));
        return result;
    }

    @GetMapping("/cosers/me/reputation")
    public Map<String, Object> myReputation(@RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestHeader(value = "X-Demo-User", required = false) String demo) {
        String user = actor(bearer, demo);
        requireCoser(user);
        return reputationOf(user);
    }

    @GetMapping("/cosers/{id}/reputation")
    public Map<String, Object> publicReputation(@PathVariable String id) {
        Integer exists = db.queryForObject("SELECT COUNT(*) FROM coser_profile WHERE user_id=?", Integer.class, id);
        if (exists == null || exists == 0) throw new ApiException(HttpStatus.NOT_FOUND, "Coser不存在");
        return reputationOf(id);
    }

    @GetMapping("/cosers/me/profile")
    public Map<String, Object> myProfile(@RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestHeader(value = "X-Demo-User", required = false) String demo) {
        String user = actor(bearer, demo);
        requireCoser(user);
        return db.queryForMap(
            "SELECT c.user_id AS \"userId\", c.display_name AS \"displayName\", c.city, c.introduction, " +
            "c.verification_status AS \"verificationStatus\", c.service_status AS \"serviceStatus\", " +
            "c.buffer_before_minutes AS \"bufferBeforeMinutes\", c.buffer_after_minutes AS \"bufferAfterMinutes\", " +
            "c.min_lead_minutes AS \"minLeadMinutes\", u.nickname, u.avatar_url AS \"avatarUrl\", u.phone, u.bio " +
            "FROM coser_profile c JOIN app_user u ON u.id=c.user_id WHERE c.user_id=?", user);
    }

    @PatchMapping("/cosers/me/profile")
    @Transactional
    public Map<String, Object> updateProfile(@RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestHeader(value = "X-Demo-User", required = false) String demo,
            @RequestBody Map<String, Object> body) {
        String user = actor(bearer, demo);
        requireCoser(user);
        if (body.containsKey("displayName")) {
            String name = required(body, "displayName");
            if (name.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "展示名过长");
            db.update("UPDATE coser_profile SET display_name=?, updated_at=CURRENT_TIMESTAMP WHERE user_id=?", name, user);
        }
        if (body.containsKey("city")) {
            String city = required(body, "city");
            if (city.length() > 80) throw new ApiException(HttpStatus.BAD_REQUEST, "城市过长");
            db.update("UPDATE coser_profile SET city=?, updated_at=CURRENT_TIMESTAMP WHERE user_id=?", city, user);
        }
        if (body.containsKey("introduction")) {
            Object raw = body.get("introduction");
            String intro = raw == null ? "" : String.valueOf(raw).trim();
            if (intro.length() > 2000) throw new ApiException(HttpStatus.BAD_REQUEST, "简介过长");
            db.update("UPDATE coser_profile SET introduction=?, updated_at=CURRENT_TIMESTAMP WHERE user_id=?", intro, user);
        }
        if (body.containsKey("serviceStatus")) {
            String status = required(body, "serviceStatus");
            if (!List.of("active", "paused").contains(status))
                throw new ApiException(HttpStatus.BAD_REQUEST, "营业状态仅支持 active 或 paused");
            db.update("UPDATE coser_profile SET service_status=?, updated_at=CURRENT_TIMESTAMP WHERE user_id=?", status, user);
        }
        return myProfile(bearer, demo);
    }

    @GetMapping("/cosers/me/settlements/summary")
    public Map<String, Object> settlementSummary(@RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestHeader(value = "X-Demo-User", required = false) String demo) {
        String user = actor(bearer, demo);
        requireCoser(user);
        releaseDueSettlements(user);

        Long pending = db.queryForObject(
            "SELECT COALESCE(SUM(payable_amount),0) FROM settlement WHERE coser_id=? AND status='pending'", Long.class, user);
        LocalDateTime monthStart = LocalDate.now(SHANGHAI).withDayOfMonth(1).atStartOfDay();
        Long monthSettled = db.queryForObject(
            "SELECT COALESCE(SUM(payable_amount),0) FROM settlement WHERE coser_id=? AND status='settled' " +
            "AND COALESCE(settled_at,created_at)>=?", Long.class, user, Timestamp.valueOf(monthStart));
        Integer monthOrders = db.queryForObject(
            "SELECT COUNT(*) FROM booking_order WHERE coser_id=? AND status='completed' AND completion_submitted_at>=?",
            Integer.class, user, Timestamp.valueOf(monthStart));

        Map<String, Object> result = new HashMap<>();
        result.put("pendingAmount", pending == null ? 0L : pending);
        result.put("monthSettledAmount", monthSettled == null ? 0L : monthSettled);
        result.put("monthOrderCount", monthOrders == null ? 0 : monthOrders);
        result.put("platformFeeBps", PLATFORM_FEE_BPS);
        result.put("settlementHoldHours", SETTLEMENT_HOLD_HOURS);
        return result;
    }

    @GetMapping("/cosers/me/settlements")
    public List<Map<String, Object>> settlements(@RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestHeader(value = "X-Demo-User", required = false) String demo,
            @RequestParam(defaultValue = "50") int limit) {
        String user = actor(bearer, demo);
        requireCoser(user);
        releaseDueSettlements(user);
        int size = Math.min(Math.max(limit, 1), 100);
        return db.queryForList(
            "SELECT s.id, s.order_id AS \"orderId\", o.service_title_snapshot AS \"title\", " +
            "s.gross_amount AS \"grossAmount\", s.platform_fee AS \"platformFee\", " +
            "s.payable_amount AS \"payableAmount\", s.status, s.created_at AS \"createdAt\", " +
            "s.settled_at AS \"settledAt\" FROM settlement s JOIN booking_order o ON o.id=s.order_id " +
            "WHERE s.coser_id=? ORDER BY s.created_at DESC LIMIT ?", user, size);
    }

    @GetMapping("/coser/exceptions")
    public List<Map<String, Object>> exceptions(@RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestHeader(value = "X-Demo-User", required = false) String demo) {
        String user = actor(bearer, demo);
        requireCoser(user);
        return db.queryForList(
            "SELECT e.id, e.order_id AS \"orderId\", o.service_title_snapshot AS \"title\", e.reason, e.status, " +
            "e.responsible_party AS \"responsibleParty\", e.fulfillment_incomplete AS \"fulfillmentIncomplete\", " +
            "e.review_note AS \"reviewNote\", e.created_at AS \"createdAt\", e.reviewed_at AS \"reviewedAt\" " +
            "FROM order_exception e JOIN booking_order o ON o.id=e.order_id WHERE o.coser_id=? " +
            "ORDER BY e.created_at DESC LIMIT 100", user);
    }

    @PostMapping("/support/tickets")
    @Transactional
    public Map<String, Object> createTicket(@RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestHeader(value = "X-Demo-User", required = false) String demo,
            @RequestBody Map<String, Object> body) {
        String user = actor(bearer, demo);
        String role = db.queryForObject("SELECT role FROM app_user WHERE id=? AND account_status='active'", String.class, user);
        if (role == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        String category = required(body, "category");
        String subject = body.containsKey("subject") ? required(body, "subject") : category;
        String ticketBody = required(body, "body");
        if (category.length() > 40 || subject.length() > 120 || ticketBody.length() > 2000)
            throw new ApiException(HttpStatus.BAD_REQUEST, "工单内容过长");
        String orderId = null;
        if (body.get("orderId") instanceof String && !((String) body.get("orderId")).isBlank()) {
            orderId = ((String) body.get("orderId")).trim();
            Integer owned = db.queryForObject(
                "SELECT COUNT(*) FROM booking_order WHERE id=? AND (customer_id=? OR coser_id=?)", Integer.class, orderId, user, user);
            if (owned == null || owned == 0) throw new ApiException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        String id = "tkt-" + UUID.randomUUID();
        db.update("INSERT INTO support_ticket(id,requester_id,role_snapshot,category,subject,body,order_id) VALUES (?,?,?,?,?,?,?)",
            id, user, role, category, subject, ticketBody, orderId);
        return Map.of("id", id, "status", "open");
    }
}
