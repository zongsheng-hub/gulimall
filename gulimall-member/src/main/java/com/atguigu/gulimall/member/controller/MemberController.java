package com.atguigu.gulimall.member.controller;

import java.util.Arrays;
import java.util.Map;

//import org.apache.shiro.authz.annotation.RequiresPermissions;
import com.atguigu.common.exception.BizCodeEnume;
import com.atguigu.gulimall.member.exception.PhoneException;
import com.atguigu.gulimall.member.exception.UsernameException;
import com.atguigu.gulimall.member.feign.CouponFeignService;
import com.atguigu.gulimall.member.vo.MemberLoginVo;
import com.atguigu.gulimall.member.vo.MemberRegisterVo;
import com.atguigu.gulimall.member.vo.SocialUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.atguigu.gulimall.member.entity.MemberEntity;
import com.atguigu.gulimall.member.service.MemberService;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.R;



/**
 * 会员
 *
 * @author fuzs
 * @email fuzs@gmail.com
 * @date 2022-04-27 13:07:01
 */
@RestController
@RequestMapping("member/member")
public class MemberController {
    @Autowired
    private MemberService memberService;
    @Autowired
    private CouponFeignService couponFeignService;

    /**
     * 列表
     */
    @RequestMapping("/list")
   // @RequiresPermissions("memeber:member:list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = memberService.queryPage(params);

        return R.ok().put("page", page);
    }


    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
   // @RequiresPermissions("memeber:member:info")
    public R info(@PathVariable("id") Long id){
		MemberEntity member = memberService.getById(id);

        return R.ok().put("member", member);
    }

    /**
     * 保存
     */
    @RequestMapping("/save")
   // @RequiresPermissions("memeber:member:save")
    public R save(@RequestBody MemberEntity member){
		memberService.save(member);

        return R.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
  //  @RequiresPermissions("memeber:member:update")
    public R update(@RequestBody MemberEntity member){
		memberService.updateById(member);

        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
   // @RequiresPermissions("memeber:member:delete")
    public R delete(@RequestBody Long[] ids){
		memberService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

    @RequestMapping("/coupons")
    public R test(){
        MemberEntity memberEntity = new MemberEntity();
        memberEntity.setNickname("张三");
        R membercoupons = couponFeignService.membercoupons(); //假设张三去数据库查了后返回了张三的优惠券信息

        // 打印会员和优惠券信息
        return R.ok().put("member",memberEntity).put("coupons",membercoupons.get("coupons"));
    }
    @PostMapping("/regist")
    public R regist(@RequestBody MemberRegisterVo memberRegisterVo){
        try {
            memberService.regist(memberRegisterVo);
        } catch (UsernameException e) {
            return R.error(BizCodeEnume.USER_EXIT_EXCEPTION.getCode(), BizCodeEnume.USER_EXIT_EXCEPTION.getMsg());
        } catch (PhoneException e){
            return R.error(BizCodeEnume.PHONE_EXIT_EXCEPTION.getCode(), BizCodeEnume.PHONE_EXIT_EXCEPTION.getMsg());

        }
        return R.ok();
    }
    @PostMapping("/login")
    public R login(@RequestBody MemberLoginVo memberLoginVo){
        MemberEntity memberEntity = memberService.login(memberLoginVo);
        if(memberEntity == null){
            return R.error(BizCodeEnume.USERNAME_PHONE_INVALID_EXCEPTION.getCode(), BizCodeEnume.USERNAME_PHONE_INVALID_EXCEPTION.getMsg());
        }else{
            return R.ok().setData(memberEntity);
        }
    }

    @PostMapping("/oauth/login")
    public R oauthLogin(SocialUser socialUser) throws Exception {
        MemberEntity memberEntity = memberService.login(socialUser);
        if(memberEntity == null){
            return R.error(BizCodeEnume.USERNAME_PHONE_INVALID_EXCEPTION.getCode(), BizCodeEnume.USERNAME_PHONE_INVALID_EXCEPTION.getMsg());
        }else{
            return R.ok().setData(memberEntity);
        }
    }

}
