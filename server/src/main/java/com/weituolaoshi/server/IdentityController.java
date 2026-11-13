package com.weituolaoshi.server;

import java.net.URI;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/v1/identity")
public class IdentityController {
    private final JdbcTemplate db;
    private final AuthSupport auth;
    @Value("${identity.provider-url:}") private String providerUrl;
    @Value("${identity.provider-key:}") private String providerKey;
    @Value("${identity.digest-key:}") private String digestKey;
    public IdentityController(JdbcTemplate db, AuthSupport auth) { this.db=db; this.auth=auth; }
    private Map<String,Object> status(String user) {
        List<Map<String,Object>> rows=db.queryForList("SELECT status,mode,adult,legal_gender AS \"gender\",masked_document_number AS \"maskedId\",review_reason AS \"reason\" FROM identity_verification WHERE user_id=?",user);
        return rows.isEmpty()?Map.of("status","unverified"):rows.get(0);
    }
    @GetMapping("/me") public Map<String,Object> me(@RequestHeader(value="Authorization",required=false) String header) { return status(auth.user(header)); }
    @PostMapping("/verifications")
    @Transactional
    public Map<String,Object> submit(@RequestHeader(value="Authorization",required=false) String header,@RequestBody Map<String,Object> body) {
        String user=auth.user(header);
        String name=body.get("name") instanceof String?((String)body.get("name")).trim():"";
        String number=body.get("idNumber") instanceof String?((String)body.get("idNumber")).trim().toUpperCase(Locale.ROOT):"";
        String mode="coser".equals(body.get("mode"))?"coser":"customer";
        if(name.length()<2||name.length()>80||!number.matches("[1-9]\\d{5}\\d{8}\\d{3}[0-9X]")) throw new ApiException(HttpStatus.BAD_REQUEST,"姓名或身份证号码格式不正确");
        if(!Boolean.TRUE.equals(body.get("agreed"))) throw new ApiException(HttpStatus.BAD_REQUEST,"请同意身份核验授权");
        int[] weights={7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2}; String checks="10X98765432"; int sum=0;
        for(int i=0;i<17;i++) sum+=(number.charAt(i)-'0')*weights[i];
        if(checks.charAt(sum%11)!=number.charAt(17)) throw new ApiException(HttpStatus.BAD_REQUEST,"身份证号码校验失败");
        LocalDate birth;
        try { birth=LocalDate.parse(number.substring(6,14),java.time.format.DateTimeFormatter.BASIC_ISO_DATE); }
        catch(Exception ex) { throw new ApiException(HttpStatus.BAD_REQUEST,"出生日期无效"); }
        if(birth.plusYears(18).isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")))) throw new ApiException(HttpStatus.FORBIDDEN,"未满18周岁不能认证");
        if(providerUrl.isBlank()||providerKey.isBlank()||digestKey.isBlank()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"实名核验服务尚未配置");
        Map<?,?> result;
        try {
            HttpHeaders headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON); headers.setBearerAuth(providerKey);
            result=new RestTemplate().postForObject(URI.create(providerUrl),new HttpEntity<>(Map.of("name",name,"idNumber",number),headers),Map.class);
        } catch(Exception ex) { throw new ApiException(HttpStatus.BAD_GATEWAY,"实名核验服务暂不可用"); }
        if(result==null||!Boolean.TRUE.equals(result.get("verified"))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"姓名与证件信息未通过核验");
        String digest=auth.hash(digestKey+":"+number);
        Integer existing=db.queryForObject("SELECT COUNT(*) FROM identity_verification WHERE document_digest=? AND user_id<>?",Integer.class,digest,user);
        if(existing!=null&&existing>0) throw new ApiException(HttpStatus.CONFLICT,"该证件已绑定其他账号");
        String state="coser".equals(mode)?"pending":"verified";
        String gender=(number.charAt(16)-'0')%2==0?"F":"M";
        db.update("DELETE FROM identity_verification WHERE user_id=?",user);
        db.update("INSERT INTO identity_verification(id,user_id,provider_reference,document_digest,masked_document_number,legal_gender,adult,status,mode,verified_at) VALUES (?,?,?,?,?,?,TRUE,?,?,?)",
            "iv-"+UUID.randomUUID(),user,String.valueOf(result.get("reference")),digest,number.substring(0,3)+"************"+number.substring(15),gender,state,mode,
            "verified".equals(state)?java.sql.Timestamp.valueOf(LocalDateTime.now()):null);
        return status(user);
    }
}
