package com.atguigu.gulimall.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.fastjson.JSON;
import com.atguigu.common.exception.BizCodeEnume;
import com.atguigu.common.utils.R;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class SentinelGatewayConfig {
   public SentinelGatewayConfig(){
       GatewayCallbackManager.setBlockHandler(new BlockRequestHandler() {
           @Override
           public Mono<ServerResponse> handleRequest(ServerWebExchange serverWebExchange, Throwable throwable) {
               R error = R.error(BizCodeEnume.TOO_MANANY_REQUEST.getCode(), BizCodeEnume.TOO_MANANY_REQUEST.getMsg());
               String s = JSON.toJSONString(error);

               return ServerResponse.ok().body(Mono.just(s),String.class);
           }
       });
   }
}
