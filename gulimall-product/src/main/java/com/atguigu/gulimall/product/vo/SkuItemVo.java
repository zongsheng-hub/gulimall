package com.atguigu.gulimall.product.vo;

import com.atguigu.gulimall.product.entity.SkuImagesEntity;
import com.atguigu.gulimall.product.entity.SkuInfoEntity;
import com.atguigu.gulimall.product.entity.SpuInfoDescEntity;
import lombok.Data;

import java.util.List;
@Data
public class SkuItemVo {
    //sku基本信息获取
    SkuInfoEntity info;
    //sku的图片信息
    List<SkuImagesEntity> images;
    //spu的销售属性组合
    List<SkuItemSaleAttrVo> saleAttr;
    //是否有货
    boolean hasStock = true;

    //spu的介绍
    SpuInfoDescEntity desp;

    //获取spu的规格参数信息
    List<SpuItemAttrGroupVo> groupAttrs;

    SeckillSkuVo seckillSkuVo;

    @Data
    public static class SkuItemSaleAttrVo{
        private Long attrId;
        private String attrName;
        private List<AttrValueWithSkuId> attrValues;
    }
    @Data
    public static class SpuItemAttrGroupVo{
        private String groupName;
        private List<SpuBaseAttrVo> attrs;


    }
    @Data
    public static class SpuBaseAttrVo{
        private String attrName;
        private String attrValue;

    }
    @Data
    public static class AttrValueWithSkuId{
        private String attrValue;
        private String skuIds;
    }
}
