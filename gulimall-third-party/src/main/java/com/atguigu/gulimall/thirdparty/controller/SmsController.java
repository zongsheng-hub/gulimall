package com.atguigu.gulimall.thirdparty.controller;

import com.atguigu.common.utils.R;


import com.atguigu.gulimall.thirdparty.component.SmsComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping(value = "/sms")
public class SmsController {
    @Autowired
    private SmsComponent smsComponent;
    @GetMapping("/sendCode")
    public R sendCode(@RequestParam("phone") String phone,@RequestParam("code") String code){
        smsComponent.sendCode(phone,code);
        return R.ok();

    }
}
