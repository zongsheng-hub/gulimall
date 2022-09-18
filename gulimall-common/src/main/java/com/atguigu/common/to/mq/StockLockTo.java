package com.atguigu.common.to.mq;

import lombok.Data;

@Data
public class StockLockTo {
    //库存工作单id
    private Long id;
    //工作单详情所有信息
    private StockDetailTo stockDetailTo;
}
