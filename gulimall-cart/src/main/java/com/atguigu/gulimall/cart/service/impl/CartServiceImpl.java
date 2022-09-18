package com.atguigu.gulimall.cart.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.CartConstant;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.cart.feign.ProductFeignService;
import com.atguigu.gulimall.cart.interceptor.CartInterceptor;
import com.atguigu.gulimall.cart.service.CartService;
import com.atguigu.gulimall.cart.to.UserInfoTo;
import com.atguigu.gulimall.cart.vo.CartItemVo;
import com.atguigu.gulimall.cart.vo.CartVo;
import com.atguigu.gulimall.cart.vo.SkuInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundGeoOperations;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CartServiceImpl implements CartService {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ProductFeignService productFeignService;
    @Autowired
    private ThreadPoolExecutor executor;
    @Override
    public CartVo getCart() throws ExecutionException, InterruptedException {
        //获取当前用户的信息
        UserInfoTo userInfoTo = CartInterceptor.toThreadLocal.get();
        if(userInfoTo != null){
            CartVo cartVo = new CartVo();
            //已登录
            String cartKey = CartConstant.CART_PREFIX+userInfoTo.getUserId();
            //临时登录的购物车的key
            String tempCartKey = CartConstant.CART_PREFIX+userInfoTo.getUserKey();

            List<CartItemVo> cartItems = getCartItems(tempCartKey);
            if(cartItems!=null && cartItems.size()>0){
                for (CartItemVo cartItem : cartItems) {
                    //合并到已登录的账户的购物车
                    addToCart(cartItem.getSkuId(),cartItem.getCount());
                }
                //清除临时购物车的数据
                clearCartInfo(tempCartKey);
            }
            List<CartItemVo> cartItems1 = getCartItems(cartKey);
            cartVo.setItems(cartItems1);
            return cartVo;
        }else {
            //未登录
            CartVo cartVo = new CartVo();
            String cartKey = CartConstant.CART_PREFIX+userInfoTo.getUserKey();
            List<CartItemVo> cartItems = getCartItems(cartKey);
            cartVo.setItems(cartItems);
            return cartVo;
        }
    }

    @Override
    public CartItemVo  addToCart(Long skuId, Integer num) throws ExecutionException, InterruptedException {

        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        String s1 = (String)cartOps.get(skuId.toString());
        if(StringUtils.isEmpty(s1)){
            //购物车里没有商品
            //商品添加到购物车
            CartItemVo cartItemVo = new CartItemVo();
            CompletableFuture<Void> getSkuInfoTask = CompletableFuture.runAsync(() -> {
                R info = productFeignService.info(skuId);
                SkuInfoVo skuInfo = info.getData(new TypeReference<SkuInfoVo>() {
                }, "skuInfo");
                cartItemVo.setCheck(true);
                cartItemVo.setCount(num);
                cartItemVo.setImage(skuInfo.getSkuDefaultImg());
                cartItemVo.setTitle(skuInfo.getSkuTitle());
                cartItemVo.setSkuId(skuId);
                cartItemVo.setPrice(skuInfo.getPrice());
            }, executor);

            CompletableFuture<Void> getSkuAttrs = CompletableFuture.runAsync(() -> {
                List<String> skuSaleAttrValues = productFeignService.getSkuSaleAttrValues(skuId);
                cartItemVo.setSkuAttrValues(skuSaleAttrValues);

            }, executor);
            CompletableFuture.allOf(getSkuInfoTask,getSkuAttrs).get();
            String s = JSON.toJSONString(cartItemVo);
            cartOps.put(skuId.toString(),s);


            return cartItemVo;

        }else{
            //购物车有此商品，修改数量即可
            CartItemVo cartItemVo = JSON.parseObject(s1, CartItemVo.class);
            cartItemVo.setCount(cartItemVo.getCount()+num);
            String jsonString = JSON.toJSONString(cartItemVo);
            cartOps.put(skuId.toString(),jsonString);
            return cartItemVo;
        }

    }

    @Override
    public CartItemVo getCartItem(Long skuId) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        String  s = (String) cartOps.get(skuId.toString());
        CartItemVo cartItemVo = JSON.parseObject(s, CartItemVo.class);

        return cartItemVo;
    }

    @Override
    public void checkItem(Long skuId, Integer check) {
        //获取操作那个数据库
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        //更改状态
        CartItemVo cartItem = getCartItem(skuId);
        cartItem.setCheck(check==1?true:false);
        String s = JSON.toJSONString(cartItem);
        cartOps.put(skuId.toString(),s);

    }

    @Override
    public void deleteItem(Long skuId) {
        //获取操作那个数据库
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        cartOps.delete(skuId);


    }

    @Override
    public List<CartItemVo> getUserCartItems() {
        log.info("**********************getUserCartItems************************");
        List<CartItemVo> cartItemVoList = new ArrayList<>();
        //获取当前用户登录的信息
        UserInfoTo userInfoTo = CartInterceptor.toThreadLocal.get();
        //如果用户未登录直接返回null
        if (userInfoTo.getUserId() == null) {
            return null;
        } else {
            //获取购物车项
            String cartKey = CartConstant.CART_PREFIX + userInfoTo.getUserId();
            //获取所有的
            List<CartItemVo> cartItems = getCartItems(cartKey);
//            if (cartItems == null) {
//                throw new CartExceptionHandler();
//            }
            //筛选出选中的
            cartItemVoList = cartItems.stream()
                    .filter(items -> items.getCheck())
                    .map(item -> {
                        //更新为最新的价格（查询数据库）
                        BigDecimal price = productFeignService.getPrice(item.getSkuId());
                        item.setPrice(price);
                        return item;
                    })
                    .collect(Collectors.toList());
        }

        return cartItemVoList;
    }

    private BoundHashOperations<String, Object, Object> getCartOps() {
        //获取当前用户的信息
        UserInfoTo userInfoTo = CartInterceptor.toThreadLocal.get();
        String cartKey="";
        if(userInfoTo!=null){
            cartKey= CartConstant.CART_PREFIX+userInfoTo.getUserId();
        }else{
            cartKey=CartConstant.CART_PREFIX+userInfoTo.getUserKey();
        }

        BoundHashOperations<String, Object, Object> operations = redisTemplate.boundHashOps(cartKey);
        return operations;
    }
    //获取未登录的购物车商品信息
    private List<CartItemVo> getCartItems(String cartKey){
        BoundHashOperations<String, Object, Object> operations = redisTemplate.boundHashOps(cartKey);
        List<Object> values = operations.values();
        if(values != null && values.size()>0){
            List<CartItemVo> collect = values.stream().map((item) -> {
                String s = (String) item;
                CartItemVo cartItemVo = JSON.parseObject(s, CartItemVo.class);
                return cartItemVo;

            }).collect(Collectors.toList());
            return collect;
        }
        return null;

    }
    //清除临时购物车的数据
    private void clearCartInfo(String cartKey){
        redisTemplate.delete(cartKey);
    }
}
