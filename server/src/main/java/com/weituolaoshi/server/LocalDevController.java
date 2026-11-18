package com.weituolaoshi.server;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

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
            id,"demo-coser-f",Timestamp.valueOf(LocalDateTime.of(date,LocalTime.of(10,0))),
            Timestamp.valueOf(LocalDateTime.of(date,LocalTime.of(22,0))));
    }; }

    @PostMapping("/login")
    public Map<String,Object> login() {
        String id="demo-customer";
        String token=UUID.randomUUID().toString()+UUID.randomUUID();
        db.update("INSERT INTO user_session(token_hash,user_id,expires_at) VALUES (?,?,?)",
            auth.hash(token),id,Timestamp.valueOf(LocalDateTime.now().plusHours(8)));
        return Map.of("token",token,"user",db.queryForMap("SELECT id,nickname,avatar_url AS \"avatarUrl\",role FROM app_user WHERE id=?",id));
    }
}
