package com.weituolaoshi.server;

import java.util.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/api/identity-whitelist")
public class IdentityWhitelistController {
    private final JdbcTemplate db;
    private final AuthSupport auth;
    @Value("${identity.digest-key:}") private String digestKey;
    public IdentityWhitelistController(JdbcTemplate db,AuthSupport auth) { this.db=db; this.auth=auth; }
    private String admin(String header) { String id=auth.admin(header); auth.require(id,"identity:whitelist"); return id; }
    @GetMapping public List<Map<String,Object>> list(@RequestHeader(value="Authorization",required=false) String header) {
        admin(header);
        return db.queryForList("SELECT id,masked_number AS \"maskedId\",created_at AS \"createdAt\" FROM identity_test_whitelist ORDER BY created_at DESC");
    }
    @PostMapping @Transactional public Map<String,Object> add(@RequestHeader(value="Authorization",required=false) String header,@RequestBody Map<String,Object> body) {
        String actor=admin(header);
        Object raw=body.get("idNumber");
        String number=raw instanceof String?((String)raw).trim().toUpperCase(Locale.ROOT):"";
        if (!number.matches("[1-9]\\d{16}[0-9X]")) throw new ApiException(HttpStatus.BAD_REQUEST,"身份证号码格式不正确");
        int[] weights={7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2}; int sum=0;
        for(int i=0;i<17;i++) sum+=(number.charAt(i)-'0')*weights[i];
        if(number.charAt(17)!="10X98765432".charAt(sum%11)) throw new ApiException(HttpStatus.BAD_REQUEST,"身份证号码校验失败");
        try { LocalDate.parse(number.substring(6,14),DateTimeFormatter.BASIC_ISO_DATE); }
        catch(Exception ex) { throw new ApiException(HttpStatus.BAD_REQUEST,"出生日期无效"); }
        if(digestKey.isBlank()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"实名认证摘要密钥尚未配置");
        String digest=auth.hash(digestKey+":"+number);
        if(db.queryForObject("SELECT COUNT(*) FROM identity_test_whitelist WHERE document_digest=?",Integer.class,digest)>0)
            throw new ApiException(HttpStatus.CONFLICT,"该证件已在白名单中");
        String id="wl-"+UUID.randomUUID(), masked=number.substring(0,3)+"************"+number.substring(15);
        db.update("INSERT INTO identity_test_whitelist(id,document_digest,masked_number) VALUES (?,?,?)",id,digest,masked);
        audit(actor,"identity.whitelist.add",id,masked);
        return Map.of("id",id,"maskedId",masked);
    }
    @DeleteMapping("/{id}") @Transactional public Map<String,Object> remove(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id) {
        String actor=admin(header);
        if(db.update("DELETE FROM identity_test_whitelist WHERE id=?",id)!=1) throw new ApiException(HttpStatus.NOT_FOUND,"白名单记录不存在");
        audit(actor,"identity.whitelist.remove",id,"");
        return Map.of("ok",true);
    }
    private void audit(String actor,String action,String id,String detail) {
        db.update("INSERT INTO admin_audit(id,actor_id,action,target_id,detail) VALUES (?,?,?,?,?)","aud-"+UUID.randomUUID(),actor,action,id,detail);
    }
}
