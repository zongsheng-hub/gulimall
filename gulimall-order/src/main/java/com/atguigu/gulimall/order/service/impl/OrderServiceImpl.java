package com.atguigu.gulimall.order.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.to.mq.OrderTo;
import com.atguigu.common.to.mq.SeckillOrderTo;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberRespVo;

import com.atguigu.gulimall.order.constant.OrderConst;
import com.atguigu.gulimall.order.entity.OrderItemEntity;
import com.atguigu.gulimall.order.entity.PaymentInfoEntity;
import com.atguigu.gulimall.order.enume.OrderStatusEnume;
import com.atguigu.gulimall.order.feign.CartFeignService;
import com.atguigu.gulimall.order.feign.MemberFeignService;
import com.atguigu.gulimall.order.feign.ProductFeignService;
import com.atguigu.gulimall.order.feign.WareFeignService;
import com.atguigu.gulimall.order.interceptor.LoginUserInterceptor;
import com.atguigu.gulimall.order.service.OrderItemService;
import com.atguigu.gulimall.order.service.PaymentInfoService;
import com.atguigu.gulimall.order.to.OrderCreateTo;
import com.atguigu.gulimall.order.vo.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.order.dao.OrderDao;
import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.service.OrderService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {
    @Autowired
    private MemberFeignService memberFeignService;
    @Autowired
    private CartFeignService cartFeignService;
    @Autowired
    private ThreadPoolExecutor executor;
    @Autowired
    private WareFeignService wareFeignService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private ProductFeignService productFeignService;
    @Autowired
    private OrderItemService orderItemService;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private PaymentInfoService paymentInfoService;

    private ThreadLocal<OrderSubmitVo> orderSubmitThreadLocal = new ThreadLocal<>();

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public OrderConfirmVo confirm() {
        OrderConfirmVo orderConfirmVo = new OrderConfirmVo();
        //获取当前用户
        MemberRespVo memberRespVo = LoginUserInterceptor.loginUser.get();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if(memberRespVo != null){
//            System.out.println("address线程=========="+Thread.currentThread().getName());
//            CompletableFuture<Void> addressFuture = CompletableFuture.runAsync(() -> {
//                RequestContextHolder.setRequestAttributes(requestAttributes);
//                List<MemberAddressVo> userAddress = memberFeignService.getUserAddress(memberRespVo.getId());
//                orderConfirmVo.setAddress(userAddress);
//            }, executor);
//
//            CompletableFuture<Void> ItemFuture = CompletableFuture.runAsync(() -> {
//                System.out.println("Item线程=========="+Thread.currentThread().getName());
//                RequestContextHolder.setRequestAttributes(requestAttributes);
//                List<OrderItemVo> currentCartItems = cartFeignService.getCurrentCartItems();
//                orderConfirmVo.setItems(currentCartItems);
//            }, executor);


            //已登录

            //收货地址
            List<MemberAddressVo> userAddress = memberFeignService.getUserAddress(memberRespVo.getId());
            orderConfirmVo.setAddress(userAddress);


            List<OrderItemVo> currentCartItems = cartFeignService.getCurrentCartItems();
            orderConfirmVo.setItems(currentCartItems);

            //获取库存状态
            List<Long> skuIds = currentCartItems.stream().map(item -> item.getSkuId()).collect(Collectors.toList());
            R skuHasStock = wareFeignService.getSkuHasStock(skuIds);
            List<SkuStockVo> skuStockVos = skuHasStock.getData(new TypeReference<List<SkuStockVo>>() {
            }, "data");
            if(skuStockVos!=null&&skuStockVos.size()>0){
                Map<Long, Boolean> skuHasStockMap = skuStockVos.stream().collect(Collectors.toMap(SkuStockVo::getSkuId, SkuStockVo::getHasStock));
                orderConfirmVo.setStocks(skuHasStockMap);
            }



            //3、查询用户积分
            Integer integration = memberRespVo.getIntegration();
            orderConfirmVo.setIntegration(integration);
           // CompletableFuture.allOf(addressFuture,ItemFuture);
            //生成令牌
            String replace = UUID.randomUUID().toString().replace("-", "");
            //服务端存放UUID
            redisTemplate.opsForValue().set(OrderConst.USER_ORDER_TOKEN_PREFIX+memberRespVo.getId(),replace,30, TimeUnit.MINUTES);
            //客户端存放token
            orderConfirmVo.setOrderToken(replace);

            return orderConfirmVo;


        }else{
            //没登录
            return null;

        }



    }
    //@GlobalTransactional
    @Transactional(rollbackFor = Exception.class)
    @Override
    public SubmitOrderRespVo submitOrder(OrderSubmitVo vo) {
        orderSubmitThreadLocal.set(vo);
        SubmitOrderRespVo submitOrderRespVo = new SubmitOrderRespVo();
        submitOrderRespVo.setCode(0);
        //校验令牌
        String orderToken = vo.getOrderToken();
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        //获取当前用户
        MemberRespVo memberRespVo = LoginUserInterceptor.loginUser.get();
        Long execute = (Long) redisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class), Arrays.asList(OrderConst.USER_ORDER_TOKEN_PREFIX + memberRespVo.getId()), orderToken);
        if(execute == 0L){
            submitOrderRespVo.setCode(1);
            //令牌校验失败
            return submitOrderRespVo;
        }else{
            //令牌校验成功
            //验价
            OrderCreateTo order = createOrder();
            BigDecimal payAmount = order.getOrderEntity().getPayAmount();
            BigDecimal payPrice = vo.getPayPrice();
            if (Math.abs(payAmount.subtract(payPrice).doubleValue()) < 0.01){
                //验价成功
                //保存订单
                saveOrder(order);
                WareSkuLockVo wareSkuLockVo = new WareSkuLockVo();
                wareSkuLockVo.setOrderSn(order.getOrderEntity().getOrderSn());
                List<OrderItemVo> orderItemVos = order.getOrderItems().stream().map((item) -> {
                    OrderItemVo orderItemVo = new OrderItemVo();
                    orderItemVo.setSkuId(item.getSkuId());
                    orderItemVo.setCount(item.getSkuQuantity());
                    orderItemVo.setTitle(item.getSkuName());
                    return orderItemVo;
                }).collect(Collectors.toList());
                wareSkuLockVo.setLocks(orderItemVos);
                //调用远程接口锁定库存
                R r = wareFeignService.orderLockWare(wareSkuLockVo);
                if(r.getCode() == 0){
                    //锁定成功
                    submitOrderRespVo.setOrderEntity(order.getOrderEntity());
//                    MessagePostProcessor messagePostProcessor = new MessagePostProcessor() {
//                        @Override
//                        public Message postProcessMessage(Message message) throws AmqpException {
//                            message.getMessageProperties().setExpiration("1000");
//                            return message;
//                        }
//                    };
//              发送消息
                    rabbitTemplate.convertAndSend("order-event-exchange","order.create.order",order.getOrderEntity());
                    return submitOrderRespVo;

                }else{

                    submitOrderRespVo.setCode(3);

                    return submitOrderRespVo;


                }
            }else{
                submitOrderRespVo.setCode(2);
                //验价失败
                return submitOrderRespVo;
            }


        }

    }

    @Override
    public OrderEntity getOrderByOrderSn(String orderSn) {
        OrderEntity orderEntity = this.baseMapper.selectOne(new QueryWrapper<OrderEntity>().eq("order_sn", orderSn));
        return orderEntity;
    }

    @Override
    public void closeOrder(OrderEntity entity) {
        OrderEntity orderEntity = this.getById(entity.getId());
        if(orderEntity.getStatus().equals(OrderStatusEnume.CREATE_NEW.getCode())){
            //更改订单状态
            OrderEntity update = new OrderEntity();
            update.setId(orderEntity.getId());
            update.setStatus(OrderStatusEnume.CANCLED.getCode());
            this.updateById(update);
            //发送消息给MQ,解库存
            OrderTo orderTo = new OrderTo();
            BeanUtils.copyProperties(orderEntity,orderTo);

            try {
                rabbitTemplate.convertAndSend("order-event-exchange", "order.release.other",orderTo);
                //将消息保存到mq_message表，定期重试
            } catch (AmqpException e) {

                e.printStackTrace();
            }


        }


    }

    @Override
    public PayVo OrderPay(String orderSn) {
        PayVo payVo = new PayVo();
        OrderEntity order = this.getOrderByOrderSn(orderSn);
        //保留两位小数点，向上取值
        BigDecimal bigDecimal = order.getPayAmount().setScale(2, BigDecimal.ROUND_UP);
        payVo.setTotal_amount(bigDecimal);
        payVo.setOut_trade_no(orderSn);
        //查询订单项的数据
        List<OrderItemEntity> orderItemEntities = orderItemService.list(new QueryWrapper<OrderItemEntity>().eq("order_sn", orderSn));
        OrderItemEntity orderItemEntity = orderItemEntities.get(0);
        payVo.setBody(orderItemEntity.getSkuAttrsVals());

        payVo.setSubject(orderItemEntity.getSkuName());

        return payVo;
    }

    @Override
    public PageUtils listWithItem(Map<String, Object> params) {
        MemberRespVo memberRespVo = LoginUserInterceptor.loginUser.get();

        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>()
                        .eq("member_id",memberRespVo.getId()).orderByDesc("create_time")
        );
        List<OrderEntity> orderEntity = page.getRecords().stream().map(item -> {
            String orderSn = item.getOrderSn();
            List<OrderItemEntity> order_sn = orderItemService.list(new QueryWrapper<OrderItemEntity>().eq("order_sn", orderSn));
            item.setItemEntities(order_sn);
            return item;
        }).collect(Collectors.toList());
        page.setRecords(orderEntity);

        return new PageUtils(page);
    }

    @Override
    public String handleResPay(PayAsyncVo vo) {
        //保存流水单信息
        PaymentInfoEntity paymentInfoEntity = new PaymentInfoEntity();
        paymentInfoEntity.setOrderSn(vo.getOut_trade_no());
        paymentInfoEntity.setAlipayTradeNo(vo.getTrade_no());
        paymentInfoEntity.setPaymentStatus(vo.getTrade_status());
        paymentInfoService.save(paymentInfoEntity);
        //更改订单的状态信息
        if(vo.getTrade_status().equals("TRADE_SUCCESS") || vo.getTrade_status().equals("TRADE_FINISHED")){
            //更改订单状态
            String orderSn = vo.getOut_trade_no();
            this.baseMapper.updateOrderStatus(orderSn,OrderStatusEnume.PAYED.getCode());
        }
        return null;
    }

    @Override
    public void createSeckillOrder(SeckillOrderTo orderTo) {
        //TODO 保存订单信息
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderSn(orderTo.getOrderSn());
        orderEntity.setMemberId(orderTo.getMemberId());
        orderEntity.setCreateTime(new Date());
        BigDecimal totalPrice = orderTo.getSeckillPrice().multiply(BigDecimal.valueOf(orderTo.getNum()));
        orderEntity.setPayAmount(totalPrice);
        orderEntity.setStatus(OrderStatusEnume.CREATE_NEW.getCode());

        //保存订单
        this.save(orderEntity);

        //保存订单项信息
        OrderItemEntity orderItem = new OrderItemEntity();
        orderItem.setOrderSn(orderTo.getOrderSn());
        orderItem.setRealAmount(totalPrice);

        orderItem.setSkuQuantity(orderTo.getNum());


        //保存订单项数据
        orderItemService.save(orderItem);

    }

    private void saveOrder(OrderCreateTo order) {
        OrderEntity orderEntity = order.getOrderEntity();
        orderEntity.setModifyTime(new Date());
        this.save(orderEntity);
        List<OrderItemEntity> orderItems = order.getOrderItems();
        orderItemService.saveBatch(orderItems);
    }

    //创建订单
    private OrderCreateTo createOrder(){
        OrderCreateTo orderCreateTo = new OrderCreateTo();
        String millisecond = IdWorker.getMillisecond();
        //创建订单信息
        OrderEntity orderEntity = buildeOrder(millisecond);
        //创建订单项
        List<OrderItemEntity> orderItemVos = builderItems(millisecond);
        //计算订单的价格
        computePrice(orderEntity,orderItemVos);
        orderCreateTo.setOrderEntity(orderEntity);
        orderCreateTo.setOrderItems(orderItemVos);
        return orderCreateTo;


    }

    private void computePrice(OrderEntity orderEntity, List<OrderItemEntity> orderItemEntities) {

        //总价
        BigDecimal total = new BigDecimal("0.0");
        //优惠价
        BigDecimal coupon = new BigDecimal("0.0");
        BigDecimal intergration = new BigDecimal("0.0");
        BigDecimal promotion = new BigDecimal("0.0");

        //积分、成长值
        Integer integrationTotal = 0;
        Integer growthTotal = 0;

        //订单总额，叠加每一个订单项的总额信息
        for (OrderItemEntity orderItem : orderItemEntities) {
            //优惠价格信息
            coupon = coupon.add(orderItem.getCouponAmount());
            promotion = promotion.add(orderItem.getPromotionAmount());
            intergration = intergration.add(orderItem.getIntegrationAmount());

            //总价
            total = total.add(orderItem.getRealAmount());

            //积分信息和成长值信息
            integrationTotal += orderItem.getGiftIntegration();
            growthTotal += orderItem.getGiftGrowth();

        }
        //1、订单价格相关的
        orderEntity.setTotalAmount(total);
        //设置应付总额(总额+运费)
        orderEntity.setPayAmount(total.add(orderEntity.getFreightAmount()));
        orderEntity.setCouponAmount(coupon);
        orderEntity.setPromotionAmount(promotion);
        orderEntity.setIntegrationAmount(intergration);

        //设置积分成长值信息
        orderEntity.setIntegration(integrationTotal);
        orderEntity.setGrowth(growthTotal);

        //设置删除状态(0-未删除，1-已删除)
        orderEntity.setDeleteStatus(0);

    }

    //创建订单项信息
    private List<OrderItemEntity> builderItems(String millisecond) {
        List<OrderItemVo> currentCartItems = cartFeignService.getCurrentCartItems();
        if(currentCartItems != null && currentCartItems.size()>0){
            List<OrderItemEntity> list = currentCartItems.stream().map(item -> {
                OrderItemEntity orderItemEntity = buildOrderItem(item);
                orderItemEntity.setOrderSn(millisecond);
                return orderItemEntity;
            }).collect(Collectors.toList());
            return list;
        }
        return null;

    }

    private OrderItemEntity buildOrderItem(OrderItemVo item) {
        OrderItemEntity orderItemEntity = new OrderItemEntity();
        //设置spu信息
        R spuInfoBySkuId = productFeignService.getSpuInfoBySkuId(item.getSkuId());
        SpuInfoVo spuInfoData = spuInfoBySkuId.getData(new TypeReference<SpuInfoVo>() {
        });
        orderItemEntity.setSpuId(spuInfoData.getId());
        orderItemEntity.setSpuName(spuInfoData.getSpuName());
        orderItemEntity.setSpuBrand(spuInfoData.getBrandName());
        orderItemEntity.setCategoryId(spuInfoData.getCatalogId());


        //2、商品的sku信息
        orderItemEntity.setSkuId(item.getSkuId());
        orderItemEntity.setSkuName(item.getTitle());
        orderItemEntity.setSkuPic(item.getImage());
        orderItemEntity.setSkuPrice(item.getPrice());
        orderItemEntity.setSkuQuantity(item.getCount());

        //使用StringUtils.collectionToDelimitedString将list集合转换为String
        String skuAttrValues = StringUtils.collectionToDelimitedString(item.getSkuAttrValues(), ";");
        orderItemEntity.setSkuAttrsVals(skuAttrValues);

        //3、商品的优惠信息

        //4、商品的积分信息
        orderItemEntity.setGiftGrowth(item.getPrice().multiply(new BigDecimal(item.getCount())).intValue());
        orderItemEntity.setGiftIntegration(item.getPrice().multiply(new BigDecimal(item.getCount())).intValue());
        //5、订单项的价格信息
        orderItemEntity.setPromotionAmount(BigDecimal.ZERO);
        orderItemEntity.setCouponAmount(BigDecimal.ZERO);
        orderItemEntity.setIntegrationAmount(BigDecimal.ZERO);

        //当前订单项的实际金额.总额 - 各种优惠价格
        //原来的价格
        BigDecimal origin = orderItemEntity.getSkuPrice().multiply(new BigDecimal(orderItemEntity.getSkuQuantity().toString()));
        //原价减去优惠价得到最终的价格
        BigDecimal subtract = origin.subtract(orderItemEntity.getCouponAmount())
                .subtract(orderItemEntity.getPromotionAmount())
                .subtract(orderItemEntity.getIntegrationAmount());
        orderItemEntity.setRealAmount(subtract);
        return orderItemEntity;

    }

    private OrderEntity buildeOrder(String millisecond) {
        //生成订单
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderSn(millisecond);
        //调用远程方法获取运费信息
        OrderSubmitVo orderSubmitVo = orderSubmitThreadLocal.get();
        R fare = wareFeignService.getFare(orderSubmitVo.getAddrId());
        FareVo fareVo = fare.getData(new TypeReference<FareVo>() {
        });
        //设置运费
        orderEntity.setFreightAmount(fareVo.getFare());
        //设置收获地址信息
        MemberAddressVo address = fareVo.getAddress();
        orderEntity.setReceiverName(address.getName());
        orderEntity.setReceiverPhone(address.getPhone());
        orderEntity.setReceiverPostCode(address.getPostCode());
        orderEntity.setReceiverProvince(address.getProvince());
        orderEntity.setReceiverCity(address.getCity());
        orderEntity.setReceiverRegion(address.getRegion());
        orderEntity.setReceiverDetailAddress(address.getDetailAddress());
        //设置订单相关的状态信息
        orderEntity.setStatus(OrderStatusEnume.CREATE_NEW.getCode());
        orderEntity.setAutoConfirmDay(7);
        orderEntity.setConfirmStatus(0);
        return orderEntity;
    }

}