package com.atguigu.gulimallauthserver.vo;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;


@Data
public class RegisterVo {
    @NotNull(message = "用户姓名不能为空")
    @Length(min = 3,max = 6,message = "用户姓名长度在3-6之间")
    private String username;
    @NotNull(message = "密码不能为空")
    @Length(min = 6,max = 12,message = "密码长度在6-12位之间")
    private String password;
    @NotNull
    private String phone;
    @NotNull
    private String code;
}
