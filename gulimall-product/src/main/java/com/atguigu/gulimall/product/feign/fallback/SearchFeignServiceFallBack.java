package com.atguigu.gulimall.product.feign.fallback;

import com.atguigu.common.exception.BizCodeEnume;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.product.feign.SearchFeignService;
import org.springframework.stereotype.Component;

import java.util.List;
@Component
public class SearchFeignServiceFallBack implements SearchFeignService {

    @Override
    public R productStatusUp(List<SkuEsModel> skuEsModels) {
        return R.error(BizCodeEnume.TOO_MANANY_REQUEST.getCode(),BizCodeEnume.TOO_MANANY_REQUEST.getMsg());
    }
}
