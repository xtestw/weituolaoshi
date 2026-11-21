package com.weituolaoshi.server;

import java.nio.file.*;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/messages")
public class MessageController {
    private final JdbcTemplate db; private final AuthSupport auth; private final MessageSocket socket;
    @Value("${messages.media-directory:./data/messages}") private String mediaDirectory;
    public MessageController(JdbcTemplate db,AuthSupport auth,MessageSocket socket) { this.db=db; this.auth=auth; this.socket=socket; }
    private String user(String header) { return auth.user(header); }
    private Map<String,Object> conversation(String id,String user) {
        List<Map<String,Object>> rows=db.queryForList("SELECT * FROM conversation WHERE id=? AND (customer_id=? OR coser_id=?)",id,user,user);
        if(rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"会话不存在"); return rows.get(0);
    }
    private Map<String,Object> message(String id) { return db.queryForMap("SELECT id,conversation_id AS \"conversationId\",sender_id AS \"senderId\",body,message_type AS \"type\",moderation_status AS \"status\",created_at AS \"createdAt\" FROM chat_message WHERE id=?",id); }
    private void notifyBoth(Map<String,Object> c) {
        Runnable send=()->{
            String id=(String)c.get("id");
            String customer=(String)c.get("customer_id");
            String coser=(String)c.get("coser_id");
            if(customer!=null) socket.notifyUser(customer,id);
            if(coser!=null) socket.notifyUser(coser,id);
            if("official".equals(c.get("kind"))) socket.notifyUser("official-platform",id);
        };
        if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { send.run(); } });
        else send.run();
    }
    @PostMapping("/conversations") @Transactional public Map<String,Object> create(@RequestHeader(value="Authorization",required=false) String header,@RequestBody Map<String,Object> body) {
        String me=user(header), service=String.valueOf(body.getOrDefault("serviceId","")), order=String.valueOf(body.getOrDefault("orderId",""));
        String customer,coser; String serviceId=null,orderId=null;
        if(!order.isBlank()) { List<Map<String,Object>> orders=db.queryForList("SELECT customer_id,coser_id,service_id FROM booking_order WHERE id=? AND (customer_id=? OR coser_id=?)",order,me,me); if(orders.isEmpty()) throw new ApiException(HttpStatus.FORBIDDEN,"无权访问订单"); Map<String,Object> o=orders.get(0); customer=(String)o.get("customer_id"); coser=(String)o.get("coser_id"); serviceId=(String)o.get("service_id"); orderId=order; }
        else { List<Map<String,Object>> services=db.queryForList("SELECT s.coser_id FROM service_post s JOIN coser_profile c ON c.user_id=s.coser_id WHERE s.id=? AND s.status='published' AND c.verification_status='verified'",service); if(services.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"服务不存在"); customer=me; coser=(String)services.get(0).get("coser_id"); serviceId=service; if(me.equals(coser)) throw new ApiException(HttpStatus.BAD_REQUEST,"不能与自己建立会话"); }
        List<String> existing=orderId==null?db.queryForList("SELECT id FROM conversation WHERE kind='peer' AND customer_id=? AND coser_id=? AND service_id=? AND order_id IS NULL",String.class,customer,coser,serviceId):db.queryForList("SELECT id FROM conversation WHERE kind='peer' AND order_id=?",String.class,orderId);
        if(!existing.isEmpty()) return Map.of("id",existing.get(0));
        String id="conv-"+UUID.randomUUID(); db.update("INSERT INTO conversation(id,customer_id,coser_id,service_id,order_id,kind) VALUES (?,?,?,?,?,'peer')",id,customer,coser,serviceId,orderId); return Map.of("id",id);
    }
    @GetMapping("/conversations") public List<Map<String,Object>> list(@RequestHeader(value="Authorization",required=false) String header) {
        String me=user(header);
        return db.queryForList(
            "SELECT c.id,c.kind,c.service_id AS \"serviceId\",c.order_id AS \"orderId\"," +
            "CASE WHEN c.kind='official' THEN ofc.nickname WHEN c.customer_id=? THEN co.nickname ELSE cu.nickname END AS name," +
            "CASE WHEN c.kind='official' THEN 'official-platform' WHEN c.customer_id=? THEN c.coser_id ELSE c.customer_id END AS \"peerId\"," +
            "(SELECT body FROM chat_message m WHERE m.conversation_id=c.id AND m.moderation_status='approved' ORDER BY m.created_at DESC,m.id DESC LIMIT 1) AS preview," +
            "(SELECT COUNT(*) FROM chat_message m WHERE m.conversation_id=c.id AND m.sender_id<>? AND m.moderation_status='approved' AND m.created_at>COALESCE((SELECT last_read_at FROM conversation_read r WHERE r.conversation_id=c.id AND r.user_id=?),TIMESTAMP '1970-01-01 00:00:00')) AS unread " +
            "FROM conversation c JOIN app_user cu ON cu.id=c.customer_id LEFT JOIN app_user co ON co.id=c.coser_id " +
            "LEFT JOIN app_user ofc ON ofc.id='official-platform' " +
            "WHERE c.customer_id=? OR c.coser_id=? " +
            "ORDER BY COALESCE((SELECT MAX(created_at) FROM chat_message WHERE conversation_id=c.id),c.created_at) DESC",
            me,me,me,me,me,me);
    }
    @GetMapping("/conversations/{id}/messages") public List<Map<String,Object>> history(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id,@RequestParam(required=false) String before) {
        String me=user(header); conversation(id,me); Timestamp cursor=Timestamp.valueOf(LocalDateTime.now().plusYears(1)); if(before!=null) try { cursor=Timestamp.valueOf(before.replace('T',' ')); } catch(Exception ex) { throw new ApiException(HttpStatus.BAD_REQUEST,"游标无效"); }
        return db.queryForList("SELECT id,conversation_id AS \"conversationId\",sender_id AS \"senderId\",body,message_type AS \"type\",moderation_status AS \"status\",created_at AS \"createdAt\" FROM chat_message WHERE conversation_id=? AND created_at<? AND (moderation_status='approved' OR sender_id=?) ORDER BY created_at DESC,id DESC LIMIT 50",id,cursor,me);
    }
    @PostMapping("/conversations/{id}/read") @Transactional public Map<String,Object> read(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id) { String me=user(header); conversation(id,me); if(db.update("UPDATE conversation_read SET last_read_at=CURRENT_TIMESTAMP WHERE conversation_id=? AND user_id=?",id,me)==0) db.update("INSERT INTO conversation_read(conversation_id,user_id,last_read_at) VALUES (?,?,CURRENT_TIMESTAMP)",id,me); return Map.of("ok",true); }
    @PostMapping("/conversations/{id}/messages") @Transactional public Map<String,Object> send(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id,@RequestBody Map<String,Object> body) {
        String me=user(header); Map<String,Object> c=conversation(id,me); Integer restricted=db.queryForObject("SELECT COUNT(*) FROM conversation_restriction WHERE conversation_id=? AND restricted=TRUE",Integer.class,id); if(restricted!=null&&restricted>0) throw new ApiException(HttpStatus.FORBIDDEN,"会话已被平台限制");
        String client=String.valueOf(body.getOrDefault("clientId","")); if(!client.matches("[A-Za-z0-9_-]{8,80}")) throw new ApiException(HttpStatus.BAD_REQUEST,"消息标识无效");
        List<String> old=db.queryForList("SELECT id FROM chat_message WHERE conversation_id=? AND sender_id=? AND client_id=?",String.class,id,me,client); if(!old.isEmpty()) return message(old.get(0));
        String type=String.valueOf(body.getOrDefault("type","text")), text=String.valueOf(body.getOrDefault("body","" )).trim();
        if(!"text".equals(type)||text.isEmpty()||text.length()>2000) throw new ApiException(HttpStatus.BAD_REQUEST,"请输入2000字以内的文字");
        boolean risky=text.matches("(?s).*(微信|vx|VX|加V|支付宝|银行卡|私下交易|站外支付|[1][3-9][0-9]{9}).*");
        String mid=UUID.randomUUID().toString(); db.update("INSERT INTO chat_message(id,conversation_id,sender_id,body,moderation_status,client_id,message_type) VALUES (?,?,?,?,?,?,?)",mid,id,me,text,risky?"pending":"approved",client,"text"); notifyBoth(c); return message(mid);
    }
    @PostMapping(value="/conversations/{id}/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public Map<String,Object> image(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id,@RequestParam("file") MultipartFile file) throws Exception {
        String me=user(header); Map<String,Object> c=conversation(id,me); Integer restricted=db.queryForObject("SELECT COUNT(*) FROM conversation_restriction WHERE conversation_id=? AND restricted=TRUE",Integer.class,id); if(restricted!=null&&restricted>0) throw new ApiException(HttpStatus.FORBIDDEN,"会话已被平台限制"); if(file.isEmpty()||file.getSize()>2_000_000) throw new ApiException(HttpStatus.BAD_REQUEST,"图片不能超过2MB"); String mime=file.getContentType(); if(!List.of("image/jpeg","image/png","image/webp").contains(mime)) throw new ApiException(HttpStatus.BAD_REQUEST,"仅支持JPEG、PNG或WebP");
        byte[] bytes=file.getBytes(); boolean jpeg=bytes.length>3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255; boolean png=bytes.length>8&&(bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71; boolean webp=bytes.length>12&&new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")&&new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP"); if(!(mime.equals("image/jpeg")&&jpeg||mime.equals("image/png")&&png||mime.equals("image/webp")&&webp)) throw new ApiException(HttpStatus.BAD_REQUEST,"图片内容无效");
        String mid=UUID.randomUUID().toString(); String ext=mime.equals("image/png")?".png":mime.equals("image/webp")?".webp":".jpg"; Path dir=Paths.get(mediaDirectory).toAbsolutePath().normalize(); Files.createDirectories(dir); Path target=dir.resolve(mid+ext); Files.write(target,bytes);
        db.update("INSERT INTO chat_message(id,conversation_id,sender_id,body,moderation_status,message_type,media_path) VALUES (?,?,?,'[图片]','pending','image',?)",mid,id,me,target.toString()); notifyBoth(c); return message(mid);
    }
    @GetMapping("/conversations/{id}/images/{mid}") public ResponseEntity<byte[]> imageData(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id,@PathVariable String mid) throws Exception {
        String me=user(header); conversation(id,me); List<Map<String,Object>> rows=db.queryForList("SELECT sender_id,moderation_status,media_path FROM chat_message WHERE id=? AND conversation_id=? AND message_type='image'",mid,id); if(rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"图片不存在"); Map<String,Object> row=rows.get(0); if(!"approved".equals(row.get("moderation_status"))&&!me.equals(row.get("sender_id"))) throw new ApiException(HttpStatus.FORBIDDEN,"图片审核中"); Path path=Paths.get((String)row.get("media_path")); String mime=path.toString().endsWith(".png")?"image/png":path.toString().endsWith(".webp")?"image/webp":"image/jpeg"; return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime)).body(Files.readAllBytes(path));
    }
    @PostMapping("/messages/{mid}/reports") public Map<String,Object> report(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String mid,@RequestBody Map<String,Object> body) { String me=user(header); List<String> conv=db.queryForList("SELECT conversation_id FROM chat_message WHERE id=?",String.class,mid); if(conv.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"消息不存在"); conversation(conv.get(0),me); String reason=String.valueOf(body.getOrDefault("reason","" )).trim(); if(reason.isBlank()||reason.length()>300) throw new ApiException(HttpStatus.BAD_REQUEST,"请输入举报原因"); db.update("INSERT INTO message_report(id,message_id,reporter_id,reason) VALUES (?,?,?,?)","rep-"+UUID.randomUUID(),mid,me,reason); return Map.of("ok",true); }
    @PostMapping("/conversations/{id}/confirm-time") @Transactional public Map<String,Object> confirmTime(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id,@RequestBody Map<String,Object> body) {
        String me=user(header); Map<String,Object> c=conversation(id,me); if(!me.equals(c.get("coser_id"))) throw new ApiException(HttpStatus.FORBIDDEN,"仅Coser可确认时间"); try { LocalDateTime start=LocalDateTime.parse(String.valueOf(body.get("startAt"))),end=LocalDateTime.parse(String.valueOf(body.get("endAt"))); if(!end.isAfter(start)||end.isAfter(start.plusHours(8))||start.isBefore(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")))) throw new Exception(); db.update("INSERT INTO time_confirmation(id,conversation_id,coser_id,start_at,end_at,expires_at) VALUES (?,?,?,?,?,?)","tc-"+UUID.randomUUID(),id,me,Timestamp.valueOf(start),Timestamp.valueOf(end),Timestamp.valueOf(LocalDateTime.now().plusHours(24))); notifyBoth(c); return Map.of("ok",true); } catch(Exception ex) { throw new ApiException(HttpStatus.BAD_REQUEST,"时间范围无效"); }
    }
}
