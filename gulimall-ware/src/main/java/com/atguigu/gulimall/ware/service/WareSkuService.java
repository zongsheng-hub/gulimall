package com.atguigu.gulimall.ware.service;

import com.atguigu.common.to.SkuHasStockTo;
import com.atguigu.gulimall.ware.vo.WareSkuLockVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.gulimall.ware.entity.WareSkuEntity;

import java.util.List;
import java.util.Map;

/**
 * 商品库存
 *
 * @author fuzs
 * @email fuzs@gmail.com
 * @date 2022-04-27 13:33:58
 */
public interface WareSkuService extends IService<WareSkuEntity> {

    PageUtils queryPage(Map<String, Object> params);

    void addStore(Long skuId, Long wareId, Integer skuNum);

    List<SkuHasStockTo> getHasStock(List<Long> skuIds);

    boolean orderLockWare(WareSkuLockVo vo);
}

