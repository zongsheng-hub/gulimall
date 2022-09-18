package com.atguigu.gulimall.product.service;

import com.atguigu.gulimall.product.vo.AttrGroupRelationVo;
import com.atguigu.gulimall.product.vo.AttrRespVo;
import com.atguigu.gulimall.product.vo.AttrVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.gulimall.product.entity.AttrEntity;

import java.util.List;
import java.util.Map;

/**
 * 商品属性
 *
 * @author fuzs
 * @email fuzs@gmail.com
 * @date 2022-04-27 12:20:44
 */
public interface AttrService extends IService<AttrEntity> {

    PageUtils queryPage(Map<String, Object> params);


    void saveAttr(AttrVo attr);


    PageUtils queryBaseAttrPage(Long catelogId, Map<String, Object> params, String type);

    AttrRespVo getAttrInfo(Long attrId);

    void updateAttr(AttrVo attr);

    List<AttrEntity> getAttrRealation(String attrgroupId);

    List<AttrEntity> getAttrRealationByLong(Long attrgroupId);

    void deleteRealation(AttrGroupRelationVo[] attrGroupRelationVo);

    PageUtils getNoRelation(Map<String, Object> params, String attrgroupId);

    List<Long> getSearchAttrIds(List<Long> attrIds);
}

