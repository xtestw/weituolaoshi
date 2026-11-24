package com.weituolaoshi.server;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.faceid.v20180301.FaceidClient;
import com.tencentcloudapi.faceid.v20180301.models.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/identity")
public class IdentityController {
    private static final ZoneId SHANGHAI=ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate db;
    private final AuthSupport auth;
    @Value("${faceid.secret-id:}") private String secretId;
    @Value("${faceid.secret-key:}") private String secretKey;
    @Value("${faceid.rule-id:}") private String ruleId;
    @Value("${faceid.region:ap-guangzhou}") private String region;
    @Value("${identity.digest-key:}") private String digestKey;
    @Value("${identity.face-check-whitelist:}") private String faceWhitelist;
    private final IdentityGenderPolicy genderPolicy;
    public IdentityController(JdbcTemplate db,AuthSupport auth,IdentityGenderPolicy genderPolicy) {
        this.db=db; this.auth=auth; this.genderPolicy=genderPolicy;
    }

    private boolean faceAllowed(String number) {
        return (!digestKey.isBlank() && db.queryForObject("SELECT COUNT(*) FROM identity_test_whitelist WHERE document_digest=?",Integer.class,auth.hash(digestKey+":"+number))>0) || Arrays.stream(faceWhitelist.split(","))
            .map(s -> s.trim().toUpperCase(Locale.ROOT)).anyMatch(number::equals);
    }

    @PostMapping("/verifications") @Transactional
    public Map<String,Object> verify(@RequestHeader(value="Authorization",required=false) String header,
            @RequestBody Map<String,Object> body) {
        String user=auth.user(header), number=number(body);
        validate(name(body),number,body);
        if (!faceAllowed(number)) throw new ApiException(HttpStatus.FORBIDDEN,"该账号证件不在测试白名单，请使用人脸核身");
        return verifyTest(user,number,mode(body));
    }

    private Map<String,Object> verifyTest(String user,String number,String mode) {
        Map<String,Object> result=new HashMap<>(saveIdentity(user,number,mode,"test-face-whitelist:"+UUID.randomUUID()));
        result.put("faceBypassed",true);
        return result;
    }

    private Map<String,Object> status(String user) {
        List<Map<String,Object>> rows=db.queryForList("SELECT status,mode,adult,legal_gender AS \"gender\",masked_document_number AS \"maskedId\",review_reason AS \"reason\" FROM identity_verification WHERE user_id=?",user);
        Map<String,Object> result=rows.isEmpty()?new HashMap<>(Map.of("status","unverified")):new HashMap<>(rows.get(0));
        List<String> coserStatuses=db.queryForList("SELECT verification_status FROM coser_profile WHERE user_id=?",String.class,user);
        result.put("coserStatus",coserStatuses.isEmpty()?"unverified":coserStatuses.get(0));
        return result;
    }
    @GetMapping("/me") public Map<String,Object> me(@RequestHeader(value="Authorization",required=false) String header) { return status(auth.user(header)); }
    private FaceidClient client() {
        if(secretId.isBlank()||secretKey.isBlank()||ruleId.isBlank()||digestKey.isBlank())
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"人脸核身服务尚未配置");
        return new FaceidClient(new Credential(secretId,secretKey),region);
    }
    private String mode(Map<String,Object> body) { return "coser".equals(body.get("mode"))?"coser":"customer"; }
    private String name(Map<String,Object> body) { return body.get("name") instanceof String?((String)body.get("name")).trim():""; }
    private String number(Map<String,Object> body) { return body.get("idNumber") instanceof String?((String)body.get("idNumber")).trim().toUpperCase(Locale.ROOT):""; }
    private void validate(String name,String number,Map<String,Object> body) {
        if(name==null||name.length()<2||name.length()>32||!number.matches("[1-9]\\d{5}\\d{8}\\d{3}[0-9X]"))
            throw new ApiException(HttpStatus.BAD_REQUEST,"姓名或身份证号码格式不正确");
        if(!Boolean.TRUE.equals(body.get("agreed"))) throw new ApiException(HttpStatus.BAD_REQUEST,"请同意人脸核身授权");
        int[] weights={7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2}; String checks="10X98765432"; int sum=0;
        for(int i=0;i<17;i++) sum+=(number.charAt(i)-'0')*weights[i];
        if(checks.charAt(sum%11)!=number.charAt(17)) throw new ApiException(HttpStatus.BAD_REQUEST,"身份证号码校验失败");
        try { LocalDate.parse(number.substring(6,14),DateTimeFormatter.BASIC_ISO_DATE); }
        catch(Exception ex) { throw new ApiException(HttpStatus.BAD_REQUEST,"出生日期无效"); }
    }
    @PostMapping("/face/start") @Transactional public Map<String,Object> start(@RequestHeader(value="Authorization",required=false) String header,@RequestBody Map<String,Object> body) {
        String user=auth.user(header), name=name(body), number=number(body), mode=mode(body);
        validate(name,number,body);
        if (faceAllowed(number)) return verifyTest(user,number,mode);
        try {
            DetectAuthRequest request=new DetectAuthRequest(); request.setRuleId(ruleId); request.setName(name); request.setIdCard(number);
            DetectAuthResponse response=client().DetectAuth(request);
            String bizToken=response.getBizToken();
            if(bizToken==null||bizToken.isBlank()) throw new ApiException(HttpStatus.BAD_GATEWAY,"未获得人脸核身凭证");
            db.update("INSERT INTO face_verification_attempt(token_hash,user_id,mode,expires_at) VALUES (?,?,?,?)",
                auth.hash(bizToken),user,mode,Timestamp.from(Instant.now().plusSeconds(7200)));
            return Map.of("bizToken",bizToken);
        } catch(TencentCloudSDKException ex) { throw new ApiException(HttpStatus.BAD_GATEWAY,"人脸核身服务暂不可用"); }
    }
    @PostMapping("/face/complete") @Transactional public Map<String,Object> complete(@RequestHeader(value="Authorization",required=false) String header,@RequestBody Map<String,Object> body) {
        String user=auth.user(header), bizToken=body.get("bizToken") instanceof String?(String)body.get("bizToken"):"";
        if(bizToken.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST,"缺少核身凭证");
        List<Map<String,Object>> attempts=db.queryForList("SELECT mode FROM face_verification_attempt WHERE token_hash=? AND user_id=? AND consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP",auth.hash(bizToken),user);
        if(attempts.isEmpty()) throw new ApiException(HttpStatus.CONFLICT,"核身流程不存在、已使用或已过期");
        String mode=(String)attempts.get(0).get("mode");
        GetDetectInfoEnhancedResponse response;
        try {
            GetDetectInfoEnhancedRequest request=new GetDetectInfoEnhancedRequest(); request.setRuleId(ruleId); request.setBizToken(bizToken); request.setInfoType("1");
            response=client().GetDetectInfoEnhanced(request);
        } catch(TencentCloudSDKException ex) { throw new ApiException(HttpStatus.BAD_GATEWAY,"暂时无法获取人脸核身结果"); }
        DetectInfoText result=response.getText();
        if(result==null||result.getErrCode()==null||result.getErrCode()!=0L) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"人脸核身未通过");
        String name=result.getName(), number=result.getIdCard()==null?"":result.getIdCard().toUpperCase(Locale.ROOT);
        validate(name,number,Map.of("agreed",true));
        int consumed=db.update("UPDATE face_verification_attempt SET consumed_at=CURRENT_TIMESTAMP WHERE token_hash=? AND user_id=? AND consumed_at IS NULL",auth.hash(bizToken),user);
        if(consumed!=1) throw new ApiException(HttpStatus.CONFLICT,"核身结果已使用");
        return saveIdentity(user,number,mode,response.getRequestId());
    }

    private Map<String,Object> saveIdentity(String user,String number,String mode,String reference) {
        if (digestKey.isBlank()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"实名认证摘要密钥尚未配置");
        // Serialize repeated verification for a user; document uniqueness is enforced by the database.
        db.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",String.class,user);
        LocalDate birth=LocalDate.parse(number.substring(6,14),DateTimeFormatter.BASIC_ISO_DATE);
        if(birth.plusYears(18).isAfter(LocalDate.now(SHANGHAI))) throw new ApiException(HttpStatus.FORBIDDEN,"未满18周岁不能认证");
        String gender=(number.charAt(16)-'0')%2==0?"F":"M";
        if("coser".equals(mode)&&!genderPolicy.allowsNumber(gender,number)) throw new ApiException(HttpStatus.FORBIDDEN,"仅成年女性可申请Coser认证");
        String digest=auth.hash(digestKey+":"+number);
        Integer existing=db.queryForObject("SELECT COUNT(*) FROM identity_verification WHERE document_digest=? AND user_id<>?",Integer.class,digest,user);
        if(existing!=null&&existing>0) throw new ApiException(HttpStatus.CONFLICT,"该证件已绑定其他账号");
        db.update("DELETE FROM identity_verification WHERE user_id=?",user);
        db.update("INSERT INTO identity_verification(id,user_id,provider_reference,document_digest,masked_document_number,legal_gender,adult,status,mode,verified_at) VALUES (?,?,?,?,?,?,TRUE,'verified',?,CURRENT_TIMESTAMP)",
            "iv-"+UUID.randomUUID(),user,reference,digest,number.substring(0,3)+"************"+number.substring(15),gender,mode);
        if("coser".equals(mode)) {
            db.update("UPDATE app_user SET role='coser' WHERE id=?",user);
            Integer count=db.queryForObject("SELECT COUNT(*) FROM coser_profile WHERE user_id=?",Integer.class,user);
            if(count==null||count==0) db.update("INSERT INTO coser_profile(user_id,display_name,city,verification_status,service_status) SELECT id,COALESCE(nickname,'微信用户'),'','verified','active' FROM app_user WHERE id=?",user);
            else db.update("UPDATE coser_profile SET verification_status='verified',service_status='active' WHERE user_id=?",user);
        }
        return status(user);
    }
}
