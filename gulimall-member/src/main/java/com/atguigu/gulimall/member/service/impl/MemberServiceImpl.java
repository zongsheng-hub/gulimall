package com.atguigu.gulimall.member.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gulimall.member.dao.MemberLevelDao;
import com.atguigu.gulimall.member.entity.MemberLevelEntity;
import com.atguigu.gulimall.member.exception.PhoneException;
import com.atguigu.gulimall.member.exception.UsernameException;
import com.atguigu.gulimall.member.utils.HttpUtils;
import com.atguigu.gulimall.member.vo.MemberLoginVo;
import com.atguigu.gulimall.member.vo.MemberRegisterVo;
import com.atguigu.gulimall.member.vo.SocialUser;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.member.dao.MemberDao;
import com.atguigu.gulimall.member.entity.MemberEntity;
import com.atguigu.gulimall.member.service.MemberService;


@Service("memberService")
public class MemberServiceImpl extends ServiceImpl<MemberDao, MemberEntity> implements MemberService {
    @Autowired
    private MemberLevelDao memberLevelDao;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<MemberEntity> page = this.page(
                new Query<MemberEntity>().getPage(params),
                new QueryWrapper<MemberEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public void regist(MemberRegisterVo memberRegisterVo) {
        MemberEntity memberEntity = new MemberEntity();
        //获取用户默认等级
        MemberLevelEntity levelEntity = memberLevelDao.getDefaultLevel();
        memberEntity.setLevelId(levelEntity.getId());
        showUserNameUnique(memberRegisterVo.getUsername());
        showPhoneUnique(memberRegisterVo.getPhone());
        memberEntity.setNickname(memberRegisterVo.getUsername());
        memberEntity.setMobile(memberRegisterVo.getPhone());
        //设置密码
        //MD5加盐加密
        BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
        String encode = bCryptPasswordEncoder.encode(memberRegisterVo.getPassword());
        memberEntity.setPassword(encode);
        memberEntity.setCreateTime(new Date());

        this.baseMapper.insert(memberEntity);
    }

    @Override
    public void showUserNameUnique(String userName) {
        Integer count = baseMapper.selectCount(new QueryWrapper<MemberEntity>().eq("username", userName));
        if(count>0){
            throw new UsernameException();
        }

    }

    @Override
    public void showPhoneUnique(String phone) {
        Integer count = baseMapper.selectCount(new QueryWrapper<MemberEntity>().eq("mobile", phone));
        if(count>0){
            throw new PhoneException();
        }

    }

    @Override
    public MemberEntity login(MemberLoginVo memberLoginVo) {
        String loginNameAcct = memberLoginVo.getLoginNameAcct();
        String password = memberLoginVo.getPassword();
        MemberEntity memberEntity = baseMapper.selectOne(new QueryWrapper<MemberEntity>().eq("mobile", loginNameAcct).or().eq("username", loginNameAcct));
        if(memberEntity != null){
            String passwordDB = memberEntity.getPassword();
            BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
            boolean matches = bCryptPasswordEncoder.matches(password, passwordDB);
            if(matches){
                //密码验证通过
                return memberEntity;

            }else{
                //密码验证不通过
                return null;
            }
        }else{
            return null;
        }
    }

    @Override
    public MemberEntity login(SocialUser socialUser) throws Exception {
        MemberEntity memberEntity = baseMapper.selectOne(new QueryWrapper<MemberEntity>().eq("social_uid", socialUser.getUid()));
        if(memberEntity != null){
            //在数据库里有社交登录账号，执行登录操作
            MemberEntity update = new MemberEntity();
            //更新令牌和过期时间
            update.setId(memberEntity.getId());
            update.setAccessToken(socialUser.getAccess_token());
            update.setExpiresIn(socialUser.getExpires_in());
            baseMapper.updateById(update);
            memberEntity.setAccessToken(socialUser.getAccess_token());
            memberEntity.setExpiresIn(socialUser.getExpires_in());
            return memberEntity;
        }else{
            //在数据库里没有社交登录账号，执行注册
            MemberEntity insert = new MemberEntity();
            //获取用户默认等级
            MemberLevelEntity levelEntity = memberLevelDao.getDefaultLevel();
            //查询微博用户的信息
            try {
                HashMap<String, String> query = new HashMap<>();
                query.put("access_token",socialUser.getAccess_token());
                query.put("uid",socialUser.getUid());
                HttpResponse response = HttpUtils.doGet("https://api.weibo.com", "/2/users/show.json", "get", new HashMap<String, String>(),query);
                if(response.getStatusLine().getStatusCode() == 200){
                    //查询微博用户信息成功
                    String s = EntityUtils.toString(response.getEntity());
                    JSONObject jsonObject = JSONObject.parseObject(s);
                    String name = jsonObject.getString("name");
                    String gender = jsonObject.getString("gender");
                    insert.setNickname(name);
                    insert.setGender("m".equals(gender)?1:0);
                }
            }catch (Exception e){

            }
            insert.setLevelId(levelEntity.getId());
            insert.setSocialUid(socialUser.getUid());
            insert.setAccessToken(socialUser.getAccess_token());
            insert.setExpiresIn(socialUser.getExpires_in());
            this.baseMapper.insert(insert);
        }

        return memberEntity;
    }

}