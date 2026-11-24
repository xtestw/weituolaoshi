package com.weituolaoshi.server;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AvailabilitySupport {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final LocalTime WINDOW_START = LocalTime.of(10, 0);
    public static final LocalTime WINDOW_END = LocalTime.of(22, 0);
    public static final String WINDOW_LABEL = "10:00–22:00";

    private final JdbcTemplate db;

    public AvailabilitySupport(JdbcTemplate db) { this.db = db; }

    public Timestamp dayWindowStart(LocalDate day) { return Timestamp.valueOf(day.atTime(WINDOW_START)); }
    public Timestamp dayWindowEnd(LocalDate day) { return Timestamp.valueOf(day.atTime(WINDOW_END)); }

    public void requireServiceWindow(LocalDateTime start, LocalDateTime end) {
        if (!start.toLocalDate().equals(end.toLocalDate())
            || start.getMinute() != 0 || start.getSecond() != 0 || start.getNano() != 0
            || end.getMinute() != 0 || end.getSecond() != 0 || end.getNano() != 0
            || start.toLocalTime().isBefore(WINDOW_START)
            || end.toLocalTime().isAfter(WINDOW_END)
            || !end.isAfter(start)
            || !start.isAfter(LocalDateTime.now(ZONE)))
            throw new ApiException(HttpStatus.BAD_REQUEST,
                "仅支持未来上海时间" + WINDOW_LABEL + "内的非过夜整点时段");
    }

    public List<Map<String, Object>> openWindows(String coserId, Timestamp start, Timestamp end) {
        return db.queryForList("SELECT id, start_at AS \"startAt\", end_at AS \"endAt\" FROM availability_slot " +
            "WHERE coser_id=? AND status='open' AND start_at<? AND end_at>? ORDER BY start_at, id",
            coserId, end, start);
    }

    public List<Map<String, Object>> occupiedIntervals(String coserId, Timestamp start, Timestamp end) {
        return db.queryForList(
            "SELECT occupied_start_at AS \"startAt\", occupied_end_at AS \"endAt\", status, order_id AS \"orderId\" " +
            "FROM order_appointment WHERE coser_id=? AND occupied_start_at<? AND occupied_end_at>? AND " +
            "(status IN ('confirmed','in_service') OR (status='held' AND hold_expires_at>CURRENT_TIMESTAMP) OR " +
            "(status='awaiting_coser' AND confirm_expires_at>CURRENT_TIMESTAMP)) ORDER BY occupied_start_at",
            coserId, end, start);
    }

    public int occupiedCountOnDay(String coserId, LocalDate day) {
        Timestamp start = Timestamp.valueOf(day.atStartOfDay());
        Timestamp next = Timestamp.valueOf(day.plusDays(1).atStartOfDay());
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM order_appointment WHERE coser_id=? AND occupied_start_at<? AND occupied_end_at>? AND " +
            "(status IN ('confirmed','in_service') OR (status='held' AND hold_expires_at>CURRENT_TIMESTAMP) OR " +
            "(status='awaiting_coser' AND confirm_expires_at>CURRENT_TIMESTAMP))",
            Integer.class, coserId, next, start);
        return count == null ? 0 : count;
    }

    public void requireBookable(String coserId, Timestamp start, Timestamp end) {
        List<String> windows = db.queryForList(
            "SELECT id FROM availability_slot WHERE coser_id=? AND status='open' AND start_at<=? AND end_at>=? ORDER BY id",
            String.class, coserId, start, end);
        if (windows.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "档期不可约");

        Integer orderConflicts = db.queryForObject(
            "SELECT COUNT(*) FROM booking_order WHERE coser_id=? AND start_at<? AND end_at>? " +
            "AND status NOT IN ('cancelled','refunded') " +
            "AND NOT (status='awaiting_payment' AND payment_expires_at<=CURRENT_TIMESTAMP)",
            Integer.class, coserId, end, start);
        if (orderConflicts != null && orderConflicts > 0)
            throw new ApiException(HttpStatus.CONFLICT, "档期已被其他订单占用");

        Map<String, Object> policy = db.queryForMap(
            "SELECT buffer_before_minutes, buffer_after_minutes FROM coser_profile WHERE user_id=?", coserId);
        int before = ((Number) policy.get("buffer_before_minutes")).intValue();
        int after = ((Number) policy.get("buffer_after_minutes")).intValue();
        Timestamp occupiedStart = Timestamp.valueOf(start.toLocalDateTime().minusMinutes(before));
        Timestamp occupiedEnd = Timestamp.valueOf(end.toLocalDateTime().plusMinutes(after));
        Integer overlaps = db.queryForObject(
            "SELECT COUNT(*) FROM order_appointment WHERE coser_id=? AND occupied_start_at<? AND occupied_end_at>? AND " +
            "(status IN ('confirmed','in_service') OR (status='held' AND hold_expires_at>CURRENT_TIMESTAMP) OR " +
            "(status='awaiting_coser' AND confirm_expires_at>CURRENT_TIMESTAMP))",
            Integer.class, coserId, occupiedEnd, occupiedStart);
        if (overlaps != null && overlaps > 0) throw new ApiException(HttpStatus.CONFLICT, "档期已被占用");
    }

    public List<Map<String, Object>> availableSlots(String coserId, LocalDateTime from) {
        Timestamp fromTs = Timestamp.valueOf(from);
        List<Map<String, Object>> opens = db.queryForList(
            "SELECT start_at AS \"startAt\", end_at AS \"endAt\" FROM availability_slot " +
            "WHERE coser_id=? AND status='open' AND end_at>? ORDER BY start_at",
            coserId, fromTs);
        if (opens.isEmpty()) return List.of();
        Timestamp rangeEnd = (Timestamp) opens.get(opens.size() - 1).get("endAt");
        List<Map<String, Object>> occupied = occupiedIntervals(coserId, fromTs, rangeEnd);
        List<Interval> free = subtract(toIntervals(opens), toIntervals(occupied));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Interval item : free) {
            LocalDateTime start = item.start.isBefore(from) ? from : item.start;
            if (!start.isBefore(item.end)) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("startAt", format(start));
            row.put("endAt", format(item.end));
            result.add(row);
        }
        return result;
    }

    private static String format(LocalDateTime value) {
        return value.withNano(0).toString();
    }

    private List<Interval> toIntervals(List<Map<String, Object>> rows) {
        List<Interval> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object start = row.get("startAt");
            Object end = row.get("endAt");
            LocalDateTime startAt = start instanceof Timestamp ? ((Timestamp) start).toLocalDateTime() : LocalDateTime.parse(String.valueOf(start));
            LocalDateTime endAt = end instanceof Timestamp ? ((Timestamp) end).toLocalDateTime() : LocalDateTime.parse(String.valueOf(end));
            if (endAt.isAfter(startAt)) result.add(new Interval(startAt, endAt));
        }
        result.sort(Comparator.comparing(item -> item.start));
        return result;
    }

    private List<Interval> subtract(List<Interval> opens, List<Interval> occupied) {
        List<Interval> current = new ArrayList<>(opens);
        for (Interval block : occupied) {
            List<Interval> next = new ArrayList<>();
            for (Interval open : current) {
                if (!block.end.isAfter(open.start) || !block.start.isBefore(open.end)) {
                    next.add(open);
                    continue;
                }
                if (block.start.isAfter(open.start)) next.add(new Interval(open.start, block.start));
                if (block.end.isBefore(open.end)) next.add(new Interval(block.end, open.end));
            }
            current = next;
        }
        return current;
    }

    private static final class Interval {
        final LocalDateTime start;
        final LocalDateTime end;
        Interval(LocalDateTime start, LocalDateTime end) { this.start = start; this.end = end; }
    }
}
