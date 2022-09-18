package com.atguigu.gulimall.cart.service;

import com.atguigu.gulimall.cart.vo.CartItemVo;
import com.atguigu.gulimall.cart.vo.CartVo;


import java.util.List;
import java.util.concurrent.ExecutionException;

public interface CartService {
    /**
     * 获取购物车里面的信息
     * @return
     */
    CartVo getCart() throws ExecutionException, InterruptedException;

    CartItemVo  addToCart(Long skuId, Integer num) throws ExecutionException, InterruptedException;

    CartItemVo getCartItem(Long skuId);

    void checkItem(Long skuId, Integer check);

    void deleteItem(Long skuId);

    List<CartItemVo> getUserCartItems();
}
