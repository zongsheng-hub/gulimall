package com.atguigu.gulimall.seckill.scheduled;

import com.atguigu.gulimall.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SeckillSkuScheduled {
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private SeckillService seckillService;

    private final String upload_lock="seckill:upload:lock";
    @Scheduled(cron = "0 * * * * ?")
    public void uploadSeckillSkuLate3Days(){
        log.info("上架商品。。。。。。。。。。。。。saveSessionSkuInfo");
        RLock lock = redissonClient.getLock(upload_lock);
        lock.lock(30, TimeUnit.SECONDS);
        try {
            seckillService.uploadSeckillSkuLate3Days();
        }finally {
            //解锁
            lock.unlock();

        }


    }
}
