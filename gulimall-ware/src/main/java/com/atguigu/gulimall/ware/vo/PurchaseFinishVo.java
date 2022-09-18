package com.atguigu.gulimall.ware.vo;

import lombok.Data;

import java.util.List;
@Data
public class PurchaseFinishVo {
    private Long id;
    private List<PurChaseItemFinishVo> items;
}
