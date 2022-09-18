package com.atguigu.gulimallauthserver.controller;

import com.atguigu.common.constant.AuthServerConstant;
import com.atguigu.common.exception.BizCodeEnume;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberRespVo;
import com.atguigu.gulimallauthserver.feign.MemberFeignService;
import com.atguigu.gulimallauthserver.feign.ThirdPartyFeignService;
import com.atguigu.gulimallauthserver.vo.RegisterVo;
import com.atguigu.gulimallauthserver.vo.UserLoginVo;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.alibaba.fastjson.TypeReference;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Controller
public class LoginController {
    @Autowired
    private ThirdPartyFeignService thirdPartyFeignService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private MemberFeignService memberFeignService;
    @ResponseBody
    @GetMapping("/sms/sendCode")
    public R sendSmsCode(@RequestParam("phone") String phone){
        //验证码防重刷
        //String[] s = stringRedisTemplate.opsForValue().get(AuthServerConstant.SMS_CODE_CACHE_PREFIX + phone).split("_");
        String s = stringRedisTemplate.opsForValue().get(AuthServerConstant.SMS_CODE_CACHE_PREFIX + phone);
        if(!StringUtils.isEmpty(s)){
            String[] s1 = s.split("_");
            long l = Long.parseLong(s1[1]);
            if(System.currentTimeMillis()-l<=60000){
                //60秒内不能重复发
                return R.error(BizCodeEnume.SMS_EXCEPTION.getCode(), BizCodeEnume.SMS_EXCEPTION.getMsg());
            }
        }


        String code = UUID.randomUUID().toString().substring(0, 5);
        String redisCode = code+"_"+System.currentTimeMillis();
        //将验证码保存到redis中
        stringRedisTemplate.opsForValue().setIfAbsent(AuthServerConstant.SMS_CODE_CACHE_PREFIX+phone,redisCode,10, TimeUnit.MINUTES);

        thirdPartyFeignService.sendCode(phone, code);
        return R.ok();


    }
    @PostMapping("/register")
    public String register(@Validated RegisterVo registerVo, BindingResult result, RedirectAttributes redirectAttributes){
        if(result.hasErrors()){
            //有错误跳转到注册页面
            Map<String, String> errors = result.getFieldErrors().stream().collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage));
            redirectAttributes.addFlashAttribute("errors",errors);
            return "redirect:http://auth.gulimall.com/reg.html";
        }
        //真正的注册，调用远程服务
        //1、校验验证码
        String code = registerVo.getCode();
        String redis = stringRedisTemplate.opsForValue().get(AuthServerConstant.SMS_CODE_CACHE_PREFIX + registerVo.getPhone());
        if(!StringUtils.isEmpty(redis)){
            if(code.equals(redis.split("_")[0])){
               //执行远程服务的保存会员信息方法
                R regist = memberFeignService.regist(registerVo);
                if(regist.getCode()==0){
                    //成功
                    //删除旧的验证码
                    stringRedisTemplate.delete(AuthServerConstant.SMS_CODE_CACHE_PREFIX + registerVo.getPhone());
                    return "redirect:http://auth.gulimall.com/login.html";
                }else{
                    HashMap<String, String> errors = new HashMap<>();
                    errors.put("msg", regist.getData(new TypeReference<String>(){},"msg"));
                    redirectAttributes.addFlashAttribute("errors",errors);
                    return "redirect:http://auth.gulimall.com/reg.html";

                }
            }else{
                Map<String, String> errors = new HashMap<>();
                errors.put("code","验证码错误");
                redirectAttributes.addFlashAttribute("errors",errors);
                return "redirect:http://auth.gulimall.com/reg.html";
            }


        }else{
            Map<String, String> errors = new HashMap<>();
            errors.put("code","验证码错误");
            redirectAttributes.addFlashAttribute("errors",errors);
            return "redirect:http://auth.gulimall.com/reg.html";

        }

    }
    @PostMapping("/login")
    public String login(UserLoginVo userLoginVo, RedirectAttributes redirectAttributes, HttpSession session){

        //调用远程的保存方法
        R login = memberFeignService.login(userLoginVo);
        if(login.getCode()==0){
            MemberRespVo data = login.getData(new TypeReference<MemberRespVo>() {
            }, "data");
            session.setAttribute(AuthServerConstant.LOGIN_USER,data);
            return "redirect:http://gulimall.com";
        }else{
            Map<String, String> errors = new HashMap<>();
            errors.put("msg", login.getData(new TypeReference<String>(){},"msg"));
            redirectAttributes.addFlashAttribute("errors",errors);
            return "redirect:http://auth.gulimall.com/login.html";
        }


    }
    @GetMapping("/login.html")
    public String loginPage(HttpSession session){
        Object attribute = session.getAttribute(AuthServerConstant.LOGIN_USER);
        if(attribute == null){
            return "login";
        }else{
            return "redirect:http://gulimall.com";
        }

    }

}
