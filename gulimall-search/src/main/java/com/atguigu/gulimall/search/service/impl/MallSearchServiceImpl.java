package com.atguigu.gulimall.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.search.config.GulimallElasticSearchConfig;
import com.atguigu.gulimall.search.constant.EsContant;
import com.atguigu.gulimall.search.feign.ProductFeignService;
import com.atguigu.gulimall.search.service.MallSearchService;
import com.atguigu.gulimall.search.vo.AttrResponseVo;
import com.atguigu.gulimall.search.vo.SearchParam;
import com.atguigu.gulimall.search.vo.SearchResult;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.hibernate.validator.constraints.URL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MallSearchServiceImpl implements MallSearchService {
    @Autowired
    private RestHighLevelClient esRestClient;
    @Autowired
    private ProductFeignService productFeignService;
    @Override
    public SearchResult search(SearchParam searchParam) {
        SearchResult searchResult = null;
        //??????????????????
        SearchRequest searchRequest = bulidSearchRequest(searchParam);
        //??????????????????
        try {
            SearchResponse search = esRestClient.search(searchRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);

            //????????????????????????
            searchResult = buildSearchResult(search,searchParam);
        } catch (IOException e) {
            e.printStackTrace();
        }


        return searchResult;
    }


    private SearchRequest bulidSearchRequest(SearchParam searchParam) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        /**
         * ?????????????????????????????????????????????????????????????????????????????????
         */
        //1. ??????bool-query
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        //1.1 bool-must
        if (!StringUtils.isEmpty(searchParam.getKeyword())) {
            boolQueryBuilder.must(QueryBuilders.matchQuery("skuTitle",searchParam.getKeyword()));
        }
        //1.2 bool-fiter
        //1.2.1 catelogId
        if(searchParam.getCatalog3Id() != null){
            boolQueryBuilder.filter(QueryBuilders.termQuery("catalogId",searchParam.getCatalog3Id()));
        }
        //1.2.2 brandId
        if(null != searchParam.getBrandId() && searchParam.getBrandId().size() >0){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId",searchParam.getBrandId()));
        }
        //1.2.3 attrs
        if(searchParam.getAttrs()!=null && searchParam.getAttrs().size()>0){
            searchParam.getAttrs().forEach(item->{
                BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                //attrs=1_5???:8???
                String[] s = item.split("_");
                String attrId = s[0];
                String[] attrvalue = s[1].split(":");
                boolQuery.must(QueryBuilders.termQuery("attrs.attrId",attrId));
                boolQuery.must(QueryBuilders.termsQuery("attrs.attrValue",attrvalue));

                NestedQueryBuilder attrs = QueryBuilders.nestedQuery("attrs", boolQuery, ScoreMode.None);
                boolQueryBuilder.filter(attrs);
            });
        }

        //1.3hasStock
        if(null!=searchParam.getHasStock()){
            boolQueryBuilder.filter(QueryBuilders.termQuery("hasStock",searchParam.getHasStock()==1));
        }
        //1.4skuPrice
        if(!StringUtils.isEmpty(searchParam.getSkuPrice())){
            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("skuPrice");
            String skuPrice = searchParam.getSkuPrice();
            String[] price = skuPrice.split("_");
            if(price.length==2){
                rangeQueryBuilder.gte(price[0]).lte(price[1]);
            }else if (price.length==1){
                if(skuPrice.startsWith("_")){
                    rangeQueryBuilder.lte(price[1]);
                }else if (skuPrice.endsWith("_")){
                    rangeQueryBuilder.gte(price[0]);
                }

            }
            boolQueryBuilder.filter(rangeQueryBuilder);

        }



        searchSourceBuilder.query(boolQueryBuilder);
        /**
         * ????????????????????????
         */

        //2.1??????
        if (!StringUtils.isEmpty(searchParam.getSort())) {
            String sort = searchParam.getSort();
            String[] s = sort.split("_");
            SortOrder sortOrder = s[1].equalsIgnoreCase("asc")?SortOrder.ASC:SortOrder.DESC;
            searchSourceBuilder.sort("skuPrice",sortOrder);

        }
        //2.2??????
        searchSourceBuilder.from((searchParam.getPageNum()-1)*EsContant.PRODUCT_PAGESIZE);
        searchSourceBuilder.size(EsContant.PRODUCT_PAGESIZE);
        //2.3??????
        if (!StringUtils.isEmpty(searchParam.getKeyword())) {
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            highlightBuilder.field("skuTitle");
            highlightBuilder.preTags("<b style='color:red'>");
            highlightBuilder.postTags("</b>");
            searchSourceBuilder.highlighter(highlightBuilder);
        }
        //??????
        //3.1????????????
        TermsAggregationBuilder brand_agg = AggregationBuilders.terms("brand_agg");
         brand_agg.field("brandId").size(100);
         brand_agg.subAggregation(AggregationBuilders.terms("brand_name_agg").field("brandName").size(10));
         brand_agg.subAggregation(AggregationBuilders.terms("brand_img_agg").field("brandImg").size(10));
        searchSourceBuilder.aggregation(brand_agg);
         //3.2????????????
        TermsAggregationBuilder catalog_agg = AggregationBuilders.terms("catalog_agg").field("catalogId").size(10);
        catalog_agg.subAggregation(AggregationBuilders.terms("catalog_name_agg").field("catalogName").size(10));
        searchSourceBuilder.aggregation(catalog_agg);
        //2. ??????????????????????????????
        NestedAggregationBuilder attr_agg = AggregationBuilders.nested("attr_agg", "attrs");
        //2.1 ????????????ID????????????
        TermsAggregationBuilder attr_id_agg = AggregationBuilders.terms("attr_id_agg").field("attrs.attrId");
        attr_agg.subAggregation(attr_id_agg);
        //2.1.1 ???????????????ID?????????????????????????????????
        attr_id_agg.subAggregation(AggregationBuilders.terms("attr_name_agg").field("attrs.attrName").size(1));
        //2.1.1 ???????????????ID?????????????????????????????????
        attr_id_agg.subAggregation(AggregationBuilders.terms("attr_value_agg").field("attrs.attrValue").size(50));
        searchSourceBuilder.aggregation(attr_agg);


        String s = searchSourceBuilder.toString();
        System.out.println("?????????DSL??????"+s);

        SearchRequest searchRequest = new SearchRequest(new String[]{EsContant.PRODUCT_INDEX}, searchSourceBuilder);
        return searchRequest;


    }

    private SearchResult buildSearchResult(SearchResponse response,SearchParam searchParam) {

        SearchResult result = new SearchResult();
        //???????????????????????????
        SearchHits hits = response.getHits();
        List<SkuEsModel> esModels = new ArrayList<>();
        //??????????????????
        if(hits.getHits() != null && hits.getHits().length>0){
            for (SearchHit hit : hits.getHits()) {
                String sourceAsString = hit.getSourceAsString();
                SkuEsModel skuEsModel = JSON.parseObject(sourceAsString, SkuEsModel.class);
                //??????
                if(!StringUtils.isEmpty(searchParam.getKeyword())){
                    HighlightField skuTitle = hit.getHighlightFields().get("skuTitle");
                    String string = skuTitle.getFragments()[0].string();
                    skuEsModel.setSkuTitle(string);
                }
                esModels.add(skuEsModel);
            }
        }
        result.setProduct(esModels);
        //?????????????????????????????????
        List<SearchResult.AttrVo> attrVos = new ArrayList<>();
        ParsedNested attrsAgg = response.getAggregations().get("attr_agg");
        ParsedLongTerms attrIdAgg = attrsAgg.getAggregations().get("attr_id_agg");
        for (Terms.Bucket bucket : attrIdAgg.getBuckets()) {
            SearchResult.AttrVo attrVo = new SearchResult.AttrVo();
            //???????????????id
            long attrId = bucket.getKeyAsNumber().longValue();
            attrVo.setAttrId(attrId);
            //?????????????????????
            ParsedStringTerms attrNameAgg = bucket.getAggregations().get("attr_name_agg");
            String attrName = attrNameAgg.getBuckets().get(0).getKeyAsString();
            attrVo.setAttrName(attrName);
            //????????????????????????
            ParsedStringTerms attrValueAgg = bucket.getAggregations().get("attr_value_agg");
            List<String> attrValues = attrValueAgg.getBuckets().stream().map(item -> {
                return item.getKeyAsString();
            }).collect(Collectors.toList());
            attrVo.setAttrValue(attrValues);
            attrVos.add(attrVo);


        }

        result.setAttrs(attrVos);
        //???????????????????????????
        List<SearchResult.BrandVo> brandVos = new ArrayList<>();
        ParsedLongTerms brandAgg = response.getAggregations().get("brand_agg");
        for (Terms.Bucket bucket : brandAgg.getBuckets()) {
            SearchResult.BrandVo brandVo = new SearchResult.BrandVo();
            //???????????????id
            long brandId = bucket.getKeyAsNumber().longValue();
            brandVo.setBrandId(brandId);
            //?????????????????????
            ParsedStringTerms brandNameAgg = bucket.getAggregations().get("brand_name_agg");
            String brandName = brandNameAgg.getBuckets().get(0).getKeyAsString();
            brandVo.setBrandName(brandName);
            //??????????????????
            ParsedStringTerms brandImgAgg = bucket.getAggregations().get("brand_img_agg");
            String brandImg = brandImgAgg.getBuckets().get(0).getKeyAsString();
            brandVo.setBrandImg(brandImg);
            brandVos.add(brandVo);


        }
        result.setBrands(brandVos);
        //??????????????????????????????
        List<SearchResult.CatalogVo> catalogVos = new ArrayList<>();
        ParsedLongTerms catalogAgg = response.getAggregations().get("catalog_agg");
        for (Terms.Bucket bucket : catalogAgg.getBuckets()) {
            SearchResult.CatalogVo catalogVo = new SearchResult.CatalogVo();
            //????????????ID
            long catalogId = bucket.getKeyAsNumber().longValue();
            catalogVo.setCatalogId(catalogId);
            //???????????????
            ParsedStringTerms catalogNameAgg = bucket.getAggregations().get("catalog_name_agg");
            String catalogName = catalogNameAgg.getBuckets().get(0).getKeyAsString();
            catalogVo.setCatalogName(catalogName);
            catalogVos.add(catalogVo);
        }

        result.setCatalogs(catalogVos);
        //===============????????????????????????????????????====================//
        //5???????????????-??????
        result.setPageNum(searchParam.getPageNum());
        //5???1???????????????????????????
        long total = hits.getTotalHits().value;
        result.setTotal(total);
        //5???2????????????-?????????-??????
        int totalPages = (int)total % EsContant.PRODUCT_PAGESIZE == 0 ?
                (int)total / EsContant.PRODUCT_PAGESIZE : ((int)total / EsContant.PRODUCT_PAGESIZE + 1);
        result.setTotalPages(totalPages);
        List<Integer> pageNavs = new ArrayList<>();
        for (int i = 0; i <totalPages; i++) {
            pageNavs.add(i);

        }
        result.setPageNavs(pageNavs);
        //???????????????
        if (searchParam.getAttrs() != null && searchParam.getAttrs().size() > 0){
            List<SearchResult.NavVo> navVos = searchParam.getAttrs().stream().map(attr -> {
                SearchResult.NavVo navVo = new SearchResult.NavVo();
                String[] s = attr.split("_");
                navVo.setNavValue(s[1]);
                //????????????????????????id??????????????????
                R r = productFeignService.info(Long.parseLong(s[0]));
                if (r.getCode() == 0) {
                    AttrResponseVo data = r.getData(new TypeReference<AttrResponseVo>() {
                    },"attr");
                    navVo.setNavName(data.getAttrName());
                } else {
                    navVo.setNavName(s[0]);
                }

                //2???????????????????????????????????????????????????????????????????????????????????????url?????????????????????
                //??????????????????????????????????????????
                String encode=null;
                try {
                    encode = URLEncoder.encode(attr, "UTF-8");
                    encode.replace("+","%20");  //??????????????????????????????Java???????????????????????????
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                String queryString = searchParam.get_queryString();

                String replace = searchParam.get_queryString().replace("&attrs=" + encode, "");
                navVo.setLink("http://search.gulimall.com/list.html?" + replace);


                return navVo;
            }).collect(Collectors.toList());
            result.setNavs(navVos);
        }





        return result;
    }
}
