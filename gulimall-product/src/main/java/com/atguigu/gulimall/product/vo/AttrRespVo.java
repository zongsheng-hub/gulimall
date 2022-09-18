package com.atguigu.gulimall.product.vo;

import lombok.Data;

@Data
public class AttrRespVo extends AttrVo{
    private String catelogName;
    private String groupName;
    //所属分类全路径
    private Long[] catelogPath;
}
