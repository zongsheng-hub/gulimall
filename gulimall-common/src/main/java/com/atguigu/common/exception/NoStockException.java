package com.atguigu.common.exception;

public class NoStockException extends RuntimeException{
    private Long skuId;
    public NoStockException(Long skuId){
        super("商品"+skuId+"商品没有库存");
    }
    public NoStockException(){
        super("商品没有库存");
    }
}
