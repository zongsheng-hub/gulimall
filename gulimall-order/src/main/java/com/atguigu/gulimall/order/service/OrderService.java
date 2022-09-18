package com.atguigu.gulimall.order.service;

import com.atguigu.common.to.mq.SeckillOrderTo;
import com.atguigu.gulimall.order.vo.*;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.gulimall.order.entity.OrderEntity;

import java.util.Map;

/**
 * 订单
 *
 * @author fuzs
 * @email fuzs@gmail.com
 * @date 2022-04-27 13:28:14
 */
public interface OrderService extends IService<OrderEntity> {

    PageUtils queryPage(Map<String, Object> params);

    OrderConfirmVo confirm();

    SubmitOrderRespVo submitOrder(OrderSubmitVo vo);

    OrderEntity getOrderByOrderSn(String orderSn);

    void closeOrder(OrderEntity entity);

    PayVo OrderPay(String orderSn);

    PageUtils listWithItem(Map<String, Object> params);

    String handleResPay(PayAsyncVo vo);

    void createSeckillOrder(SeckillOrderTo to);
}

