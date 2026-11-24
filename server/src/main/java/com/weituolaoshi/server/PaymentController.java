package com.weituolaoshi.server;

import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class PaymentController {
    private final JdbcTemplate db;
    private final AuthSupport auth;
    @Value("${wechat.app-id:}") private String appId;
    @Value("${payment.merchant-id:}") private String merchantId;
    @Value("${payment.merchant-serial-number:}") private String serial;
    @Value("${payment.private-key-path:}") private String privateKeyPath;
    @Value("${payment.api-v3-key:}") private String apiV3Key;
    @Value("${payment.notify-url:}") private String notifyUrl;
    @Value("${payment.refund-notify-url:}") private String refundNotifyUrl;
    private volatile RSAAutoCertificateConfig config;
    PaymentController(JdbcTemplate db, AuthSupport auth) { this.db=db; this.auth=auth; }

    private boolean configured() {
        return !appId.isBlank() && !merchantId.isBlank() && !serial.isBlank() &&
            !privateKeyPath.isBlank() && !apiV3Key.isBlank() && !notifyUrl.isBlank() &&
            !refundNotifyUrl.isBlank();
    }
    private RSAAutoCertificateConfig config() {
        if (!configured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"微信支付商户配置尚未完成");
        if (config==null) synchronized(this) {
            if (config==null) config=new RSAAutoCertificateConfig.Builder().merchantId(merchantId)
                .privateKeyFromPath(privateKeyPath).merchantSerialNumber(serial).apiV3Key(apiV3Key).build();
        }
        return config;
    }
    private RequestParam notification(@RequestHeader("Wechatpay-Serial") String serialNumber,
            @RequestHeader("Wechatpay-Timestamp") String timestamp,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,@RequestBody String body) {
        return new RequestParam.Builder().serialNumber(serialNumber).timestamp(timestamp).nonce(nonce)
            .signature(signature).body(body).build();
    }

    @PostMapping("/orders/{id}/payment")
    public Map<String,String> payment(@PathVariable String id,@RequestHeader("Authorization") String bearer) {
        String user=auth.user(bearer);
        List<Map<String,Object>> rows=db.queryForList("SELECT o.*,u.wechat_openid FROM booking_order o " +
            "JOIN app_user u ON u.id=o.customer_id WHERE o.id=? AND o.customer_id=?",id,user);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"订单不存在");
        Map<String,Object> order=rows.get(0);
        if (!"awaiting_payment".equals(order.get("status")) ||
            !((Timestamp)order.get("payment_expires_at")).after(new Timestamp(System.currentTimeMillis())))
            throw new ApiException(HttpStatus.CONFLICT,"订单已过付款期限");
        if (order.get("wechat_openid")==null) throw new ApiException(HttpStatus.BAD_REQUEST,"请使用微信账号支付");
        com.wechat.pay.java.service.payments.jsapi.model.Amount amount=new com.wechat.pay.java.service.payments.jsapi.model.Amount();
        amount.setTotal(Math.toIntExact(((Number)order.get("payable_amount")).longValue()));
        amount.setCurrency("CNY");
        Payer payer=new Payer(); payer.setOpenid((String)order.get("wechat_openid"));
        PrepayRequest request=new PrepayRequest();
        request.setAppid(appId); request.setMchid(merchantId); request.setOutTradeNo(id);
        request.setDescription(String.valueOf(order.get("service_title_snapshot")));
        request.setNotifyUrl(notifyUrl);
        request.setTimeExpire(((Timestamp)order.get("payment_expires_at")).toLocalDateTime()
            .atZone(ZoneId.systemDefault()).toOffsetDateTime().toString());
        request.setAmount(amount); request.setPayer(payer);
        try {
            PrepayWithRequestPaymentResponse response=new JsapiServiceExtension.Builder().config(config()).build()
                .prepayWithRequestPayment(request);
            return Map.of("timeStamp",response.getTimeStamp(),"nonceStr",response.getNonceStr(),
                "package",response.getPackageVal(),"signType",response.getSignType(),"paySign",response.getPaySign());
        } catch (ApiException ex) { throw ex; }
        catch (RuntimeException ex) { throw new ApiException(HttpStatus.BAD_GATEWAY,"微信支付下单失败，请稍后重试"); }
    }

    @PostMapping("/payments/wechat/notify")
    @Transactional
    public void paid(@RequestHeader("Wechatpay-Serial") String serialNumber,
            @RequestHeader("Wechatpay-Timestamp") String timestamp,@RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,@RequestBody String body) {
        Transaction transaction=new NotificationParser(config()).parse(
            notification(serialNumber,timestamp,nonce,signature,body),Transaction.class);
        if (transaction.getTradeState()!=Transaction.TradeStateEnum.SUCCESS) return;
        if (!merchantId.equals(transaction.getMchid()) || !appId.equals(transaction.getAppid()))
            throw new ApiException(HttpStatus.BAD_REQUEST,"支付商户信息不匹配");
        String id=transaction.getOutTradeNo();
        List<Map<String,Object>> rows=db.queryForList("SELECT * FROM booking_order WHERE id=? FOR UPDATE",id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"订单不存在");
        Map<String,Object> order=rows.get(0);
        long total=((Number)order.get("payable_amount")).longValue();
        if (transaction.getAmount()==null || transaction.getAmount().getTotal()==null ||
            transaction.getAmount().getTotal()!=total)
            throw new ApiException(HttpStatus.BAD_REQUEST,"支付金额不匹配");
        Integer duplicate=db.queryForObject("SELECT COUNT(*) FROM payment_transaction WHERE provider_transaction_id=?",
            Integer.class,transaction.getTransactionId());
        if (duplicate!=null&&duplicate>0) return;
        db.update("INSERT INTO payment_transaction(id,order_id,provider_transaction_id,type,amount,status) " +
            "VALUES (?,?,?,'payment',?,'succeeded')","pay-"+UUID.randomUUID(),id,transaction.getTransactionId(),total);
        if ("awaiting_payment".equals(order.get("status")) &&
            ((Timestamp)order.get("payment_expires_at")).after(new Timestamp(System.currentTimeMillis()))) {
            Timestamp expiry=Timestamp.valueOf(LocalDateTime.now().plusHours(2));
            db.update("UPDATE booking_order SET status='awaiting_coser',payment_status='paid',paid_amount=?,confirm_expires_at=? WHERE id=?",
                total,expiry,id);
            db.update("UPDATE order_appointment SET status='awaiting_coser',confirm_expires_at=? WHERE order_id=?",expiry,id);
            db.update("INSERT INTO order_event(id,order_id,action,from_status,to_status) VALUES (?,?,'paid','awaiting_payment','awaiting_coser')",
                "evt-"+UUID.randomUUID(),id);
        } else if ("cancelled".equals(order.get("status"))) {
            db.update("UPDATE booking_order SET status='refund_pending',payment_status='paid',paid_amount=?,refund_amount_due=? WHERE id=?",
                total,total,id);
            db.update("INSERT INTO order_refund(id,refund_no,order_id,request_key,reason_code,actor_type," +
                "cancellation_requested_at,service_start_snapshot,cancel_policy_code,calculation_base_amount," +
                "refund_rate_bps,refund_amount,currency) VALUES (?,?,?,?,?,'system',CURRENT_TIMESTAMP,?,?,?,?,?,'CNY')",
                "refund-"+UUID.randomUUID(),"rf-"+UUID.randomUUID(),id,"late-payment-"+id,"late_payment",
                order.get("start_at"),order.get("cancel_policy_code"),total,10000,total);
        }
    }

    @Scheduled(fixedDelay=60000)
    public void processRefunds() {
        if (!configured()) return;
        List<Map<String,Object>> refunds=db.queryForList("SELECT r.*,o.paid_amount FROM order_refund r " +
            "JOIN booking_order o ON o.id=r.order_id WHERE r.status='pending' ORDER BY r.created_at LIMIT 20");
        RefundService service=new RefundService.Builder().config(config()).build();
        for (Map<String,Object> refund: refunds) {
            String refundId=(String)refund.get("id");
            try {
                CreateRequest request=new CreateRequest();
                request.setOutTradeNo((String)refund.get("order_id"));
                request.setOutRefundNo((String)refund.get("refund_no"));
                request.setReason((String)refund.get("reason_code"));
                request.setNotifyUrl(refundNotifyUrl);
                AmountReq amount=new AmountReq();
                amount.setRefund(((Number)refund.get("refund_amount")).longValue());
                amount.setTotal(((Number)refund.get("paid_amount")).longValue());
                amount.setCurrency("CNY");
                request.setAmount(amount);
                com.wechat.pay.java.service.refund.model.Refund result=service.create(request);
                db.update("UPDATE order_refund SET status='processing',payment_provider='wechat',provider_refund_no=?," +
                    "submitted_at=CURRENT_TIMESTAMP WHERE id=? AND status='pending'",result.getRefundId(),refundId);
            } catch (RuntimeException ex) {
                db.update("UPDATE order_refund SET retry_count=retry_count+1,failed_at=CURRENT_TIMESTAMP " +
                    "WHERE id=? AND status='pending'",refundId);
            }
        }
    }

    @PostMapping("/payments/wechat/refund-notify")
    @Transactional
    public void refunded(@RequestHeader("Wechatpay-Serial") String serialNumber,
            @RequestHeader("Wechatpay-Timestamp") String timestamp,@RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,@RequestBody String body) {
        RefundNotification notice=new NotificationParser(config()).parse(
            notification(serialNumber,timestamp,nonce,signature,body),RefundNotification.class);
        if (notice.getRefundStatus()!=com.wechat.pay.java.service.refund.model.Status.SUCCESS) return;
        List<Map<String,Object>> rows=db.queryForList("SELECT * FROM order_refund WHERE refund_no=? FOR UPDATE",notice.getOutRefundNo());
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"退款记录不存在");
        Map<String,Object> refund=rows.get(0);
        if ("succeeded".equals(refund.get("status"))) return;
        long amount=((Number)refund.get("refund_amount")).longValue();
        if (notice.getAmount()==null || notice.getAmount().getRefund()==null ||
            notice.getAmount().getRefund()!=amount)
            throw new ApiException(HttpStatus.BAD_REQUEST,"退款金额不匹配");
        String id=(String)refund.get("order_id");
        db.update("UPDATE order_refund SET status='succeeded',succeeded_at=CURRENT_TIMESTAMP WHERE id=?",refund.get("id"));
        db.update("UPDATE booking_order SET refunded_amount=refunded_amount+?,refund_status='succeeded',status='cancelled' WHERE id=?",
            amount,id);
        db.update("INSERT INTO order_event(id,order_id,action,from_status,to_status) " +
            "VALUES (?,?,'refund','refund_pending','cancelled')","evt-"+UUID.randomUUID(),id);
    }
}
