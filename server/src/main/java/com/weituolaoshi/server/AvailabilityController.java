package com.weituolaoshi.server;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/cosers/me/availability")
public class AvailabilityController {
    private final JdbcTemplate db;
    private final AuthSupport auth;
    private final IdentityGenderPolicy genderPolicy;
    private final Environment environment;
    private final AvailabilitySupport availability;

    public AvailabilityController(JdbcTemplate db, AuthSupport auth, Environment environment,
            IdentityGenderPolicy genderPolicy, AvailabilitySupport availability) {
        this.db = db;
        this.auth = auth;
        this.environment = environment;
        this.genderPolicy = genderPolicy;
        this.availability = availability;
    }

    private String coser(String bearer, String demo) {
        String user;
        if (bearer != null && !bearer.isBlank()) user = auth.user(bearer);
        else if (Arrays.asList(environment.getActiveProfiles()).contains("local") && "demo-coser-f".equals(demo)) user = demo;
        else throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        Integer eligible = db.queryForObject("SELECT COUNT(*) FROM coser_profile c JOIN app_user u ON u.id=c.user_id " +
            "WHERE c.user_id=? AND u.account_status='active' AND u.role='coser' " +
            "AND c.verification_status='verified' AND c.service_status='active' " +
            "AND EXISTS (SELECT 1 FROM identity_verification v WHERE v.user_id=c.user_id " +
            "AND v.status='verified' AND v.adult=TRUE AND " + genderPolicy.sql("v.") + ")", Integer.class, user);
        if (eligible == null || eligible == 0) throw new ApiException(HttpStatus.FORBIDDEN, "仅已认证的成年女性Coser可设置档期");
        return user;
    }

    private YearMonth month(String value) {
        try {
            YearMonth month = YearMonth.parse(value);
            YearMonth now = YearMonth.now(AvailabilitySupport.ZONE);
            if (month.isBefore(now) || month.isAfter(YearMonth.of(now.getYear() + 1, 12)))
                throw new ApiException(HttpStatus.BAD_REQUEST, "仅可设置本月至明年12月的档期");
            return month;
        } catch (DateTimeParseException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "month格式应为YYYY-MM");
        }
    }

    private LocalDate date(String value) {
        try {
            LocalDate date = LocalDate.parse(value);
            month(YearMonth.from(date).toString());
            if (date.isBefore(LocalDate.now(AvailabilitySupport.ZONE)))
                throw new ApiException(HttpStatus.BAD_REQUEST, "不能修改过去的档期");
            return date;
        } catch (DateTimeParseException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "date格式应为YYYY-MM-DD");
        }
    }

    @GetMapping
    public Map<String, Object> month(@RequestHeader(value="Authorization", required=false) String bearer,
            @RequestHeader(value="X-Demo-User", required=false) String demo, @RequestParam String month) {
        String user = coser(bearer, demo);
        YearMonth target = month(month);
        Timestamp first = Timestamp.valueOf(target.atDay(1).atStartOfDay());
        Timestamp last = Timestamp.valueOf(target.plusMonths(1).atDay(1).atStartOfDay());
        List<java.sql.Date> openDates = db.queryForList("SELECT DISTINCT CAST(start_at AS DATE) FROM availability_slot " +
            "WHERE coser_id=? AND status='open' AND start_at>=? AND start_at<?", java.sql.Date.class, user, first, last);
        Set<LocalDate> open = new HashSet<>();
        for (java.sql.Date item : openDates) open.add(item.toLocalDate());

        List<Map<String, Object>> occupiedRows = db.queryForList(
            "SELECT CAST(occupied_start_at AS DATE) AS occupied_day, COUNT(*) AS cnt FROM order_appointment " +
            "WHERE coser_id=? AND occupied_start_at<? AND occupied_end_at>? AND " +
            "(status IN ('confirmed','in_service') OR (status='held' AND hold_expires_at>CURRENT_TIMESTAMP) OR " +
            "(status='awaiting_coser' AND confirm_expires_at>CURRENT_TIMESTAMP)) " +
            "GROUP BY CAST(occupied_start_at AS DATE)", user, last, first);
        Map<LocalDate, Integer> occupied = new HashMap<>();
        for (Map<String, Object> row : occupiedRows) {
            LocalDate day = ((java.sql.Date) row.get("occupied_day")).toLocalDate();
            occupied.put(day, ((Number) row.get("cnt")).intValue());
        }

        LocalDate today = LocalDate.now(AvailabilitySupport.ZONE);
        List<Map<String, Object>> days = new ArrayList<>();
        for (int day = 1; day <= target.lengthOfMonth(); day++) {
            LocalDate date = target.atDay(day);
            boolean isOpen = open.contains(date);
            int count = occupied.getOrDefault(date, 0);
            String status = !isOpen ? "closed" : (count > 0 ? "occupied" : "open");
            boolean past = date.isBefore(today);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.toString());
            item.put("open", isOpen);
            item.put("status", status);
            item.put("occupiedCount", count);
            item.put("editable", !past && count == 0);
            days.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", target.toString());
        result.put("windowStart", AvailabilitySupport.WINDOW_START.toString());
        result.put("windowEnd", AvailabilitySupport.WINDOW_END.toString());
        result.put("windowLabel", AvailabilitySupport.WINDOW_LABEL);
        result.put("days", days);
        return result;
    }

    private void lock(String user) {
        db.queryForList("SELECT user_id FROM coser_profile WHERE user_id=? FOR UPDATE", user);
    }

    /** @return true when changed, false when skipped due to occupation in non-strict mode */
    private boolean setDay(String user, LocalDate day, boolean open, boolean strict) {
        Timestamp start = Timestamp.valueOf(day.atStartOfDay());
        Timestamp next = Timestamp.valueOf(day.plusDays(1).atStartOfDay());
        int occupied = availability.occupiedCountOnDay(user, day);
        if (occupied > 0) {
            if (!open) {
                if (strict) throw new ApiException(HttpStatus.CONFLICT, day + "已有进行中的委托，不能设为不可约");
                return false;
            }
            Integer canonical = db.queryForObject("SELECT COUNT(*) FROM availability_slot WHERE coser_id=? " +
                "AND start_at=? AND end_at=? AND status='open'", Integer.class, user,
                availability.dayWindowStart(day), availability.dayWindowEnd(day));
            if (canonical == null || canonical == 0) {
                if (strict) throw new ApiException(HttpStatus.CONFLICT, day + "已有委托占档，不能重设该日档期");
                return false;
            }
            return false;
        }
        db.update("UPDATE availability_slot SET status='closed',version=version+1,updated_at=CURRENT_TIMESTAMP " +
            "WHERE coser_id=? AND start_at>=? AND start_at<? AND status='open'", user, start, next);
        if (open) {
            List<String> existing = db.queryForList("SELECT id FROM availability_slot WHERE coser_id=? AND start_at=? AND end_at=? " +
                "ORDER BY id LIMIT 1", String.class, user, availability.dayWindowStart(day), availability.dayWindowEnd(day));
            if (existing.isEmpty()) db.update("INSERT INTO availability_slot(id,coser_id,start_at,end_at,status) " +
                "VALUES (?,?,?,?,'open')", "slot-" + UUID.randomUUID(), user,
                availability.dayWindowStart(day), availability.dayWindowEnd(day));
            else db.update("UPDATE availability_slot SET status='open',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?", existing.get(0));
        }
        return true;
    }

    @PutMapping("/{date}")
    @Transactional
    public Map<String, Object> set(@RequestHeader(value="Authorization", required=false) String bearer,
            @RequestHeader(value="X-Demo-User", required=false) String demo, @PathVariable String date,
            @RequestBody Map<String, Object> body) {
        String user = coser(bearer, demo);
        if (!(body.get("open") instanceof Boolean)) throw new ApiException(HttpStatus.BAD_REQUEST, "open必须是布尔值");
        LocalDate day = date(date);
        lock(user);
        boolean open = (Boolean) body.get("open");
        setDay(user, day, open, true);
        int occupied = availability.occupiedCountOnDay(user, day);
        return Map.of("date", day.toString(), "open", open, "status", occupied > 0 ? "occupied" : (open ? "open" : "closed"),
            "occupiedCount", occupied, "editable", occupied == 0);
    }

    @PostMapping("/batch")
    @Transactional
    public Map<String, Object> batch(@RequestHeader(value="Authorization", required=false) String bearer,
            @RequestHeader(value="X-Demo-User", required=false) String demo, @RequestBody Map<String, Object> body) {
        String user = coser(bearer, demo);
        if (!(body.get("month") instanceof String)) throw new ApiException(HttpStatus.BAD_REQUEST, "month不能为空");
        YearMonth target = month((String) body.get("month"));
        String type = body.get("type") instanceof String ? (String) body.get("type") : "";
        if (!List.of("all_open", "all_closed", "weekdays_open", "weekends_open").contains(type))
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的批量设置类型");
        lock(user);
        int changed = 0, skipped = 0;
        for (int day = 1; day <= target.lengthOfMonth(); day++) {
            LocalDate date = target.atDay(day);
            if (date.isBefore(LocalDate.now(AvailabilitySupport.ZONE))) continue;
            boolean weekday = date.getDayOfWeek().getValue() <= 5;
            boolean match = type.equals("all_open") || type.equals("all_closed") ||
                (type.equals("weekdays_open") && weekday) || (type.equals("weekends_open") && !weekday);
            if (!match) continue;
            if (setDay(user, date, !type.equals("all_closed"), false)) changed++;
            else skipped++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", target.toString());
        result.put("type", type);
        result.put("changedDays", changed);
        result.put("skippedDays", skipped);
        return result;
    }
}
