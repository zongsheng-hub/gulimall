package com.atguigu.gulimall.seckill.scheduled;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@EnableScheduling
public class HelloScheduled {
    @Scheduled(cron = "*/5 * * ? * *")
    public void helloScheduled(){
        log.info("hello");
    }
}
