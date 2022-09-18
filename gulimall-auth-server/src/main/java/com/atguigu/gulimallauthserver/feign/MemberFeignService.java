package com.atguigu.gulimallauthserver.feign;

import com.atguigu.common.utils.R;
import com.atguigu.gulimallauthserver.vo.RegisterVo;
import com.atguigu.gulimallauthserver.vo.SocialUser;
import com.atguigu.gulimallauthserver.vo.UserLoginVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("gulimall-member")
public interface MemberFeignService {
    @PostMapping("/member/member/regist")
    R regist(@RequestBody RegisterVo registerVo);
    @PostMapping("/member/member/login")
    R login(@RequestBody UserLoginVo userLoginVo);
    @PostMapping("/member/member/oauth/login")
    R oauthLogin( SocialUser socialUser) throws Exception;
}
