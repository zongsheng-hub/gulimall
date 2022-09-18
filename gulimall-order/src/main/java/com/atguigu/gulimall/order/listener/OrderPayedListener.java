package com.atguigu.gulimall.order.listener;

import com.atguigu.gulimall.order.service.OrderService;
import com.atguigu.gulimall.order.vo.PayAsyncVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
public class OrderPayedListener {
    @Autowired
    private OrderService orderService;
    @PostMapping(value = "/payed/notify")
    public String handleAlipayed(PayAsyncVo vo,HttpServletRequest request){
        String result = orderService.handleResPay(vo);

        return result;
    }
}
