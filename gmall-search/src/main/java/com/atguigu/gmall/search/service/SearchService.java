package com.atguigu.gmall.search.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchParamVo;
import com.atguigu.gmall.search.pojo.SearchResponseAttrVo;
import com.atguigu.gmall.search.pojo.SearchResponseVo;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    public SearchResponseVo search(SearchParamVo paramVo) {
        try {
            SearchResponse response = this.restHighLevelClient.search(new SearchRequest(new String[]{"goods"}, this.buildDsl(paramVo)), RequestOptions.DEFAULT);
            SearchResponseVo responseVo = this.parseResult(response);
            //设置分页参数
            responseVo.setPageNum(paramVo.getPageNum());
            responseVo.setPageSize(paramVo.getPageSize());
            return responseVo;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;

    }

    private SearchResponseVo parseResult(SearchResponse response) {
        SearchResponseVo responseVo = new SearchResponseVo();
        //解析 hits: 总记录数和goods列表
        SearchHits hits = response.getHits();
        //设置总记录数
        responseVo.setTotal(hits.getTotalHits());
        SearchHit[] hitsHits = hits.getHits();
        //设置当前页数据
        responseVo.setGoodsList(Stream.of(hitsHits).map(hitsHit -> {
            //获取hitsHit对象中_source --> Json字符串
            String json = hitsHit.getSourceAsString();
            //反序列化为goods对象
            Goods goods = JSON.parseObject(json, Goods.class);
            //获取hitsHit中的高亮字段
            Map<String, HighlightField> highlightFields = hitsHit.getHighlightFields();
            HighlightField highlightField = highlightFields.get("title");
            highlightField.getFragments()[0].toString();
            return goods;
        }).collect(Collectors.toList()));

        // 解析aggregations:品牌 分类 规格参数的过滤条件
        Map<String, Aggregation> aggMap = response.getAggregations().asMap();
        //解析品牌聚合
        ParsedLongTerms brandIdAgg = (ParsedLongTerms) aggMap.get("brandIdAgg");
        List<? extends Terms.Bucket> brandIdAggBuckets = brandIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(brandIdAggBuckets)) {
            responseVo.setBrands(brandIdAggBuckets.stream().map(bucket -> {
                BrandEntity brandEntity = new BrandEntity();
                //获取桶中的key,设置品牌的id
                brandEntity.setId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                //获取桶中的子集合
                Map<String, Aggregation> brandSubMap = ((Terms.Bucket) bucket).getAggregations().asMap();
                //解析出品牌名称
                ParsedStringTerms brandNameAgg = (ParsedStringTerms) brandSubMap.get("brandNameAgg");
                List<? extends Terms.Bucket> nameAggBuckets = brandNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(nameAggBuckets)) {
                    brandEntity.setName(nameAggBuckets.get(0).getKeyAsString());
                }
                //解析出品牌logo
                ParsedStringTerms logoAgg = (ParsedStringTerms) brandSubMap.get("logoAgg");
                List<? extends Terms.Bucket> logoAggBuckets = logoAgg.getBuckets();
                if (!CollectionUtils.isEmpty(logoAggBuckets)) {
                    brandEntity.setLogo(logoAggBuckets.get(0).getKeyAsString());
                }
                return brandEntity;
            }).collect(Collectors.toList()));
        }
        //解析分类的聚合结果集 获取分类的过滤条件
        ParsedLongTerms categoryIdAgg = (ParsedLongTerms) aggMap.get("categoryIdAgg");
        List<? extends Terms.Bucket> buckets = categoryIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(buckets)) {
            buckets.stream().map(bucket -> {
                CategoryEntity categoryEntity = new CategoryEntity();
                return categoryEntity;
            }).collect(Collectors.toList());
            responseVo.setCategories(buckets.stream().map(bucket -> {
                CategoryEntity categoryEntity = new CategoryEntity();
                categoryEntity.setId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                ParsedStringTerms categoryNameAgg = (ParsedStringTerms) ((Terms.Bucket) bucket).getAggregations().get("categoryNameAgg");
                List<? extends Terms.Bucket> nameAggBuckets = categoryNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(nameAggBuckets)) {
                    categoryEntity.setName(nameAggBuckets.get(0).getKeyAsString());
                }
                return categoryEntity;
            }).collect(Collectors.toList()));
        }
        //解析规格参数的集合结果集,获取属性过滤条件
        ParsedNested attrAgg = (ParsedNested) aggMap.get("attrAgg");
        //获取嵌套聚合中的子聚合 --- attrIdAgg
        ParsedLongTerms attrIdAgg = (ParsedLongTerms) attrAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> attrIdAggBuckets = attrIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(attrIdAggBuckets)) {
            responseVo.setFilters(attrIdAggBuckets.stream().map(bucket -> {
                SearchResponseAttrVo responseAttrVo = new SearchResponseAttrVo();
                responseAttrVo.setAttrId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                //获取子聚合
                Map<String, Aggregation> attrSubAggMap = ((Terms.Bucket) bucket).getAggregations().asMap();
                ParsedStringTerms attrNameAgg = (ParsedStringTerms) attrSubAggMap.get("attrNameAgg");
                List<? extends Terms.Bucket> nameAggBuckets = attrNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(nameAggBuckets)) {
                    responseAttrVo.setAttrName(nameAggBuckets.get(0).getKeyAsString());
                }
                ParsedStringTerms attrValueAgg = (ParsedStringTerms) attrSubAggMap.get("attrValueAgg");
                List<? extends Terms.Bucket> valueAggBuckets = attrValueAgg.getBuckets();
                if (!CollectionUtils.isEmpty(valueAggBuckets)) {
                    responseAttrVo.setAttrValues(valueAggBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList()));
                }
                return responseAttrVo;
            }).collect(Collectors.toList()));
        }

        return responseVo;
    }

    private SearchSourceBuilder buildDsl(SearchParamVo paramVo) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        // 1.查询和过滤条件的构建
        String keyword = paramVo.getKeyword();
        if (StringUtils.isBlank(keyword)) {
            // TODO: 打广告
            return sourceBuilder;
        }
        // 构建布尔查询
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);
        // 1.1. 匹配查询
        boolQueryBuilder.must(QueryBuilders.matchQuery("title", keyword).operator(Operator.AND));

        // 1.2. 过滤条件
        // 1.2.1. 品牌过滤
        List<Long> brandId = paramVo.getBrandId();
        if (!CollectionUtils.isEmpty(brandId)) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", brandId));
        }

        // 1.2.2. 分类过滤
        List<Long> cid = paramVo.getCid();
        if (!CollectionUtils.isEmpty(cid)) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("categoryId", cid));
        }

        // 1.2.3. 价格区间
        Double priceFrom = paramVo.getPriceFrom();
        Double priceTo = paramVo.getPriceTo();
        if (priceFrom != null || priceTo != null) {
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("price");
            if (priceFrom != null) { // 如果priceFrom不为空，添加大于等于
                rangeQuery.gte(priceFrom);
            }
            if (priceTo != null) { // 如果priceTo不为空，添加小于等于
                rangeQuery.lte(priceTo);
            }
            boolQueryBuilder.filter(rangeQuery);
        }

        // 1.2.4. 是否有货
        Boolean store = paramVo.getStore();
        if (store != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("store", store));
        }

        // 1.2.5. 规格参数的嵌套过滤 >  ["4:8G-12G", ""]
        List<String> props = paramVo.getProps();
        if (!CollectionUtils.isEmpty(props)) {
            props.forEach(prop -> { // 4:8G-12G

                // 以：分割字符串获取attrId
                if (StringUtils.isNotBlank(prop)) {
                    String[] attrs = StringUtils.split(prop, ":");
                    if (attrs != null && attrs.length == 2) {
                        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

                        boolQuery.must(QueryBuilders.termQuery("searchAttrs.attrId", attrs[0]));
                        // 分割字符串：8G-12G
                        String[] attrValues = StringUtils.split(attrs[1], "-");
                        boolQuery.must(QueryBuilders.termsQuery("searchAttrs.attrValue", attrValues));

                        boolQueryBuilder.filter(QueryBuilders.nestedQuery("searchAttrs", boolQuery, ScoreMode.None));
                    }
                }
            });
        }

        // 2.排序条件的构建: 1-价格降序 2-价格升序 3-新品降序 4-销量降序
        Integer sort = paramVo.getSort();
        if (sort != null) {
            switch (sort) {
                case 1:
                    sourceBuilder.sort("price", SortOrder.DESC);
                    break;
                case 2:
                    sourceBuilder.sort("price", SortOrder.ASC);
                    break;
                case 3:
                    sourceBuilder.sort("createTime", SortOrder.DESC);
                    break;
                case 4:
                    sourceBuilder.sort("sales", SortOrder.DESC);
                    break;
                default:
                    sourceBuilder.sort("_score", SortOrder.DESC);
                    break;
            }
        }

        // 3.分页条件
        Integer pageNum = paramVo.getPageNum();
        Integer pageSize = paramVo.getPageSize();
        sourceBuilder.from((pageNum - 1) * pageSize);
        sourceBuilder.size(pageSize);

        // 4.高亮
        sourceBuilder.highlighter(new HighlightBuilder().field("title").preTags("<font style='color:red'>").postTags("</font>"));

        // 5.聚合
        // 5.1. 品牌的聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("brandIdAgg").field("brandId").subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName")).subAggregation(AggregationBuilders.terms("logoAgg").field("logo")));
        // 5.2. 分类的聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("categoryIdAgg").field("categoryId").subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName")));
        // 5.3. 规格参数的嵌套聚合
        sourceBuilder.aggregation(AggregationBuilders.nested("attrAgg", "searchAttrs").subAggregation(AggregationBuilders.terms("attrIdAgg").field("searchAttrs.attrId").subAggregation(AggregationBuilders.terms("attrNameAgg").field("searchAttrs.attrName")).subAggregation(AggregationBuilders.terms("attrValueAgg").field("searchAttrs.attrValue"))));
        // 6.构建结果集过滤
        sourceBuilder.fetchSource(new String[]{"skuId","defaultImage","title","subTitle","price"},null);

        System.out.println(sourceBuilder);
        return sourceBuilder;
    }
}
