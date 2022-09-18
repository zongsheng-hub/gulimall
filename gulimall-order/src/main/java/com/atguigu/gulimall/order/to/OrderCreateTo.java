package com.atguigu.gulimall.order.to;


import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.entity.OrderItemEntity;
import com.atguigu.gulimall.order.vo.OrderItemVo;
import lombok.Data;

import java.math.BigDecimal;

import java.util.List;
@Data
public class OrderCreateTo {
    private OrderEntity orderEntity;
    private List<OrderItemEntity> orderItems;
    private BigDecimal payPrice;
    private BigDecimal fare;


}
