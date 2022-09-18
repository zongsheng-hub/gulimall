package com.atguigu.gulimall.product.web;

import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import com.atguigu.gulimall.product.vo.Catelog2Vo;
import net.bytebuddy.asm.Advice;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class IndexController {

    @Autowired
    private CategoryService categoryService;
    @Autowired
    private RedissonClient redisson;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @GetMapping({"/","/index.html"})
    public String indexPage(Model model){
        //查询所有的一级分类
        List<CategoryEntity> categoryEntities =  categoryService.getLevel1Categories();
        model.addAttribute("categorys",categoryEntities);
        return "index";

    }

    //index/json/catalog.json
    @GetMapping(value = "index/json/catalog.json")
    @ResponseBody
    public Map<String, List<Catelog2Vo>> getCatelogJson(){
        Map<String, List<Catelog2Vo>> ctgall = categoryService.getCatelogJson();
        return ctgall;
    }

    @ResponseBody
    @GetMapping("/hello")
    public String hello(){
        RLock myLock = redisson.getLock("myLock");
        myLock.lock();
        try {
            System.out.println("执行业务方法================================"+Thread.currentThread().getId());
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            System.out.println("执行解锁方法*************************************"+Thread.currentThread().getId());
            myLock.unlock();

        }
        return "hello";

    }

    /**
     * 读写锁
     *
     */

    @ResponseBody
    @GetMapping("/write")
    public String writeLock(){
        RReadWriteLock wrLock = redisson.getReadWriteLock("wrLock");
        String s = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set("writeInfo",s);
        RLock rLock = wrLock.writeLock();
        try {

            rLock.lock();
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            rLock.unlock();

        }
        return s;
    }

    @ResponseBody
    @GetMapping("/read")
    public String readLock(){
        RReadWriteLock wrLock = redisson.getReadWriteLock("wrLock");
        String writeInfo = stringRedisTemplate.opsForValue().get("writeInfo");
        RLock rLock = wrLock.readLock();
        rLock.lock();
        rLock.unlock();
        return  writeInfo;
    }


}
