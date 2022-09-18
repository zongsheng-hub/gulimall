package com.atguigu.gulimall.ware.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.exception.NoStockException;
import com.atguigu.common.to.SkuHasStockTo;
import com.atguigu.common.to.mq.OrderTo;
import com.atguigu.common.to.mq.StockDetailTo;
import com.atguigu.common.to.mq.StockLockTo;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.ware.entity.WareOrderTaskDetailEntity;
import com.atguigu.gulimall.ware.entity.WareOrderTaskEntity;
import com.atguigu.gulimall.ware.feign.OrderFeignService;
import com.atguigu.gulimall.ware.feign.ProductFeignService;
import com.atguigu.gulimall.ware.service.WareOrderTaskDetailService;
import com.atguigu.gulimall.ware.service.WareOrderTaskService;
import com.atguigu.gulimall.ware.vo.OrderItemVo;
import com.atguigu.gulimall.ware.vo.OrderVo;
import com.atguigu.gulimall.ware.vo.WareSkuLockVo;
import com.rabbitmq.client.Channel;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.ware.dao.WareSkuDao;
import com.atguigu.gulimall.ware.entity.WareSkuEntity;
import com.atguigu.gulimall.ware.service.WareSkuService;
import org.springframework.transaction.annotation.Transactional;

@RabbitListener(queues = "stock.release.stock.queue")
@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuDao, WareSkuEntity> implements WareSkuService {
    @Autowired
    private WareSkuDao wareSkuDao;
    @Autowired
    private ProductFeignService productFeignService;
    @Autowired
    private WareOrderTaskService wareOrderTaskService;
    @Autowired
    private WareOrderTaskDetailService wareOrderTaskDetailService;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private OrderFeignService orderFeignService;


    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<WareSkuEntity> wareSkuEntityQueryWrapper = new QueryWrapper<>();
        String skuId = (String) params.get("skuId");
        if(!StringUtils.isEmpty(skuId)){
            wareSkuEntityQueryWrapper.eq("sku_id",skuId);
        }
        String wareId = (String) params.get("wareId");
        if(!StringUtils.isEmpty(wareId)){
            wareSkuEntityQueryWrapper.eq("ware_id",wareId);
        }

        IPage<WareSkuEntity> page = this.page(
                new Query<WareSkuEntity>().getPage(params),
                wareSkuEntityQueryWrapper

        );

        return new PageUtils(page);
    }
    @Transactional
    @Override
    public void addStore(Long skuId, Long wareId, Integer skuNum) {
        List<WareSkuEntity> sku_id = wareSkuDao.selectList(new QueryWrapper<WareSkuEntity>().eq("sku_id", skuId));
        if(sku_id == null || sku_id.size() == 0){
            WareSkuEntity wareSkuEntity = new WareSkuEntity();
            wareSkuEntity.setSkuId(skuId);
            wareSkuEntity.setWareId(wareId);
            wareSkuEntity.setStock(skuNum);
            wareSkuEntity.setStockLocked(0);
            //调用远程接口获取sku名称 如果调用失败则不回滚
            try {
                R info = productFeignService.info(skuId);
                Map<String, Object> data = (Map<String, Object>) info.get("skuInfo");
                if(info.getCode() == 0){
                    wareSkuEntity.setSkuName((String) data.get("skuName"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            wareSkuDao.insert(wareSkuEntity);
        }else {
            this.baseMapper.addStore(skuId,wareId,skuNum);
        }



    }

    @Override
    public List<SkuHasStockTo> getHasStock(List<Long> skuIds) {
        List<SkuHasStockTo> skuHasStockToList = skuIds.stream().map(skuId -> {
            Long count = baseMapper.getHasStock(skuId);
            SkuHasStockTo skuHasStockTo = new SkuHasStockTo();
            skuHasStockTo.setSkuId(skuId);
            skuHasStockTo.setHasStock(count == null?false:count>0);
            return skuHasStockTo;
        }).collect(Collectors.toList());
        return skuHasStockToList;
    }
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean orderLockWare(WareSkuLockVo vo) {
        //保存工作单信息
        WareOrderTaskEntity wareOrderTaskEntity = new WareOrderTaskEntity();
        wareOrderTaskEntity.setOrderSn(vo.getOrderSn());
        wareOrderTaskService.save(wareOrderTaskEntity);

        //找到每个商品在哪个仓库有库存
        List<OrderItemVo> locks = vo.getLocks();
        List<skuWareHasStock> skuWareHasStocks = locks.stream().map(item -> {
            skuWareHasStock skuWareHasStock = new skuWareHasStock();
            skuWareHasStock.setSkuId(item.getSkuId());
            skuWareHasStock.setNum(item.getCount());
            //查询这个商品在哪里有库存
            List<Long> wareIds = wareSkuDao.listWareIdHasSkuStock(item.getSkuId());
            skuWareHasStock.setWareId(wareIds);
            return skuWareHasStock;

        }).collect(Collectors.toList());
        //锁定库存
        for(skuWareHasStock hasStock : skuWareHasStocks){
            boolean skuStocked=false;
            Long skuId = hasStock.getSkuId();
            List<Long> wareIds = hasStock.getWareId();
            if(wareIds == null || wareIds.size() == 0){
                throw new NoStockException(skuId);
            }

            //1、如果每一个商品都锁定成功,将当前商品锁定了几件的工作单记录发给MQ
            //2、锁定失败。前面保存的工作单信息都回滚了。发送出去的消息，即使要解锁库存，由于在数据库查不到指定的id，所有就不用解锁
            for (Long warId : wareIds){
                //锁定成功就返回1，失败就返回0
                Long count = wareSkuDao.lockSkuStock(skuId,warId,hasStock.getNum());
                if(count == 1){
                    //锁定库存成功
                    skuStocked = true;
                    //保存工作单详情信息
                    WareOrderTaskDetailEntity wareOrderTaskDetailEntity = new WareOrderTaskDetailEntity();
                    wareOrderTaskDetailEntity.setWareId(warId);
                    wareOrderTaskDetailEntity.setSkuId(skuId);
                    wareOrderTaskDetailEntity.setTaskId(wareOrderTaskEntity.getId());
                    wareOrderTaskDetailEntity.setSkuNum(hasStock.getNum());
                    wareOrderTaskDetailEntity.setLockStatus(1);
                    wareOrderTaskDetailService.save(wareOrderTaskDetailEntity);
                    //给mq发送消息
                    StockLockTo stockLockTo = new StockLockTo();
                    stockLockTo.setId(wareOrderTaskEntity.getId());
                    StockDetailTo stockDetailTo = new StockDetailTo();
                    BeanUtils.copyProperties(wareOrderTaskDetailEntity,stockDetailTo);
                    stockLockTo.setStockDetailTo(stockDetailTo);
                    rabbitTemplate.convertAndSend("stock-event-exchange","stock.locked",stockDetailTo);
                    break;
                }else{
                    //当前仓库锁失败，重试下一个仓库
                }
            }

            if(!skuStocked){
                //当前商品所有仓库都没有锁住
                throw new NoStockException(skuId);
            }


        }
        return false;
    }

    /**
     * 解锁
     * 1、查询数据库关于这个订单锁定库存信息
     *   有：证明库存锁定成功了
     *      解锁：订单状况
     *          1、没有这个订单，必须解锁库存
     *          2、有这个订单，不一定解锁库存
     *              订单状态：已取消：解锁库存
     *                      已支付：不能解锁库存
     */
    @RabbitHandler
    public void unlockStock(StockLockTo to, Message message, Channel channel) throws IOException {
        StockDetailTo stockDetailTo = to.getStockDetailTo();
        Long id = stockDetailTo.getId();
        WareOrderTaskDetailEntity taskDetailInfo = wareOrderTaskDetailService.getById(id);
        if(taskDetailInfo != null){
            //解锁
            Long id1 = to.getId();
            WareOrderTaskEntity wareOrderTaskEntity = wareOrderTaskService.getById(id1);
            String orderSn = wareOrderTaskEntity.getOrderSn();//获取订单号码
            R r = orderFeignService.getOrderByOrderSn(orderSn);
            if(r.getCode() == 0){
                OrderVo data = r.getData(new TypeReference<OrderVo>() {
                });
                if(data == null ||data.getStatus() == 4){
                    //订单被取消了才能解锁库存
                    releaseLockStock(stockDetailTo.getSkuId(),stockDetailTo.getWareId(),stockDetailTo.getSkuNum(),id);
                    //手动确认收到消息
                    channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
                }
            }else{
                //远程调用查询失败，拒收消息
                channel.basicReject(message.getMessageProperties().getDeliveryTag(),true);
            }
        }else{
            //无需解锁
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }

    }

    private void releaseLockStock(Long skuId,Long wareId,Integer num,Long taskDetailId){
        WareOrderTaskDetailEntity byId = wareOrderTaskDetailService.getById(taskDetailId);
        if(byId.getLockStatus()==1){
            //当是锁定状态时才解锁库存
            wareSkuDao.unlockStock(skuId,wareId,num);
        }


        //更改工作单详情状态
        WareOrderTaskDetailEntity wareOrderTaskDetailEntity = new WareOrderTaskDetailEntity();
        wareOrderTaskDetailEntity.setId(taskDetailId);
        wareOrderTaskDetailEntity.setLockStatus(2);
        wareOrderTaskDetailService.updateById(wareOrderTaskDetailEntity);
    }
    /**
     * 关闭订单后立刻解锁库存
     */
    @RabbitHandler
    public void unlockStock(OrderTo to, Message message, Channel channel) throws IOException{
        String orderSn = to.getOrderSn();
        //查询库存工作单信息
        WareOrderTaskEntity wareOrderTaskEntity = wareOrderTaskService.getWareOrderTaskByOrderSn(orderSn);
        //按照工作单的id找到所有 没有解锁的库存，进行解锁
        Long id = wareOrderTaskEntity.getId();
        List<WareOrderTaskDetailEntity> list = wareOrderTaskDetailService.list(new QueryWrapper<WareOrderTaskDetailEntity>().eq("task_id", id).eq("status", 1));
        for (WareOrderTaskDetailEntity taskDetailEntity : list) {
            releaseLockStock(taskDetailEntity.getSkuId(),taskDetailEntity.getWareId(),taskDetailEntity.getSkuNum(),taskDetailEntity.getId());
        }


    }


    @Data
   class skuWareHasStock{
        private Long skuId;
        private Integer num;
        private List<Long> wareId;

    }

}