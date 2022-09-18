package com.atguigu.gulimall.order.web;

import com.atguigu.gulimall.order.service.OrderService;
import com.atguigu.gulimall.order.vo.OrderConfirmVo;
import com.atguigu.gulimall.order.vo.OrderSubmitVo;
import com.atguigu.gulimall.order.vo.SubmitOrderRespVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OrderWebController {
    @Autowired
    private OrderService orderService;
    @GetMapping("/toTrade")
    public String toTrade(Model model){
        OrderConfirmVo orderConfirmVo = orderService.confirm();
        model.addAttribute("orderConfirmData",orderConfirmVo);

        return "confirm";
    }
    @PostMapping("/submitOrder")
    public String submitOrder(OrderSubmitVo vo, Model model, RedirectAttributes attributes){
        SubmitOrderRespVo submitOrderRespVo = orderService.submitOrder(vo);
        if(submitOrderRespVo.getCode() == 0){
            model.addAttribute("submitOrderResp",submitOrderRespVo);
            //下单成功
            return "pay";
        }else{
            String msg = "下单失败";
            switch (submitOrderRespVo.getCode()) {
                case 1: msg += "令牌订单信息过期，请刷新再次提交"; break;
                case 2: msg += "订单商品价格发生变化，请确认后再次提交"; break;
                case 3: msg += "库存锁定失败，商品库存不足"; break;
            }
            attributes.addFlashAttribute("msg",msg);
            //下单失败
            return "redirect:http://order.gulimall.com/toTrade";
        }



    }
}
