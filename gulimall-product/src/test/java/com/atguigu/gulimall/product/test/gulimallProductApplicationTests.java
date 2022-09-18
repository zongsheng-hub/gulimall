package com.atguigu.gulimall.product.test;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.OSSClientBuilder;
import com.atguigu.gulimall.product.dao.AttrGroupDao;
import com.atguigu.gulimall.product.dao.SkuSaleAttrValueDao;
import com.atguigu.gulimall.product.entity.BrandEntity;
import com.atguigu.gulimall.product.service.BrandService;
import com.atguigu.gulimall.product.vo.SkuItemVo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@RunWith(SpringRunner.class)
public class gulimallProductApplicationTests {
    @Autowired
    BrandService brandService;
    @Autowired
    OSSClient ossClient;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private AttrGroupDao attrGroupDao;
    @Autowired
    private SkuSaleAttrValueDao skuSaleAttrValueDao;

    @Test
    public void contextLoads() {
        BrandEntity brandEntity = new BrandEntity();
        brandEntity.setDescript("hello");
        brandEntity.setName("华为");
        brandService.save(brandEntity);
        System.out.println("保存成功");
    }

    @Test
    public void redisTest() {
        ValueOperations<String, String> stringStringValueOperations = redisTemplate.opsForValue();
        stringStringValueOperations.set("hello","world_"+ UUID.randomUUID().toString());
        String hello = stringStringValueOperations.get("hello");
        System.out.println(hello);
    }

    @Test
    public void testUpload() throws FileNotFoundException {
         //Endpoint以杭州为例，其它Region请按实际情况填写。
      //   String endpoint = "oss-cn-beijing.aliyuncs.com";
        // 云账号AccessKey有所有API访问权限，建议遵循阿里云安全最佳实践，创建并使用RAM子账号进行API访问或日常运维，请登录 https://ram.console.aliyun.com 创建。
     //   String accessKeyId = "LTAI5tMZsfRZAx4q7pSzbj4Z";
      //   String accessKeySecret = "aZpv4f5R6BWnkSBQy1BGXre9WLISWj";

        // 创建OSSClient实例。
      //   OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        // 上传文件流。
        InputStream inputStream = new FileInputStream("C:\\Users\\FZS\\Desktop\\car.jpg");
        ossClient.putObject("gulimall-fuzs", "car.jpg", inputStream);

        // 关闭OSSClient。
        ossClient.shutdown();
        System.out.println("上传成功.");
    }

    @Test
    public void redissonTest(){
        System.out.println(redissonClient);

    }

    @Test
    public void testGetSkuItem(){
        List<SkuItemVo.SpuItemAttrGroupVo> attrGroupWithAttrsBySkuId = attrGroupDao.getAttrGroupWithAttrsBySkuId(14L, 225L);
        attrGroupWithAttrsBySkuId.forEach(item->{
            System.out.println(item);
        });

    }

    @Test
    public void testGetSaleAttr(){
        List<SkuItemVo.SkuItemSaleAttrVo> skuSaleAttrBySpuId = skuSaleAttrValueDao.getSkuSaleAttrBySpuId(14L);
        skuSaleAttrBySpuId.forEach(item->{
            System.out.println(item);
        });

    }
}

