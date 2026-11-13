package com.weituolaoshi.server;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/api")
public class AdminController {
    private final JdbcTemplate db;
    private final AuthSupport auth;
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder();
    @Value("${admin.bootstrap-user:}") private String bootstrapUser;
    @Value("${admin.bootstrap-password:}") private String bootstrapPassword;
    public AdminController(JdbcTemplate db,AuthSupport auth) { this.db=db; this.auth=auth; }
    @Bean CommandLineRunner bootstrapAdmin() { return args -> {
        Integer count=db.queryForObject("SELECT COUNT(*) FROM admin_user",Integer.class);
        if(count==0&&!bootstrapUser.isBlank()&&bootstrapPassword.length()>=12) {
            String id="adm-"+UUID.randomUUID();
            db.update("INSERT INTO admin_user(id,username,password_hash) VALUES (?,?,?)",id,bootstrapUser,passwords.encode(bootstrapPassword));
            db.update("INSERT INTO admin_user_role(user_id,role_id) VALUES (?,'super_admin')",id);
        }
    }; }
    private void audit(String actor,String action,String target,String detail) {
        db.update("INSERT INTO admin_audit(id,actor_id,action,target_id,detail) VALUES (?,?,?,?,?)","aud-"+UUID.randomUUID(),actor,action,target,detail);
    }
    private String permitted(String header,String permission) { String id=auth.admin(header); auth.require(id,permission); return id; }
    @PostMapping("/login") public Map<String,Object> login(@RequestBody Map<String,Object> body) {
        String username=String.valueOf(body.getOrDefault("username","")); String password=String.valueOf(body.getOrDefault("password",""));
        List<Map<String,Object>> rows=db.queryForList("SELECT id,password_hash,enabled FROM admin_user WHERE username=?",username);
        if(rows.isEmpty()||!Boolean.TRUE.equals(rows.get(0).get("enabled"))||!passwords.matches(password,(String)rows.get(0).get("password_hash")))
            throw new ApiException(HttpStatus.UNAUTHORIZED,"账号或密码错误");
        String id=(String)rows.get(0).get("id"), token=UUID.randomUUID().toString()+UUID.randomUUID();
        db.update("INSERT INTO admin_session(token_hash,user_id,expires_at) VALUES (?,?,?)",auth.hash(token),id,Timestamp.valueOf(LocalDateTime.now().plusHours(8)));
        audit(id,"login",id,""); return Map.of("token",token,"profile",profile(id));
    }
    private Map<String,Object> profile(String id) {
        Map<String,Object> result=new HashMap<>(db.queryForMap("SELECT id,username FROM admin_user WHERE id=?",id));
        result.put("roles",db.queryForList("SELECT r.id FROM admin_role r JOIN admin_user_role ur ON ur.role_id=r.id WHERE ur.user_id=?",String.class,id));
        result.put("permissions",db.queryForList("SELECT DISTINCT rp.permission_code FROM admin_user_role ur JOIN admin_role_permission rp ON ur.role_id=rp.role_id WHERE ur.user_id=?",String.class,id));
        return result;
    }
    @GetMapping("/me") public Map<String,Object> me(@RequestHeader(value="Authorization",required=false) String header) { return profile(auth.admin(header)); }
    @PostMapping("/logout") public Map<String,Object> logout(@RequestHeader(value="Authorization",required=false) String header) {
        String id=auth.admin(header); db.update("DELETE FROM admin_session WHERE token_hash=?",auth.hash(header.substring(7))); audit(id,"logout",id,""); return Map.of("ok",true);
    }
    @GetMapping("/identities") public List<Map<String,Object>> identities(@RequestHeader(value="Authorization",required=false) String header) {
        permitted(header,"identity:read");
        return db.queryForList("SELECT v.id,v.user_id AS \"userId\",u.nickname,v.mode,v.status,v.masked_document_number AS \"maskedId\",v.legal_gender AS \"gender\",v.review_reason AS \"reason\",v.created_at AS \"createdAt\" FROM identity_verification v JOIN app_user u ON u.id=v.user_id ORDER BY v.created_at DESC LIMIT 200");
    }
    @PatchMapping("/identities/{id}") @Transactional
    public Map<String,Object> review(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id,@RequestBody Map<String,Object> body) {
        String actor=permitted(header,"identity:review"); String decision=String.valueOf(body.get("decision"));
        String reason=body.get("reason") instanceof String?((String)body.get("reason")).trim():"";
        if(!decision.equals("verified")&&!decision.equals("rejected")) throw new ApiException(HttpStatus.BAD_REQUEST,"无效审核结果");
        if(decision.equals("rejected")&&(reason.isBlank()||reason.length()>500)) throw new ApiException(HttpStatus.BAD_REQUEST,"请填写500字以内的驳回原因");
        int changed=db.update("UPDATE identity_verification SET status=?,review_reason=?,verified_at=? WHERE id=? AND status='pending' AND mode='coser'",decision,reason,
            decision.equals("verified")?Timestamp.valueOf(LocalDateTime.now()):null,id);
        if(changed==0) throw new ApiException(HttpStatus.CONFLICT,"该申请不存在或已审核");
        if(decision.equals("verified")) db.update("UPDATE app_user SET role='coser' WHERE id=(SELECT user_id FROM identity_verification WHERE id=?)",id);
        audit(actor,"identity.review",id,decision+" "+reason);
        return Map.of("ok",true);
    }
    @GetMapping("/users") public List<Map<String,Object>> users(@RequestHeader(value="Authorization",required=false) String header) {
        permitted(header,"users:read"); return db.queryForList("SELECT id,nickname,role,created_at AS \"createdAt\" FROM app_user ORDER BY created_at DESC LIMIT 200");
    }
    @GetMapping("/admins") public List<Map<String,Object>> admins(@RequestHeader(value="Authorization",required=false) String header) {
        permitted(header,"admins:read"); return db.queryForList("SELECT id,username,enabled FROM admin_user ORDER BY username");
    }
    @GetMapping("/roles") public List<Map<String,Object>> roles(@RequestHeader(value="Authorization",required=false) String header) {
        permitted(header,"admins:read"); List<Map<String,Object>> roles=db.queryForList("SELECT id,name FROM admin_role ORDER BY name");
        for(Map<String,Object> role:roles) role.put("permissions",db.queryForList("SELECT permission_code FROM admin_role_permission WHERE role_id=?",String.class,role.get("id")));
        return roles;
    }
    @GetMapping("/permissions") public List<Map<String,Object>> permissions(@RequestHeader(value="Authorization",required=false) String header) {
        permitted(header,"admins:read"); return db.queryForList("SELECT code,description FROM admin_permission ORDER BY code");
    }
    @PostMapping("/roles") @Transactional public Map<String,Object> createRole(@RequestHeader(value="Authorization",required=false) String header,@RequestBody Map<String,Object> body) {
        String actor=permitted(header,"admins:write"),id=String.valueOf(body.getOrDefault("id","")),name=String.valueOf(body.getOrDefault("name",""));
        if(!id.matches("[a-z_]{3,40}")||name.isBlank()||name.length()>80) throw new ApiException(HttpStatus.BAD_REQUEST,"角色信息不合法");
        db.update("INSERT INTO admin_role(id,name) VALUES (?,?)",id,name);
        audit(actor,"role.create",id,name); return Map.of("id",id,"name",name);
    }
    @PutMapping("/roles/{id}/permissions") @Transactional public Map<String,Object> setPermissions(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id,@RequestBody Map<String,Object> body) {
        String actor=permitted(header,"admins:write");
        if(id.equals("super_admin")) throw new ApiException(HttpStatus.BAD_REQUEST,"超级管理员权限不可修改");
        if(db.queryForList("SELECT id FROM admin_role WHERE id=?",String.class,id).isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"角色不存在");
        if(!(body.get("permissions") instanceof List)) throw new ApiException(HttpStatus.BAD_REQUEST,"权限列表无效");
        List<?> requested=(List<?>)body.get("permissions");
        for(Object code:requested) if(!(code instanceof String)||db.queryForList("SELECT code FROM admin_permission WHERE code=?",String.class,code).isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,"权限不存在");
        db.update("DELETE FROM admin_role_permission WHERE role_id=?",id);
        for(Object code:new LinkedHashSet<>(requested)) db.update("INSERT INTO admin_role_permission(role_id,permission_code) VALUES (?,?)",id,code);
        db.update("DELETE FROM admin_session WHERE user_id IN (SELECT user_id FROM admin_user_role WHERE role_id=?)",id);
        audit(actor,"role.permissions",id,String.join(",",requested.stream().map(String::valueOf).toArray(String[]::new)));
        return Map.of("ok",true);
    }
    @PostMapping("/admins") @Transactional public Map<String,Object> createAdmin(@RequestHeader(value="Authorization",required=false) String header,@RequestBody Map<String,Object> body) {
        String actor=permitted(header,"admins:write"),username=String.valueOf(body.getOrDefault("username","")),password=String.valueOf(body.getOrDefault("password","")),role=String.valueOf(body.getOrDefault("role",""));
        if(!username.matches("[A-Za-z0-9_]{3,40}")||password.length()<12||db.queryForList("SELECT id FROM admin_role WHERE id=?",String.class,role).isEmpty())
            throw new ApiException(HttpStatus.BAD_REQUEST,"用户名、密码或角色不合法");
        String id="adm-"+UUID.randomUUID(); db.update("INSERT INTO admin_user(id,username,password_hash) VALUES (?,?,?)",id,username,passwords.encode(password));
        db.update("INSERT INTO admin_user_role(user_id,role_id) VALUES (?,?)",id,role); audit(actor,"admin.create",id,role); return profile(id);
    }
    @PatchMapping("/admins/{id}") @Transactional public Map<String,Object> changeAdmin(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id,@RequestBody Map<String,Object> body) {
        String actor=permitted(header,"admins:write"); if(actor.equals(id)) throw new ApiException(HttpStatus.BAD_REQUEST,"不能修改自己的权限");
        if(db.queryForList("SELECT id FROM admin_user WHERE id=?",String.class,id).isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"管理员不存在");
        Integer superCount=db.queryForObject("SELECT COUNT(*) FROM admin_user u JOIN admin_user_role ur ON u.id=ur.user_id WHERE ur.role_id='super_admin' AND u.enabled=TRUE",Integer.class);
        boolean isSuper=!db.queryForList("SELECT role_id FROM admin_user_role WHERE user_id=? AND role_id='super_admin'",String.class,id).isEmpty();
        if(isSuper && superCount!=null && superCount<=1 && (Boolean.FALSE.equals(body.get("enabled")) || (body.containsKey("role")&&!"super_admin".equals(body.get("role")))))
            throw new ApiException(HttpStatus.CONFLICT,"不能移除最后一名超级管理员");
        if(body.containsKey("enabled")) db.update("UPDATE admin_user SET enabled=? WHERE id=?",Boolean.TRUE.equals(body.get("enabled")),id);
        if(body.containsKey("role")) { String role=String.valueOf(body.get("role")); if(db.queryForList("SELECT id FROM admin_role WHERE id=?",String.class,role).isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,"角色不存在"); db.update("DELETE FROM admin_user_role WHERE user_id=?",id); db.update("INSERT INTO admin_user_role(user_id,role_id) VALUES (?,?)",id,role); }
        if(body.containsKey("password")) { String password=String.valueOf(body.get("password")); if(password.length()<12) throw new ApiException(HttpStatus.BAD_REQUEST,"密码至少12位"); db.update("UPDATE admin_user SET password_hash=? WHERE id=?",passwords.encode(password),id); }
        db.update("DELETE FROM admin_session WHERE user_id=?",id); audit(actor,"admin.update",id,String.valueOf(body.keySet())); return profile(id);
    }
    @GetMapping("/audit") public List<Map<String,Object>> auditLog(@RequestHeader(value="Authorization",required=false) String header) {
        permitted(header,"audit:read"); return db.queryForList("SELECT a.action,a.target_id AS \"targetId\",a.detail,a.created_at AS \"createdAt\",u.username FROM admin_audit a JOIN admin_user u ON u.id=a.actor_id ORDER BY a.created_at DESC LIMIT 200");
    }
}
