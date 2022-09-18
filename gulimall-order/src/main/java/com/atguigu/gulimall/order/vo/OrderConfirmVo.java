package com.atguigu.gulimall.order.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class OrderConfirmVo {
    //收货地址
    @Getter @Setter
    List<MemberAddressVo> address;
    //所有选中的购物项
    @Getter @Setter
    List<OrderItemVo> items;
    //发票记录
    //优惠券信息
    @Getter @Setter
    Integer integration;
    //防止重复提交令牌
    @Setter @Getter
    private String orderToken;
    @Setter @Getter
    Map<Long, Boolean> stocks;
  //  BigDecimal total;//订单总额
    public BigDecimal getTotal(){
        BigDecimal totalNum = BigDecimal.ZERO;
        if(items != null && items.size()>0){
            for(OrderItemVo item : items){
                BigDecimal multiply = item.getPrice().multiply(new BigDecimal(item.getCount().toString()));
                totalNum = totalNum.add(multiply);
            }
        }
        return totalNum;
    }

  //  BigDecimal payPrice;//应付价格

    public BigDecimal getPayPrice(){
        return  getTotal();
    }
    //商品总量
    public Integer getCount(){
        Integer i = 0;
        if(items != null && items.size()>0){
            for(OrderItemVo item : items){
                i = i+item.getCount();
            }
        }
        return i;
    }

}
