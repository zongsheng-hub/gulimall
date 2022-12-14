package com.atguigu.gulimall.seckill.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.to.mq.SeckillOrderTo;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberRespVo;
import com.atguigu.gulimall.seckill.feign.CouponFeignService;
import com.atguigu.gulimall.seckill.feign.ProductFeignService;
import com.atguigu.gulimall.seckill.interceptor.LoginUserInterceptor;
import com.atguigu.gulimall.seckill.service.SeckillService;
import com.atguigu.gulimall.seckill.to.SeckillSkuRedisTo;
import com.atguigu.gulimall.seckill.vo.SeckillSessionWithSkusVo;
import com.atguigu.gulimall.seckill.vo.SeckillSkuVo;
import com.atguigu.gulimall.seckill.vo.SkuInfoVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundGeoOperations;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SeckillServiceImpl implements SeckillService {
    @Autowired
    private CouponFeignService couponFeignService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ProductFeignService productFeignService;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final String SESSION__CACHE_PREFIX = "seckill:sessions:";

    private final String SECKILL_CHARE_PREFIX = "seckill:skus";

    private final String SKU_STOCK_SEMAPHORE = "seckill:stock:";    //+???????????????

    @Override
    public void uploadSeckillSkuLate3Days() {
        //???????????????????????????????????????
        R lates3DaySession = couponFeignService.getLates3DaySession();
        if(lates3DaySession.getCode() == 0){
            List<SeckillSessionWithSkusVo> data = lates3DaySession.getData(new TypeReference<List<SeckillSessionWithSkusVo>>() {
            }, "data");
            //?????????Redis
            //1?????????????????????
            saveSessionInfos(data);

            //2?????????????????????????????????
            saveSessionSkuInfo(data);


        }



    }

    @Override
    public List<SeckillSkuRedisTo> getCurrentSeckillSkus() {
        //??????????????????
        long time = System.currentTimeMillis();
        Set<String> keys = redisTemplate.keys(SESSION__CACHE_PREFIX+"*");
        for(String key : keys){
            String replace = key.replace("seckill:sessions:", "");
            String[] s = replace.split("_");
            Long startTime =Long.parseLong(s[0]);
            Long endTime = Long.parseLong(s[1]);
            if(time>=startTime && time<=endTime){
                //??????????????????????????????
                List<String> range = redisTemplate.opsForList().range(key, -100, 100);
                BoundHashOperations<String, String, String> ops = redisTemplate.boundHashOps(SECKILL_CHARE_PREFIX);
                List<String> listValue = ops.multiGet(range);
                if(listValue.size()!=0){
                    List<SeckillSkuRedisTo> collect = listValue.stream().map(item -> {
                        SeckillSkuRedisTo seckillSkuRedisTo = JSON.parseObject(item.toString(), SeckillSkuRedisTo.class);
                        return seckillSkuRedisTo;

                    }).collect(Collectors.toList());
                    return collect;
                }
                break;
            }

        }


        return null;
    }

    @Override
    public SeckillSkuRedisTo getSkuSeckillInfo(Long skuId) {
        BoundHashOperations<String, String, String> ops = redisTemplate.boundHashOps(SECKILL_CHARE_PREFIX);
        Set<String> keys = ops.keys();
        if(keys.size()>0 && keys!=null){
            String reg="\\d-"+skuId;
            for (String key : keys){
                if(Pattern.matches(reg,key)){
                    //????????????
                    String s = ops.get(key);
                    SeckillSkuRedisTo seckillSkuRedisTo = JSON.parseObject(s, SeckillSkuRedisTo.class);
                    Long currentTime = System.currentTimeMillis();
                    if(currentTime>=seckillSkuRedisTo.getStartTime() && currentTime<=seckillSkuRedisTo.getEndTime()){
                        return seckillSkuRedisTo;
                    }else{
                        //???????????????????????????????????????
                        seckillSkuRedisTo.setRandomCode(null);
                        return seckillSkuRedisTo;
                    }
                }

            }
        }

        return null;
    }

    @Override
    public String kill(String killId, String key, Integer num) {
        MemberRespVo respVo = LoginUserInterceptor.loginUser.get();
        BoundHashOperations<String, String, String> ops = redisTemplate.boundHashOps(SESSION__CACHE_PREFIX);
        String json = ops.get(killId);
        if(StringUtils.isEmpty(json)){
            return null;
        }else{
            //????????????
            SeckillSkuRedisTo seckillSkuRedisTo = JSON.parseObject(json, SeckillSkuRedisTo.class);
            long time = System.currentTimeMillis();
            Long startTime = seckillSkuRedisTo.getStartTime();
            Long endTime = seckillSkuRedisTo.getEndTime();
            if(time >= startTime && time<=endTime){
                //???????????????
                String randomCode = seckillSkuRedisTo.getRandomCode();
                String skuId = seckillSkuRedisTo.getPromotionId()+"-"+seckillSkuRedisTo.getSkuId();
                if(key.equals(randomCode) && killId.equals(skuId)){
                    //????????????????????????
                    if(num <= seckillSkuRedisTo.getSeckillLimit()){
                        //???????????????????????????
                        long ttl = endTime-startTime;
                        Boolean aBoolean = redisTemplate.opsForValue().setIfAbsent(respVo.getId() + "-" + skuId, num.toString(), ttl, TimeUnit.MILLISECONDS);
                        if(aBoolean){
                            //????????????
                            RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE);
                            try {
                                //????????????
                                semaphore.tryAcquire(num,100,TimeUnit.MILLISECONDS);
                                //??????mq??????
                                String timeId = IdWorker.getTimeId();
                                SeckillOrderTo orderTo = new SeckillOrderTo();
                                orderTo.setOrderSn(timeId);
                                orderTo.setMemberId(respVo.getId());
                                orderTo.setNum(num);
                                orderTo.setPromotionSessionId(seckillSkuRedisTo.getPromotionSessionId());
                                orderTo.setSkuId(seckillSkuRedisTo.getSkuId());
                                orderTo.setSeckillPrice(seckillSkuRedisTo.getSeckillPrice());
                                rabbitTemplate.convertAndSend("order-event-exchange","order.seckill.order",orderTo);
                                return timeId;
                            } catch (InterruptedException e) {
                                return null;
                            }
                        }else{
                            return null;
                        }
                    }else {
                        return null;
                    }

                }else {
                    return null;
                }
            }else{
                return null;
            }

        }
    }

    private void saveSessionInfos(List<SeckillSessionWithSkusVo> data){
        data.stream().forEach(session->{
            //???????????????????????????????????????????????????
            long startTime = session.getStartTime().getTime();
            long endTime = session.getEndTime().getTime();
            String key = SESSION__CACHE_PREFIX+startTime+"_"+endTime;
            //??????????????????????????????skuID
            //???????????????
            if(!redisTemplate.hasKey(key)){
                List<String> value = session.getSkuRelations().stream().map(item -> item.getPromotionSessionId() + "-" + item.getSkuId().toString()).collect(Collectors.toList());
                redisTemplate.opsForList().leftPushAll(key,value);
            }


        });
    }
    //?????????????????????????????????
    private void saveSessionSkuInfo(List<SeckillSessionWithSkusVo> data){
        //??????hash???????????????hash
        BoundHashOperations<String, Object, Object> operations = redisTemplate.boundHashOps(SECKILL_CHARE_PREFIX);
        data.stream().forEach(session->{
            List<SeckillSkuVo> relationSkus = session.getSkuRelations();
            session.getSkuRelations().stream().forEach(seckillSkuVo -> {
                //???????????????
                String token = UUID.randomUUID().toString().replace("-", "");
                String redisKey = seckillSkuVo.getPromotionSessionId().toString() + "-" + seckillSkuVo.getSkuId().toString();
                if(!operations.hasKey(redisKey)){
                    //??????????????????
                    SeckillSkuRedisTo seckillSkuRedisTo = new SeckillSkuRedisTo();
                    //Long skuId = seckillSkuRedisTo.getSkuId();
                    Long skuId = seckillSkuVo.getSkuId();
                    //??????????????????????????????????????????
                    R r = productFeignService.info(skuId);
                    if(r.getCode() == 0){
                        SkuInfoVo skuInfo = r.getData(new TypeReference<SkuInfoVo>() {
                        }, "skuInfo");
                        seckillSkuRedisTo.setSkuInfo(skuInfo);
                    }

                    //?????????????????????
                    BeanUtils.copyProperties(seckillSkuVo,seckillSkuRedisTo);
                    //3??????????????????????????????????????????
                    seckillSkuRedisTo.setStartTime(session.getStartTime().getTime());
                    seckillSkuRedisTo.setEndTime(session.getEndTime().getTime());

                    //4???????????????????????????????????????????????????
                    seckillSkuRedisTo.setRandomCode(token);

                    //?????????json????????????Redis???
                    String seckillValue = JSON.toJSONString(seckillSkuRedisTo);
                    operations.put(seckillSkuVo.getPromotionSessionId().toString() + "-" + seckillSkuVo.getSkuId().toString(),seckillValue);
                    //???????????????????????????????????????????????????????????????????????????
                    //5??????????????????????????????Redisson?????????????????????
                    // ????????????????????????????????????
                    RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE + token);
                    semaphore.trySetPermits(seckillSkuRedisTo.getSeckillCount());

                }
            });


        });

    }


}
