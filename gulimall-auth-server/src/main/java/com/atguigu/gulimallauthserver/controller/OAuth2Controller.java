package com.atguigu.gulimallauthserver.controller;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.R;
import com.atguigu.gulimallauthserver.feign.MemberFeignService;
import com.atguigu.gulimallauthserver.utils.HttpUtils;
import com.atguigu.common.vo.MemberRespVo;
import com.atguigu.gulimallauthserver.vo.SocialUser;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
@Controller
public class OAuth2Controller {
    @Autowired
    private MemberFeignService memberFeignService;
    @GetMapping("/oauth2.0/weibo/success")
    public String weibo(@RequestParam("code") String code,HttpSession session) throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("client_id","522451216");
        map.put("client_secret","281a57610bde2f8f0a2bc1c344721ef1");
        map.put("grant_type","authorization_code");
        map.put("redirect_uri","http://auth.gulimall.com/oauth2.0/weibo/success");
        map.put("code",code);

        //1、根据用户授权返回的code换取access_token
        HttpResponse httpResponse = HttpUtils.doPost("https://api.weibo.com", "/oauth2/access_token", "post", new HashMap<>(), map,new HashMap<>());

        if(httpResponse.getStatusLine().getStatusCode() == 200){
            //调用成功
            String s = EntityUtils.toString(httpResponse.getEntity());
            //知道了哪个社交用户
            //1）、当前用户如果是第一次进网站，自动注册进来（为当前社交用户生成一个会员信息，以后这个社交账号就对应指定的会员）
            //登录或者注册这个社交用户
            SocialUser socialUser = JSONObject.parseObject(s, SocialUser.class);
            //调用远程服务注册或登录
            R r = memberFeignService.oauthLogin(socialUser);
            if(r.getCode() == 0){
                //登录成功
                MemberRespVo data = r.getData(new TypeReference<MemberRespVo>() {
                }, "data");
                session.setAttribute("login_user",data);


            }else {

            }

            return "redirect:http://gulimall.com";
        }else{
            return "redirect:http://auth.gulimall.com/login.html";
        }

    }
}
