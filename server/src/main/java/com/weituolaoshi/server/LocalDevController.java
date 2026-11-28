package com.weituolaoshi.server;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@RestController
@Profile("local")
@RequestMapping("/v1/dev")
public class LocalDevController {
    private final JdbcTemplate db;
    private final AuthSupport auth;
    public LocalDevController(JdbcTemplate db, AuthSupport auth) { this.db=db; this.auth=auth; }

    @Bean CommandLineRunner seedLocalAvailability() { return args -> {
        LocalDate date=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(3);
        String id="dev-slot-"+date.toString();
        Integer count=db.queryForObject("SELECT COUNT(*) FROM availability_slot WHERE id=?",Integer.class,id);
        if(count==null||count==0) db.update("INSERT INTO availability_slot(id,coser_id,start_at,end_at,status) VALUES (?,? ,?,?,'open')",
            id,"demo-coser-f",Timestamp.valueOf(LocalDateTime.of(date, AvailabilitySupport.WINDOW_START)),
            Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
    }; }

    @PostMapping("/login")
    public Map<String,Object> login(@RequestBody(required=false) Map<String,Object> body) {
        String id=body!=null && "coser".equals(body.get("role"))?"demo-coser-f":"demo-customer";
        String token=UUID.randomUUID().toString()+UUID.randomUUID();
        db.update("INSERT INTO user_session(token_hash,user_id,expires_at) VALUES (?,?,?)",
            auth.hash(token),id,Timestamp.valueOf(LocalDateTime.now().plusHours(8)));
        return Map.of("token",token,"user",db.queryForMap("SELECT id,nickname,avatar_url AS \"avatarUrl\",role FROM app_user WHERE id=?",id));
    }

    @PostMapping("/orders/{id}/pay")
    @Transactional
    public Map<String,Object> simulatePaidOrder(@PathVariable String id,@RequestHeader("Authorization") String bearer) {
        String user=auth.user(bearer);
        List<Map<String,Object>> rows=db.queryForList("SELECT * FROM booking_order WHERE id=? AND customer_id=?",id,user);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"订单不存在");
        Map<String,Object> order=rows.get(0);
        if (!"awaiting_payment".equals(order.get("status")) ||
            ((Timestamp)order.get("payment_expires_at")).before(new Timestamp(System.currentTimeMillis())))
            throw new ApiException(HttpStatus.CONFLICT,"订单不可付款");
        Timestamp confirmExpiry=Timestamp.valueOf(LocalDateTime.now().plusHours(2));
        db.update("UPDATE booking_order SET status='awaiting_coser',payment_status='paid',paid_amount=payable_amount," +
            "confirm_expires_at=? WHERE id=?",confirmExpiry,id);
        db.update("UPDATE order_appointment SET status='awaiting_coser',confirm_expires_at=? WHERE order_id=?",confirmExpiry,id);
        db.update("INSERT INTO payment_transaction(id,order_id,provider_transaction_id,type,amount,status) " +
            "VALUES (?,?,?,'payment',?,'succeeded')","pay-"+UUID.randomUUID(),id,"local-"+UUID.randomUUID(),order.get("payable_amount"));
        db.update("INSERT INTO order_event(id,order_id,actor_id,action,from_status,to_status) " +
            "VALUES (?,?,?,'local_payment','awaiting_payment','awaiting_coser')","evt-"+UUID.randomUUID(),id,user);
        return db.queryForMap("SELECT * FROM booking_order WHERE id=?",id);
    }
}
