package com.atguigu.gulimall.order.vo;

import com.atguigu.gulimall.order.entity.OrderEntity;
import lombok.Data;

@Data
public class SubmitOrderRespVo {
    private OrderEntity orderEntity;
    private Integer Code;
}
