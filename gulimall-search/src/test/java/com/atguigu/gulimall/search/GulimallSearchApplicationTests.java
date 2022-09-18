package com.atguigu.gulimall.search;

import com.alibaba.fastjson.JSON;
import com.atguigu.gulimall.search.config.GulimallElasticSearchConfig;
import lombok.Data;
import lombok.ToString;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.TermVectorsResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.Avg;
import org.elasticsearch.search.aggregations.metrics.AvgAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.naming.directory.SearchControls;
import java.io.IOException;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GulimallSearchApplicationTests {
    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Test
    public void contextLoads() {
        System.out.println(restHighLevelClient);
    }
    /**
     * 测试插入数据
     */
    @Test
    public void  insertEs() throws IOException {
        IndexRequest request = new IndexRequest("user_info");
        request.id("1");
        UserInfo userInfo = new UserInfo();
        userInfo.setName("fuzs");
        userInfo.setAge(21);
        String s = JSON.toJSONString(userInfo);
        request.source(s, XContentType.JSON);
        //执行真正的插入
        IndexResponse index = restHighLevelClient.index(request, GulimallElasticSearchConfig.COMMON_OPTIONS);


    }
    @Data
    class UserInfo{
        private String name;
        private Integer age;
    }

    /**
     * 检索数据
     */
    @Test
    public void searchData() throws IOException {
        SearchRequest searchRequest = new SearchRequest();
        //设置要查找的索引
        searchRequest.indices("bank");
        //设置查询条件
        SearchSourceBuilder builder = new SearchSourceBuilder();
        builder.query(QueryBuilders.matchQuery("address","Mill"));
        //聚合
        //按照年龄聚合
        TermsAggregationBuilder age_term = AggregationBuilders.terms("age_term").field("age").size(10);
        builder.aggregation(age_term);
        //按照平均薪资聚合
        AvgAggregationBuilder avg_blance = AggregationBuilders.avg("avg_blance").field("balance");
        builder.aggregation(avg_blance);
        searchRequest.source(builder);
        System.out.println("检索条件："+builder.toString());
        //执行查询
        SearchResponse search = restHighLevelClient.search(searchRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);
        System.out.println(search.toString());
        //获取结果信息
        SearchHits hits = search.getHits();
        SearchHit[] hits1 = hits.getHits();
        for (SearchHit documentFields : hits1) {
            String sourceAsString = documentFields.getSourceAsString();
            //将数据转为实体类
            Account account = JSON.parseObject(sourceAsString, Account.class);
            System.out.println(account);
        }
        //获取所有聚合信息
        Aggregations aggregations = search.getAggregations();
        Terms age_term1 = aggregations.get("age_term");
        for (Terms.Bucket bucket : age_term1.getBuckets()) {
            String keyAsString = bucket.getKeyAsString();
            long docCount = bucket.getDocCount();
            System.out.println("年龄："+keyAsString+"=====>"+docCount);
        }
        Avg avg_blance1 = aggregations.get("avg_blance");
        double value = avg_blance1.getValue();
        System.out.println("平均薪资："+value);

    }

    @Data
    @ToString
    static class Account{
        private int account_number;
        private int balance;
        private String firstname;
        private String lastname;
        private int age;
        private String gender;
        private String address;
        private String employer;
        private String email;
        private String city;
        private String state;
    }




}
