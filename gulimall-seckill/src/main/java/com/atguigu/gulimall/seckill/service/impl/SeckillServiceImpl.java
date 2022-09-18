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

    private final String SKU_STOCK_SEMAPHORE = "seckill:stock:";    //+商品随机码

    @Override
    public void uploadSeckillSkuLate3Days() {
        //查询三天需要上传秒杀的产品
        R lates3DaySession = couponFeignService.getLates3DaySession();
        if(lates3DaySession.getCode() == 0){
            List<SeckillSessionWithSkusVo> data = lates3DaySession.getData(new TypeReference<List<SeckillSessionWithSkusVo>>() {
            }, "data");
            //缓存到Redis
            //1、缓存活动信息
            saveSessionInfos(data);

            //2、缓存活动中的商品信息
            saveSessionSkuInfo(data);


        }



    }

    @Override
    public List<SeckillSkuRedisTo> getCurrentSeckillSkus() {
        //获取当前时间
        long time = System.currentTimeMillis();
        Set<String> keys = redisTemplate.keys(SESSION__CACHE_PREFIX+"*");
        for(String key : keys){
            String replace = key.replace("seckill:sessions:", "");
            String[] s = replace.split("_");
            Long startTime =Long.parseLong(s[0]);
            Long endTime = Long.parseLong(s[1]);
            if(time>=startTime && time<=endTime){
                //查询秒杀时间内的场次
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
                    //匹配上了
                    String s = ops.get(key);
                    SeckillSkuRedisTo seckillSkuRedisTo = JSON.parseObject(s, SeckillSkuRedisTo.class);
                    Long currentTime = System.currentTimeMillis();
                    if(currentTime>=seckillSkuRedisTo.getStartTime() && currentTime<=seckillSkuRedisTo.getEndTime()){
                        return seckillSkuRedisTo;
                    }else{
                        //当前时间不在秒杀时间范围内
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
            //校验时间
            SeckillSkuRedisTo seckillSkuRedisTo = JSON.parseObject(json, SeckillSkuRedisTo.class);
            long time = System.currentTimeMillis();
            Long startTime = seckillSkuRedisTo.getStartTime();
            Long endTime = seckillSkuRedisTo.getEndTime();
            if(time >= startTime && time<=endTime){
                //校验随机码
                String randomCode = seckillSkuRedisTo.getRandomCode();
                String skuId = seckillSkuRedisTo.getPromotionId()+"-"+seckillSkuRedisTo.getSkuId();
                if(key.equals(randomCode) && killId.equals(skuId)){
                    //校验数量的正确性
                    if(num <= seckillSkuRedisTo.getSeckillLimit()){
                        //验证这个人是否买过
                        long ttl = endTime-startTime;
                        Boolean aBoolean = redisTemplate.opsForValue().setIfAbsent(respVo.getId() + "-" + skuId, num.toString(), ttl, TimeUnit.MILLISECONDS);
                        if(aBoolean){
                            //占位成功
                            RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE);
                            try {
                                //减信号量
                                semaphore.tryAcquire(num,100,TimeUnit.MILLISECONDS);
                                //发送mq消息
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
            //获取当前开始时间和结束时间的时间戳
            long startTime = session.getStartTime().getTime();
            long endTime = session.getEndTime().getTime();
            String key = SESSION__CACHE_PREFIX+startTime+"_"+endTime;
            //获取活动中所有商品的skuID
            //幂等性判断
            if(!redisTemplate.hasKey(key)){
                List<String> value = session.getSkuRelations().stream().map(item -> item.getPromotionSessionId() + "-" + item.getSkuId().toString()).collect(Collectors.toList());
                redisTemplate.opsForList().leftPushAll(key,value);
            }


        });
    }
    //缓存秒杀单中的商品信息
    private void saveSessionSkuInfo(List<SeckillSessionWithSkusVo> data){
        //准备hash操作，绑定hash
        BoundHashOperations<String, Object, Object> operations = redisTemplate.boundHashOps(SECKILL_CHARE_PREFIX);
        data.stream().forEach(session->{
            List<SeckillSkuVo> relationSkus = session.getSkuRelations();
            session.getSkuRelations().stream().forEach(seckillSkuVo -> {
                //生成随机码
                String token = UUID.randomUUID().toString().replace("-", "");
                String redisKey = seckillSkuVo.getPromotionSessionId().toString() + "-" + seckillSkuVo.getSkuId().toString();
                if(!operations.hasKey(redisKey)){
                    //缓存商品信息
                    SeckillSkuRedisTo seckillSkuRedisTo = new SeckillSkuRedisTo();
                    //Long skuId = seckillSkuRedisTo.getSkuId();
                    Long skuId = seckillSkuVo.getSkuId();
                    //调用远程方法查询商品基本信息
                    R r = productFeignService.info(skuId);
                    if(r.getCode() == 0){
                        SkuInfoVo skuInfo = r.getData(new TypeReference<SkuInfoVo>() {
                        }, "skuInfo");
                        seckillSkuRedisTo.setSkuInfo(skuInfo);
                    }

                    //保存秒杀单信息
                    BeanUtils.copyProperties(seckillSkuVo,seckillSkuRedisTo);
                    //3、设置当前商品的秒杀时间信息
                    seckillSkuRedisTo.setStartTime(session.getStartTime().getTime());
                    seckillSkuRedisTo.setEndTime(session.getEndTime().getTime());

                    //4、设置商品的随机码（防止恶意攻击）
                    seckillSkuRedisTo.setRandomCode(token);

                    //序列化json格式存入Redis中
                    String seckillValue = JSON.toJSONString(seckillSkuRedisTo);
                    operations.put(seckillSkuVo.getPromotionSessionId().toString() + "-" + seckillSkuVo.getSkuId().toString(),seckillValue);
                    //如果当前这个场次的商品库存信息已经上架就不需要上架
                    //5、使用库存作为分布式Redisson信号量（限流）
                    // 使用库存作为分布式信号量
                    RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE + token);
                    semaphore.trySetPermits(seckillSkuRedisTo.getSeckillCount());

                }
            });


        });

    }


}
