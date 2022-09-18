package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.ProductConstant;
import com.atguigu.common.to.SkuHasStockTo;
import com.atguigu.common.to.SkuReductionTo;
import com.atguigu.common.to.SpuBoundTo;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.product.entity.*;
import com.atguigu.gulimall.product.feign.CouponFeginService;
import com.atguigu.gulimall.product.feign.SearchFeignService;
import com.atguigu.gulimall.product.feign.WareFeignService;
import com.atguigu.gulimall.product.service.*;
import com.atguigu.gulimall.product.vo.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.SpuInfoDao;
import org.springframework.transaction.annotation.Transactional;


@Service("spuInfoService")
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {
    @Autowired
    private SpuImagesService spuImagesService;
    @Autowired
    private SpuInfoDescService spuInfoDescService;
    @Autowired
    private AttrService attrService;
    @Autowired
    private ProductAttrValueService productAttrValueService;
    @Autowired
    private SkuInfoService skuInfoService;
    @Autowired
    private SkuImagesService skuImagesService;
    @Autowired
    private SkuSaleAttrValueService skuSaleAttrValueService;
    @Autowired
    private CouponFeginService couponFeginService;
    @Autowired
    private BrandService brandService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private WareFeignService wareFeignService;
    @Autowired
    private SearchFeignService searchFeignService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                new QueryWrapper<SpuInfoEntity>()
        );

        return new PageUtils(page);
    }
    @Transactional
    @Override
    public void saveSpuInfo(SpuSaveVo spuInfo) {
        //1、保存spu基本信息   `pms_spu_info`
        SpuInfoEntity spuInfoEntity = new SpuInfoEntity();
        BeanUtils.copyProperties(spuInfo,spuInfoEntity);
        spuInfoEntity.setCreateTime(new Date());
        spuInfoEntity.setUpdateTime(new Date());
        this.baseMapper.insert(spuInfoEntity);
        //2、保存spu图片集   `pms_spu_images`
        List<String> images = spuInfo.getImages();
        spuImagesService.saveSpuImage(spuInfoEntity.getId(),images);

        //3、保存spu描述图片  `pms_spu_info_desc`
        List<String> decript = spuInfo.getDecript();
        SpuInfoDescEntity spuInfoDescEntity = new SpuInfoDescEntity();
        spuInfoDescEntity.setSpuId(spuInfoEntity.getId());
        spuInfoDescEntity.setDecript(String.join(",",decript));
        spuInfoDescService.saSpuInfoDesc(spuInfoDescEntity);

        //4、保存spu规格参数  `pms_product_attr_value`
        List<BaseAttrs> baseAttrs = spuInfo.getBaseAttrs();
        List<ProductAttrValueEntity> baseAttrsList = baseAttrs.stream().map(baseAttr -> {
            ProductAttrValueEntity productAttrValueEntity = new ProductAttrValueEntity();
            productAttrValueEntity.setAttrId(baseAttr.getAttrId());
            AttrEntity byId = attrService.getById(baseAttr.getAttrId());
            productAttrValueEntity.setAttrName(byId.getAttrName());
            productAttrValueEntity.setAttrValue(baseAttr.getAttrValues());
            productAttrValueEntity.setQuickShow(baseAttr.getShowDesc());
            productAttrValueEntity.setSpuId(spuInfoEntity.getId());
            return productAttrValueEntity;
        }).collect(Collectors.toList());
        productAttrValueService.saveProductAttr(baseAttrsList);


        //5、保存spu积分信息 gulimall_sms>`sms_spu_bounds`
        Bounds bounds = spuInfo.getBounds();
        SpuBoundTo spuBoundTo = new SpuBoundTo();
        spuBoundTo.setSpuId(spuInfoEntity.getId());
        BeanUtils.copyProperties(bounds,spuBoundTo);
        R r = couponFeginService.save(spuBoundTo);
        if(r.getCode() != 0){
            log.error("远程保存spu积分信息失败");

        }


        //5、保存sku信息
        List<Skus> skus = spuInfo.getSkus();
        skus.forEach(item->{
            String defaultImage="";
            for(Images images1 : item.getImages()){
                if(images1.getDefaultImg() == 1){
                    defaultImage = images1.getImgUrl();
                }
            }
            SkuInfoEntity skuInfoEntity = new SkuInfoEntity();
            BeanUtils.copyProperties(item,skuInfoEntity);
            skuInfoEntity.setBrandId(spuInfoEntity.getBrandId());
            skuInfoEntity.setCatalogId(spuInfoEntity.getCatalogId());
            skuInfoEntity.setSaleCount(0L);
            skuInfoEntity.setSpuId(spuInfoEntity.getId());
            skuInfoEntity.setSkuDefaultImg(defaultImage);
            //5.1 保存sku基本信息 `pms_sku_info`
            skuInfoService.saveSkuInfo(skuInfoEntity);
            Long skuId = skuInfoEntity.getSkuId();
            List<SkuImagesEntity> skuImagesEntities = item.getImages().stream().map(item1 -> {
                SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                skuImagesEntity.setSkuId(skuId);
                skuImagesEntity.setImgUrl(item1.getImgUrl());
                skuImagesEntity.setDefaultImg(item1.getDefaultImg());
                return skuImagesEntity;
            }).filter(entity->{
                return !StringUtils.isEmpty(entity.getImgUrl());
            }).collect(Collectors.toList());
            //5.2 保存sku图片集 `pms_sku_images`
            skuImagesService.saveBatch(skuImagesEntities);
            //5.3 保存sku的销售属性信息 `pms_sku_sale_attr_value`
            List<Attr> attrs = item.getAttr();
            List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = attrs.stream().map(attr -> {
                SkuSaleAttrValueEntity skuSaleAttrValueEntity = new SkuSaleAttrValueEntity();
                BeanUtils.copyProperties(attr, skuSaleAttrValueEntity);
                skuSaleAttrValueEntity.setSkuId(skuId);
                return skuSaleAttrValueEntity;
            }).collect(Collectors.toList());
            skuSaleAttrValueService.saveBatch(skuSaleAttrValueEntities);
            //保存sku优惠满减信息
            SkuReductionTo skuReductionTo = new SkuReductionTo();
            BeanUtils.copyProperties(item,skuReductionTo);
            skuReductionTo.setSkuId(skuId);
            if(skuReductionTo.getFullCount()>0 || skuReductionTo.getFullPrice().compareTo(new BigDecimal("0"))==1){
                R r1 = couponFeginService.saveSkuReduction(skuReductionTo);
                if(r1.getCode() != 0){
                    log.error("远程保存sku信息失败");
                }
            }



        });


        //5.4 保存sku优惠满减信息 gulimall_sms>`sms_sku_ladder`  `sms_sku_full_reduction` `sms_member_price`
    }

    @Override
    public PageUtils queryPageByCondition(Map<String, Object> params) {
        QueryWrapper<SpuInfoEntity> queryWrapper = new QueryWrapper<>();
        String key = (String) params.get("key");
        String catelogId = (String) params.get("catelogId");
        String brandId = (String) params.get("brandId");
        String status = (String) params.get("status");
        if(StringUtils.isNotEmpty(key)){
            queryWrapper.and(wrapper->{
                wrapper.eq("id",key).or().like("spu_name",key);
            });
        }
        if(StringUtils.isNotEmpty(catelogId)&& !"0".equalsIgnoreCase(catelogId)){
            queryWrapper.eq("catalog_id",catelogId);
        }
        if(StringUtils.isNotEmpty(brandId)&& !"0".equalsIgnoreCase(brandId)){
            queryWrapper.eq("brand_id",brandId);
        }
        if(StringUtils.isNotEmpty(status)&& !"0".equalsIgnoreCase(status)){
            queryWrapper.eq("publish_status",status);
        }


        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                queryWrapper
        );

        return new PageUtils(page);
    }

    @Override
    public void up(Long spuId) {
        //根据spuId查询sku信息
        List<SkuInfoEntity> skus = skuInfoService.getSkusBySpuId(spuId);
        //所有skuId的集合
        List<Long> longList = skus.stream().map(sku -> {
            return sku.getSkuId();
        }).collect(Collectors.toList());

        //TODO 查询检索规格属性
        List<ProductAttrValueEntity> attrValueEntities = productAttrValueService.baseAttrListForSpu(spuId);
        List<Long> attrIds = attrValueEntities.stream().map(attr -> {
            return attr.getAttrId();
        }).collect(Collectors.toList());
        List<Long> ids = attrService.getSearchAttrIds(attrIds);
        HashSet<Long> hashSet = new HashSet<>(ids);
        List<SkuEsModel.Attrs> attrsList = attrValueEntities.stream().filter(item -> {
            return hashSet.contains(item.getAttrId());
        }).map(item -> {
            SkuEsModel.Attrs attrs = new SkuEsModel.Attrs();
            BeanUtils.copyProperties(item, attrs);
            return attrs;
        }).collect(Collectors.toList());
        //TODO 发送远程调用查询是否有库存
        Map<Long, Boolean> hasStock = null;
        try {
            R skuHasStock = wareFeignService.getSkuHasStock(longList);
            TypeReference<List<SkuHasStockTo>> typeReference = new TypeReference<List<SkuHasStockTo>>() {
            };
             hasStock = skuHasStock.getData(typeReference).stream().collect(Collectors.toMap(SkuHasStockTo::getSkuId, item -> item.getHasStock()));
        } catch (Exception e) {
           log.error("远程调用查询库存接口失败，错误信息{}",e);
        }


        Map<Long, Boolean> finalHasStock = hasStock;
        List<SkuEsModel> collect = skus.stream().map(sku -> {
            SkuEsModel skuEsModel = new SkuEsModel();
            //将信息封装进去skuEsModel
            BeanUtils.copyProperties(sku,skuEsModel);
            skuEsModel.setSkuPrice(sku.getPrice());
            skuEsModel.setSkuImg(sku.getSkuDefaultImg());
            //TODO 发送远程调用查询是否有库存
            if(finalHasStock == null){
                skuEsModel.setHasStock(true);
            }else{
                skuEsModel.setHasStock(finalHasStock.get(sku.getSkuId()));
            }

            //TODO 热度评分默认为0
            skuEsModel.setHotScore(0L);

            //TODO 查询品牌和分类的名字
            BrandEntity brandEntity = brandService.getById(skuEsModel.getBrandId());
            skuEsModel.setBrandName(brandEntity.getName());
            skuEsModel.setBrandImg(brandEntity.getLogo());
            CategoryEntity categoryEntity = categoryService.getById(skuEsModel.getCatalogId());
            skuEsModel.setCatalogName(categoryEntity.getName());
            //设置检索属性值
            skuEsModel.setAttrs(attrsList);


            return skuEsModel;
        }).collect(Collectors.toList());

        //TODO 将数据发送给ES进行保存 gulimall-search
        R r = searchFeignService.productStatusUp(collect);
        if(r.getCode() == 0){
            //远程调用成功，更改状态
            baseMapper.updateSpuStatus(spuId, ProductConstant.StatusEnum.PRODUCT_ON.getCode());
        }else{
            //远程调用失败，幂等性
        }
    }

    @Override
    public SpuInfoEntity getSpuInfoBySkuId(Long id) {
        //获取spuId
        SkuInfoEntity byId = skuInfoService.getById(id);
        Long spuId = byId.getSpuId();
        SpuInfoEntity spuInfoEntity = baseMapper.selectById(spuId);
        return spuInfoEntity;
    }

}