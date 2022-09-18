package com.atguigu.gulimall.order.config;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.atguigu.gulimall.order.vo.PayVo;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "alipay")
@Component
@Data
public class AlipayTemplate {

    //在支付宝创建的应用的id
    private  String app_id = "2021000121625866";

    // 商户私钥，您的PKCS8格式RSA2私钥
    private  String merchant_private_key = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCMjRCAjQqjqtuL8Yyrttdaq6dflvBLjbsnWT3cx3ZYox5mc2uerOognRtU7MJezKEMvspmTNFEmhct+d3Ib0t7GnSlu+iyVtFl4Sk5Xvy+rgBH/osjgM4qeVm3Ngj61himziWykD03h6VJMeTXrmPyhj5AZQFr4xAiXMmh86feQ/ragRIz6O5N6hl9ippRyVrJsP9P5++CYe/NHkf/b2Zu5+vu4Ey7AEcW6rAKfzmnQoGHWrs+SGf8MFRlPwBsarxagTDZKPMLnZ5JQs/Iiglf8gOjF9bGDKH0xtKvhTC3qR14gkT2nFCaJsHOp59slnLwgQs6ZdM4MfDgFj5PT8BvAgMBAAECggEAbjp7xLXYsD9ha8GG92ywPLX+0CMKVdbT/qoosCvmjuFvCB5hOtKV0/oi41nDnqGRdPN0vQJl5Q2MPEyIOZe/dM5syUYz2xnno6UQtaPfSJQiOFRTPwbXqcv+JofERyT9tGRcgExLFm/Vmy/8L4Lfc+cAj3QnxzsO/kQTBmaXMuKmTh4HwxCgIZ//r8rqkwyyQkRAD44z5FXmO45ZY0iVNT/s9MOvz9x+6WiZD77eptuy8e/9y+EDFugsn1SSrXT5WjJ6450VvC5oC/BREI/up++W9YW7ccPF4ELCkzsf97Hkmb3DFI1xmCwR7JZ5OkNQl+ifgIqsVXgnC2JhoW8h0QKBgQDT9UC2aPSbQhr5t8QNKzp6ARIOO51QEBCHcook6i2GOCeg4EZbWZUqwmyT8ylo+euWHPQ+GyXdF5LncRbEd+je8pRl2OQ5ghi7JvIrPg09VSJyXG8LNlsJpR+L9oTbFEm1CxS+aU1Xv/ozvtVYAprHfAXFXdo2aElPWuS0HGO0jQKBgQCpwXAvKPBiN1mcq6xrq5ZaROC3OVZAKY92/W0NsAdo048fUBChiqMwnz74t1tSZZXTye+rbcrMCp8kLXrBShFyELFycWvG6L16LZ6WF6qiOX/AbZhnWfIMKbeKCEvRDon4yn8A/zSVWVV/AblTjamSrLpaFmQKjAx4Qxyn0LfP6wKBgQCr3FlLTlC/rtsWjeV+VAXHNSUBu16zs0LjwIC4q9Sb1t5y+Eaz/TARCrJlSC3Ue4iFIBb+YJ7T5TBtTuoKBbwhQgoHhtSOuKj63vpPC7JKf8Q9fCthqQIqEORCZXGV/Z5CGKkKN3HJ69SgrmRoRzxe8XXwcUflA6wlluqIC9jgZQKBgAoynaunmPE9g/uecIgzUZ/wnVNEdYL7PCwYw1FcLsXEV8e4xpb9vhnwpxbH5QuJILwNWIGC4fjp9m907/fmnC+vAwnt+FyFpMRPM0Su/UgrjhogO1SSGbFTXDxFjVBswCsa9xA3RJBoQnOTxe9YBkTSbYt15uKfUSx0U6ddKX2rAoGAURfWH3fQIHi/RNUqBIO8CFl4Nw/tZh9KGousRxfx7ss0wqYyQVaguJSvkk/0Rzw1dIQ32el4fx5QdeMQLSH9TPqSA1RKFp1zjrbRD43z1Xsw2KGjtQSp5hJ7mp4vuaYgMwVUk6Vekn2L0N934HSm6IuyBHUY9zZkLI2AZJzrSyk=";
    // 支付宝公钥,查看地址：https://openhome.alipay.com/platform/keyManage.htm 对应APPID下的支付宝公钥。
    private  String alipay_public_key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkvDl5kJwCEaUeIKOidvk5bm+n/tvpKEEQXzCiGLuUFLbxKyOnPbhdudzyc/z8m+P5lZRxrUqdZ/lAo8AdU+J5KRg9Vui31Gd/ngL38I2263gGnC3DgyNK6WM56oaVPcHlbXs24NpwrLSPPOM+KFbeMfVc4Lzdi70VfVfuH1xFxmi3HaJkpDnvVFxeat2oZmP6gNSFh8OYloIjCIFyGJ6r7Qae1u6TVlP9kkY3ytBlM0g4xG0X3ZOW0D29iKeMc8rQLXa8Kc9T1cQTm/WIH9RHeHKDnCSwJZAzo5MMrlFqj6mClqay/h7Bilrb6WB8t62hMnChnineCHKKgEnB8KvswIDAQAB";
    // 服务器[异步通知]页面路径  需http://格式的完整路径，不能加?id=123这类自定义参数，必须外网可以正常访问
    // 支付宝会悄悄的给我们发送一个请求，告诉我们支付成功的信息
    private  String notify_url;

    // 页面跳转同步通知页面路径 需http://格式的完整路径，不能加?id=123这类自定义参数，必须外网可以正常访问
    //同步通知，支付成功，一般跳转到成功页
    private  String return_url="http://member.gulimall.com/memberOrder.html";

    // 签名方式
    private  String sign_type = "RSA2";

    // 字符编码格式
    private  String charset = "utf-8";

    // 支付宝网关； https://openapi.alipaydev.com/gateway.do
    private  String gatewayUrl = "https://openapi.alipaydev.com/gateway.do";

    public  String pay(PayVo vo) throws AlipayApiException {

        //AlipayClient alipayClient = new DefaultAlipayClient(AlipayTemplate.gatewayUrl, AlipayTemplate.app_id, AlipayTemplate.merchant_private_key, "json", AlipayTemplate.charset, AlipayTemplate.alipay_public_key, AlipayTemplate.sign_type);
        //1、根据支付宝的配置生成一个支付客户端
        AlipayClient alipayClient = new DefaultAlipayClient(gatewayUrl,
                app_id, merchant_private_key, "json",
                charset, alipay_public_key, sign_type);

        //2、创建一个支付请求 //设置请求参数
        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();
        alipayRequest.setReturnUrl(return_url);
        alipayRequest.setNotifyUrl(notify_url);

        //商户订单号，商户网站订单系统中唯一订单号，必填
        String out_trade_no = vo.getOut_trade_no();
        //付款金额，必填
        BigDecimal total_amount = vo.getTotal_amount();
        //订单名称，必填
        String subject = vo.getSubject();
        //商品描述，可空
        String body = vo.getBody();

        alipayRequest.setBizContent("{\"out_trade_no\":\""+ out_trade_no +"\","
                + "\"total_amount\":\""+ total_amount +"\","
                + "\"subject\":\""+ subject +"\","
                + "\"body\":\""+ body +"\","
                + "\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}");

        String result = alipayClient.pageExecute(alipayRequest).getBody();

        //会收到支付宝的响应，响应的是一个页面，只要浏览器显示这个页面，就会自动来到支付宝的收银台页面
        System.out.println("支付宝的响应："+result);

        return result;

    }
}
