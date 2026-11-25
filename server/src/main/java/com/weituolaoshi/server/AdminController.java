package com.weituolaoshi.server;

import java.sql.Timestamp;
import java.nio.file.*;
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
    private final MessageSocket messageSocket;
    private final IdentityGenderPolicy genderPolicy;
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder();
    @Value("${admin.bootstrap-user:}") private String bootstrapUser;
    @Value("${admin.bootstrap-password:}") private String bootstrapPassword;
    public AdminController(JdbcTemplate db,AuthSupport auth,MessageSocket messageSocket,IdentityGenderPolicy genderPolicy) { this.db=db; this.auth=auth; this.messageSocket=messageSocket; this.genderPolicy=genderPolicy; }
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
    @GetMapping("/users") public List<Map<String,Object>> users(@RequestHeader(value="Authorization",required=false) String header) {
        permitted(header,"users:read"); return db.queryForList("SELECT u.id,u.nickname,u.phone,u.bio,u.account_status AS \"accountStatus\",u.role,u.created_at AS \"createdAt\", " +
            "v.status AS \"identityStatus\",v.adult,v.legal_gender AS \"gender\"," + genderPolicy.sql("v.") + " AS \"genderEligible\",c.verification_status AS \"coserStatus\",c.display_name AS \"coserDisplayName\",c.city AS \"coserCity\" " +
            "FROM app_user u LEFT JOIN identity_verification v ON v.user_id=u.id LEFT JOIN coser_profile c ON c.user_id=u.id ORDER BY u.created_at DESC LIMIT 200");
    }
    @GetMapping("/coser-posts") public List<Map<String,Object>> coserPosts(@RequestHeader(value="Authorization",required=false) String header) {
        permitted(header,"services:read");
        return db.queryForList("SELECT s.id,s.coser_id AS \"coserId\",u.nickname AS \"coserNickname\",c.display_name AS \"coserName\", " +
            "s.title,s.description,s.city,s.place_name AS \"place\",s.cover_url AS \"coverUrl\",s.price_per_hour AS \"pricePerHour\", " +
            "s.min_hours AS \"minHours\",s.status,s.audit_status AS \"auditStatus\",s.visibility,s.manually_offline AS \"manuallyOffline\", " +
            "s.moderation_reason AS \"moderationReason\",s.moderated_at AS \"moderatedAt\",s.created_at AS \"createdAt\",s.updated_at AS \"updatedAt\" " +
            "FROM service_post s JOIN app_user u ON u.id=s.coser_id JOIN coser_profile c ON c.user_id=s.coser_id " +
            "WHERE s.content_kind='commission' AND s.deleted_at IS NULL ORDER BY s.created_at DESC LIMIT 200");
    }
    @PatchMapping("/coser-posts/{id}/moderation") @Transactional
    public Map<String,Object> moderateCoserPost(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id,@RequestBody Map<String,Object> body) {
        String actor=permitted(header,"services:moderate"),action=Objects.toString(body.get("action"),"");
        if(!List.of("offline","restore").contains(action)) throw new ApiException(HttpStatus.BAD_REQUEST,"操作无效");
        int changed;
        if("offline".equals(action)) {
            String reason=Objects.toString(body.get("reason"),"").trim();
            if(reason.isBlank()||reason.length()>500) throw new ApiException(HttpStatus.BAD_REQUEST,"请填写500字以内的下线原因");
            changed=db.update("UPDATE service_post SET manually_offline=TRUE,visibility='private',audit_status='rejected',moderation_reason=?,moderated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP " +
                "WHERE id=? AND content_kind='commission' AND status='published' AND visibility='public' AND audit_status='approved' AND deleted_at IS NULL AND manually_offline=FALSE",reason,id);
            if(changed==0) throw new ApiException(HttpStatus.CONFLICT,"帖子不存在或当前不可下线");
            audit(actor,"coser_post.offline",id,reason);
        } else {
            changed=db.update("UPDATE service_post SET manually_offline=FALSE,visibility='public',audit_status='approved',moderation_reason=NULL,moderated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP " +
                "WHERE id=? AND content_kind='commission' AND status='published' AND deleted_at IS NULL AND manually_offline=TRUE",id);
            if(changed==0) throw new ApiException(HttpStatus.CONFLICT,"帖子不存在或当前不可恢复");
            audit(actor,"coser_post.restore",id,"");
        }
        return Map.of("ok",true,"id",id,"action",action);
    }
    @PatchMapping("/users/{id}") @Transactional
    public Map<String,Object> editUser(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id,@RequestBody Map<String,Object> body) {
        String actor=permitted(header,"users:write");
        List<Map<String,Object>> targets=db.queryForList("SELECT id,account_status FROM app_user WHERE id=? AND role<>'official'",id);
        if(targets.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"用户不存在");
        if("closed".equals(targets.get(0).get("account_status"))) throw new ApiException(HttpStatus.CONFLICT,"已注销账号不可编辑");
        Set<String> allowed=Set.of("nickname","phone","bio","accountStatus","coserDisplayName","coserCity");
        if(body.isEmpty()||!allowed.containsAll(body.keySet())) throw new ApiException(HttpStatus.BAD_REQUEST,"编辑字段无效");
        if(body.containsKey("nickname")) {
            String value=Objects.toString(body.get("nickname"),"").trim();
            if(value.isBlank()||value.length()>30) throw new ApiException(HttpStatus.BAD_REQUEST,"昵称需为1到30字");
            db.update("UPDATE app_user SET nickname=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",value,id);
        }
        if(body.containsKey("phone")) {
            String value=Objects.toString(body.get("phone"),"").trim();
            if(!value.isEmpty()&&!value.matches("1[3-9]\\d{9}")) throw new ApiException(HttpStatus.BAD_REQUEST,"手机号格式不正确");
            db.update("UPDATE app_user SET phone=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",value.isEmpty()?null:value,id);
        }
        if(body.containsKey("bio")) {
            String value=Objects.toString(body.get("bio"),"").trim();
            if(value.length()>500) throw new ApiException(HttpStatus.BAD_REQUEST,"简介最多500字");
            db.update("UPDATE app_user SET bio=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",value,id);
        }
        if(body.containsKey("accountStatus")) {
            String value=Objects.toString(body.get("accountStatus"),"");
            if(!List.of("active","frozen").contains(value)) throw new ApiException(HttpStatus.BAD_REQUEST,"账号状态无效");
            db.update("UPDATE app_user SET account_status=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND account_status<>'closed'",value,id);
        }
        if(body.containsKey("coserDisplayName")||body.containsKey("coserCity")) {
            if(db.queryForList("SELECT user_id FROM coser_profile WHERE user_id=?",String.class,id).isEmpty()) throw new ApiException(HttpStatus.CONFLICT,"该用户尚无Coser资料");
            if(body.containsKey("coserDisplayName")) {
                String value=Objects.toString(body.get("coserDisplayName"),"").trim();
                if(value.isBlank()||value.length()>100) throw new ApiException(HttpStatus.BAD_REQUEST,"Coser名称需为1到100字");
                db.update("UPDATE coser_profile SET display_name=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",value,id);
            }
            if(body.containsKey("coserCity")) {
                String value=Objects.toString(body.get("coserCity"),"").trim();
                if(value.length()>80) throw new ApiException(HttpStatus.BAD_REQUEST,"城市最多80字");
                db.update("UPDATE coser_profile SET city=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",value,id);
            }
        }
        audit(actor,"user.update",id,String.join(",",body.keySet()));
        return Map.of("ok",true,"userId",id);
    }
    @PatchMapping("/users/{id}/coser-verification") @Transactional
    public Map<String,Object> certifyCoser(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id,@RequestBody Map<String,Object> body) {
        String actor=permitted(header,"identity:certify-coser");
        String status=Objects.toString(body.get("status"),"");
        if(!List.of("verified","unverified").contains(status)) throw new ApiException(HttpStatus.BAD_REQUEST,"认证状态无效");
        List<Map<String,Object>> users=db.queryForList("SELECT id,role,nickname FROM app_user WHERE id=?",id);
        if(users.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"用户不存在");
        if("official".equals(users.get(0).get("role"))) throw new ApiException(HttpStatus.BAD_REQUEST,"官号不能认证为Coser");
        if("unverified".equals(status)) {
            db.update("UPDATE coser_profile SET verification_status='unverified',service_status='inactive',updated_at=CURRENT_TIMESTAMP WHERE user_id=?",id);
            audit(actor,"coser.uncertify",id,"");
            return Map.of("ok",true,"userId",id,"coserStatus","unverified");
        }
        Integer eligible=db.queryForObject("SELECT COUNT(*) FROM identity_verification v WHERE v.user_id=? AND v.status='verified' AND v.adult=TRUE AND " + genderPolicy.sql("v."),Integer.class,id);
        if(eligible==null||eligible==0) throw new ApiException(HttpStatus.CONFLICT,"请先完成符合平台规则的成年实名认证，再由后台认证Coser");
        db.update("UPDATE identity_verification SET mode='coser' WHERE user_id=?",id);
        db.update("UPDATE app_user SET role='coser' WHERE id=?",id);
        Integer existing=db.queryForObject("SELECT COUNT(*) FROM coser_profile WHERE user_id=?",Integer.class,id);
        if(existing==null||existing==0) db.update("INSERT INTO coser_profile(user_id,display_name,city,verification_status,service_status) VALUES (?,?, '', 'verified','active')",id,
            Objects.toString(users.get(0).get("nickname"),"微信用户"));
        else db.update("UPDATE coser_profile SET verification_status='verified',service_status='active',updated_at=CURRENT_TIMESTAMP WHERE user_id=?",id);
        audit(actor,"coser.certify",id,"identity already verified");
        return Map.of("ok",true,"userId",id,"coserStatus","verified");
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
    @GetMapping("/messages") public List<Map<String,Object>> messages(@RequestHeader(value="Authorization",required=false) String header) {
        permitted(header,"messages:read"); return db.queryForList("SELECT m.id,m.conversation_id AS \"conversationId\",m.sender_id AS \"senderId\",m.body,m.message_type AS \"type\",m.moderation_status AS \"status\",m.moderation_reason AS \"reason\",m.created_at AS \"createdAt\" FROM chat_message m ORDER BY m.created_at DESC LIMIT 200");
    }
    @GetMapping("/messages/{id}/image") public org.springframework.http.ResponseEntity<byte[]> messageImage(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id) throws Exception {
        permitted(header,"messages:read"); List<Map<String,Object>> rows=db.queryForList("SELECT media_path FROM chat_message WHERE id=? AND message_type='image'",id); if(rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"图片不存在");
        Path path=Paths.get((String)rows.get(0).get("media_path")); String mime=path.toString().endsWith(".png")?"image/png":path.toString().endsWith(".webp")?"image/webp":"image/jpeg"; return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(mime)).body(Files.readAllBytes(path));
    }
    @GetMapping("/message-reports") public List<Map<String,Object>> messageReports(@RequestHeader(value="Authorization",required=false) String header) {
        permitted(header,"messages:read"); return db.queryForList("SELECT r.id,r.message_id AS \"messageId\",r.reporter_id AS \"reporterId\",r.reason,r.status,r.created_at AS \"createdAt\" FROM message_report r ORDER BY r.created_at DESC LIMIT 200");
    }
    @PatchMapping("/messages/{id}") @Transactional public Map<String,Object> moderateMessage(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id,@RequestBody Map<String,Object> body) {
        String actor=permitted(header,"messages:moderate"), decision=String.valueOf(body.getOrDefault("decision","")), reason=String.valueOf(body.getOrDefault("reason","")).trim();
        if(!List.of("approved","rejected").contains(decision)||reason.length()>300) throw new ApiException(HttpStatus.BAD_REQUEST,"审核参数无效");
        if(db.update("UPDATE chat_message SET moderation_status=?,moderation_reason=?,created_at=CASE WHEN moderation_status='pending' AND ?='approved' THEN CURRENT_TIMESTAMP ELSE created_at END WHERE id=?",decision,reason,decision,id)==0) throw new ApiException(HttpStatus.NOT_FOUND,"消息不存在");
        db.update("UPDATE message_report SET status='closed' WHERE message_id=?",id); audit(actor,"message.moderate",id,decision+" "+reason);
        Map<String,Object> c=db.queryForMap("SELECT c.id,c.customer_id,c.coser_id FROM conversation c JOIN chat_message m ON m.conversation_id=c.id WHERE m.id=?",id);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void afterCommit() {
                String customer=(String)c.get("customer_id"), coser=(String)c.get("coser_id"), conversationId=(String)c.get("id");
                if(customer!=null) messageSocket.notifyUser(customer,conversationId);
                if(coser!=null) messageSocket.notifyUser(coser,conversationId);
            }
        });
        return Map.of("ok",true);
    }
    @PatchMapping("/conversations/{id}/restriction") @Transactional public Map<String,Object> restrictConversation(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id,@RequestBody Map<String,Object> body) {
        String actor=permitted(header,"messages:moderate"),reason=String.valueOf(body.getOrDefault("reason","")).trim(); boolean restricted=Boolean.TRUE.equals(body.get("restricted"));
        if(reason.length()>300) throw new ApiException(HttpStatus.BAD_REQUEST,"原因过长");
        if(db.queryForList("SELECT id FROM conversation WHERE id=?",String.class,id).isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"会话不存在");
        if(db.update("UPDATE conversation_restriction SET restricted=?,reason=?,updated_at=CURRENT_TIMESTAMP WHERE conversation_id=?",restricted,reason,id)==0) db.update("INSERT INTO conversation_restriction(conversation_id,restricted,reason) VALUES (?,?,?)",id,restricted,reason);
        audit(actor,"conversation.restrict",id,restricted+" "+reason); return Map.of("ok",true);
    }

    @GetMapping("/official-messages")
    public List<Map<String,Object>> officialMessages(@RequestHeader(value="Authorization",required=false) String header) {
        permitted(header,"messages:send");
        return db.queryForList(
            "SELECT m.id,m.conversation_id AS \"conversationId\",c.customer_id AS \"userId\",u.nickname," +
            "m.body,m.created_at AS \"createdAt\" FROM chat_message m " +
            "JOIN conversation c ON c.id=m.conversation_id AND c.kind='official' " +
            "JOIN app_user u ON u.id=c.customer_id " +
            "WHERE m.sender_id='official-platform' ORDER BY m.created_at DESC LIMIT 200");
    }

    @PostMapping("/official-messages")
    @Transactional
    public Map<String,Object> sendOfficialMessage(@RequestHeader(value="Authorization",required=false) String header,
            @RequestBody Map<String,Object> body) {
        String actor=permitted(header,"messages:send");
        String userId=String.valueOf(body.getOrDefault("userId","")).trim();
        String text=String.valueOf(body.getOrDefault("body","")).trim();
        if(userId.isBlank()||"official-platform".equals(userId)) throw new ApiException(HttpStatus.BAD_REQUEST,"目标用户无效");
        if(text.isEmpty()||text.length()>2000) throw new ApiException(HttpStatus.BAD_REQUEST,"请输入2000字以内的文字");
        List<Map<String,Object>> users=db.queryForList("SELECT id FROM app_user WHERE id=? AND role<>'official'",userId);
        if(users.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"用户不存在");
        List<String> existing=db.queryForList("SELECT id FROM conversation WHERE kind='official' AND official_target_id=?",String.class,userId);
        String conversationId;
        if(existing.isEmpty()) {
            conversationId="conv-"+UUID.randomUUID();
            db.update("INSERT INTO conversation(id,customer_id,coser_id,service_id,order_id,kind,official_target_id) VALUES (?,?,NULL,NULL,NULL,'official',?)",conversationId,userId,userId);
        } else conversationId=existing.get(0);
        String messageId=UUID.randomUUID().toString();
        db.update("INSERT INTO chat_message(id,conversation_id,sender_id,body,moderation_status,message_type) VALUES (?,?,?,?,'approved','text')",
            messageId,conversationId,"official-platform",text);
        audit(actor,"official.message.send",userId,conversationId);
        String notifyUserId=userId, notifyConversationId=conversationId;
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { messageSocket.notifyUser(notifyUserId,notifyConversationId); }
            });
        return Map.of("id",messageId,"conversationId",conversationId,"userId",userId);
    }

    @GetMapping("/order-exceptions")
    public List<Map<String,Object>> orderExceptions(@RequestHeader(value="Authorization",required=false) String header) {
        permitted(header,"orders:read");
        return db.queryForList("SELECT e.id,e.order_id AS \"orderId\",o.coser_id AS \"coserId\",o.customer_id AS \"customerId\"," +
            "o.service_title_snapshot AS \"title\",e.reason,e.status,e.responsible_party AS \"responsibleParty\"," +
            "e.fulfillment_incomplete AS \"fulfillmentIncomplete\",e.review_note AS \"reviewNote\"," +
            "e.created_at AS \"createdAt\",e.reviewed_at AS \"reviewedAt\" FROM order_exception e " +
            "JOIN booking_order o ON o.id=e.order_id ORDER BY e.created_at DESC LIMIT 200");
    }

    @PatchMapping("/order-exceptions/{id}")
    @Transactional
    public Map<String,Object> reviewException(@RequestHeader(value="Authorization",required=false) String header,
            @PathVariable String id,@RequestBody Map<String,Object> body) {
        String actor=permitted(header,"orders:review");
        String decision=String.valueOf(body.getOrDefault("decision","")).trim();
        String party=String.valueOf(body.getOrDefault("responsibleParty","")).trim();
        String note=String.valueOf(body.getOrDefault("reviewNote","")).trim();
        boolean incomplete=Boolean.TRUE.equals(body.get("fulfillmentIncomplete"));
        if(!List.of("approved","rejected","closed").contains(decision)) throw new ApiException(HttpStatus.BAD_REQUEST,"审核结论无效");
        if("approved".equals(decision) && !List.of("coser","customer","none","both").contains(party))
            throw new ApiException(HttpStatus.BAD_REQUEST,"责任方无效");
        if(!"approved".equals(decision)) { party=null; incomplete=false; }
        if(note.length()>500) throw new ApiException(HttpStatus.BAD_REQUEST,"审核备注过长");
        if(db.update("UPDATE order_exception SET status=?,responsible_party=?,fulfillment_incomplete=?,review_note=?," +
            "reviewed_at=CURRENT_TIMESTAMP,reviewed_by=? WHERE id=? AND status='pending'",
            decision,party,incomplete,note,actor,id)==0)
            throw new ApiException(HttpStatus.CONFLICT,"异常工单不存在或已审核");
        audit(actor,"order.exception.review",id,decision+" "+party);
        return Map.of("ok",true);
    }

    @GetMapping("/support-tickets")
    public List<Map<String,Object>> supportTickets(@RequestHeader(value="Authorization",required=false) String header) {
        permitted(header,"support:read");
        return db.queryForList("SELECT t.id,t.requester_id AS \"requesterId\",t.role_snapshot AS \"role\",t.category," +
            "t.subject,t.body,t.order_id AS \"orderId\",t.status,t.created_at AS \"createdAt\" " +
            "FROM support_ticket t ORDER BY t.created_at DESC LIMIT 200");
    }

    @PatchMapping("/support-tickets/{id}")
    @Transactional
    public Map<String,Object> updateSupportTicket(@RequestHeader(value="Authorization",required=false) String header,
            @PathVariable String id,@RequestBody Map<String,Object> body) {
        String actor=permitted(header,"support:write");
        String status=String.valueOf(body.getOrDefault("status","")).trim();
        if(!List.of("open","in_progress","resolved","closed").contains(status))
            throw new ApiException(HttpStatus.BAD_REQUEST,"工单状态无效");
        if(db.update("UPDATE support_ticket SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",status,id)==0)
            throw new ApiException(HttpStatus.NOT_FOUND,"工单不存在");
        audit(actor,"support.ticket.update",id,status);
        return Map.of("ok",true);
    }
}
