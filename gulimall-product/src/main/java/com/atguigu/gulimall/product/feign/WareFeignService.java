package com.atguigu.gulimall.product.feign;

import com.atguigu.common.to.SkuHasStockTo;
import com.atguigu.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
@FeignClient("gulimall-ware")
public interface WareFeignService {
    @PostMapping("ware/waresku/hasStock")
    public R getSkuHasStock(@RequestBody List<Long> skuIds);
}
