package com.atguigu.common.to.es;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * "properties" : {
 *         "attrs" : {
 *           "type" : "nested",
 *           "properties" : {
 *             "attrId" : {
 *               "type" : "long"
 *             },
 *             "attrName" : {
 *               "type" : "keyword",
 *               "index" : false,
 *               "doc_values" : false
 *             },
 *             "attrValue" : {
 *               "type" : "keyword"
 *             }
 *           }
 *         },
 *
 *
 *
 *       }
 */

@Data
public class SkuEsModel {
    private Long brandId;
    private String brandImg;
    private String brandName;
    private Long catalogId;
    private String catalogName;
    private Boolean hasStock;
    private Long hotScore;
    private Long saleCount;
    private Long skuId;
    private String skuImg;
    private BigDecimal skuPrice;
    private String skuTitle;
    private Long spuId;
    private List<Attrs> attrs;

    @Data
    public static class Attrs{
        private Long attrId;
        private String attrName;
        private String attrValue;
    }
    /**
     *   "skuImg" : {
     *  *           "type" : "keyword",
     *  *           "index" : false,
     *  *           "doc_values" : false
     *  *         },
     *  *         "skuPrice" : {
     *  *           "type" : "keyword"
     *  *         },
     *  *         "skuTitle" : {
     *  *           "type" : "text",
     *  *           "analyzer" : "ik_smart"
     *  *         },
     *  *         "spuId" : {
     *  *           "type" : "keyword"
     *  *         }
     */
}
