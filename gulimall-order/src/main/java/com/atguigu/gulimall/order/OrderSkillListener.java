package com.atguigu.gulimall.order;

import com.atguigu.common.to.mq.SeckillOrderTo;
import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.service.OrderService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@RabbitListener(queues = "order.seckill.order.queue")
@Service
public class OrderSkillListener {
    @Autowired
    private OrderService orderService;
    @RabbitHandler
    public void listener(SeckillOrderTo to, Channel channel, Message message) throws IOException {
        System.out.println("收到过期的订单信息，准备关闭订单"+to.getOrderSn());
        try {
            orderService.createSeckillOrder(to);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        } catch (Exception e) {
            //退回消息
            channel.basicReject(message.getMessageProperties().getDeliveryTag(),true);
        }


    }
}
