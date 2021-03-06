package com.free.es.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.free.es.annotation.Document;
import com.free.es.annotation.EsId;
import com.free.es.model.eunm.FieldType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EsUtil {

    private final RestHighLevelClient restHighLevelClient;

    /**
     * ????????????(??????????????????5???????????????1)
     *
     * @param clazz ????????????????????????es??????
     * @throws IOException
     */
    public boolean createIndexRequest(Class clazz) throws Exception {
        Document declaredAnnotation = (Document)clazz.getDeclaredAnnotation(Document.class);
        if(declaredAnnotation == null){
            throw new Exception(String.format("class name: %s can not find Annotation [Document], please check", clazz.getName()));
        }
        String indexName = declaredAnnotation.index();
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        request.settings(Settings.builder()
                                 // ??????????????????3??? ?????????2
                                 .put("index.number_of_shards", 3)
                                 .put("index.number_of_replicas", 2)
        );
        request.mapping(generateBuilder(clazz));
        CreateIndexResponse response = restHighLevelClient.indices().create(request, RequestOptions.DEFAULT);
        // ??????????????????????????????????????????
        boolean acknowledged = response.isAcknowledged();
        // ???????????????????????????????????????????????????????????????????????????????????????
        boolean shardsAcknowledged = response.isShardsAcknowledged();
        if (acknowledged || shardsAcknowledged) {
            log.info("????????????????????????????????????{}", indexName);
            return true;
        }
        return false;
    }

    /**
     * ????????????(??????????????????5???????????????1)
     * @param clazz ????????????????????????es??????
     * @throws IOException
     */
    public boolean createIndexIfNotExist(Class clazz) throws Exception {
        Document declaredAnnotation = (Document)clazz.getDeclaredAnnotation(Document.class);
        if(declaredAnnotation == null){
            throw new Exception(String.format("class name: %s can not find Annotation [Document], please check", clazz.getName()));
        }
        String indexName = declaredAnnotation.index();

        boolean indexExists = isIndexExists(indexName);
        if (!indexExists) {
            CreateIndexRequest request = new CreateIndexRequest(indexName);
            request.settings(Settings.builder()
                                     // ??????????????????3??? ?????????2
                                     .put("index.number_of_shards", 3)
                                     .put("index.number_of_replicas", 2)
            );
            request.mapping(generateBuilder(clazz));
            CreateIndexResponse response = restHighLevelClient.indices().create(request, RequestOptions.DEFAULT);
            // ??????????????????????????????????????????
            boolean acknowledged = response.isAcknowledged();
            // ???????????????????????????????????????????????????????????????????????????????????????
            boolean shardsAcknowledged = response.isShardsAcknowledged();
            if (acknowledged || shardsAcknowledged) {
                log.info("????????????????????????????????????{}", indexName);
                return true;
            }
        }
        return false;
    }

    /**
     * ????????????????????????
     * @param indexName
     * @return
     */
    public boolean isIndexExists(String indexName){
        boolean exists = false;
        try {
            GetIndexRequest getIndexRequest = new GetIndexRequest(indexName);
            getIndexRequest.humanReadable(true);
            exists = restHighLevelClient.indices().exists(getIndexRequest,RequestOptions.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return exists;
    }

    /**
     * ????????????(??????????????????5???????????????1)???
     * ????????????????????????????????????????????????
     * ??????????????????????????????
     *
     * @param clazz ????????????????????????es??????
     * @throws IOException
     */
    public boolean updateIndex(Class clazz) throws Exception {
        Document declaredAnnotation = (Document )clazz.getDeclaredAnnotation(Document.class);
        if(declaredAnnotation == null){
            throw new Exception(String.format("class name: %s can not find Annotation [Document], please check", clazz.getName()));
        }
        String indexName = declaredAnnotation.index();
        PutMappingRequest request = new PutMappingRequest(indexName);

        request.source(generateBuilder(clazz));
        AcknowledgedResponse response = restHighLevelClient.indices().putMapping(request, RequestOptions.DEFAULT);
        // ??????????????????????????????????????????
        boolean acknowledged = response.isAcknowledged();

        if (acknowledged ) {
            log.info("??????????????????????????????????????????{}", indexName);
            return true;
        }
        return false;
    }

    /**
     * ????????????
     * @param indexName
     * @return
     */
    public boolean delIndex(String indexName) throws IOException {
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indexName);
        deleteIndexRequest.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);
        AcknowledgedResponse delete = restHighLevelClient.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
        return delete.isAcknowledged();
    }

    /**
     * ??????????????????
     * @param o
     * @return
     */
    public IndexResponse index(Object o) throws Exception {
        Document declaredAnnotation = (Document )o.getClass().getDeclaredAnnotation(Document.class);
        if(declaredAnnotation == null){
            throw new Exception(String.format("class name: %s can not find Annotation [Document], please check", o.getClass().getName()));
        }
        String indexName = declaredAnnotation.index();

        IndexRequest request = new IndexRequest(indexName);
        Field fieldByAnnotation = getFieldByAnnotation(o, EsId.class);
        if (fieldByAnnotation != null) {
            fieldByAnnotation.setAccessible(true);
            try {
                Object id = fieldByAnnotation.get(o);
                request =request.id(id.toString());
            } catch (IllegalAccessException e) {
                log.error("??????id???????????????{}", e);
            }
        }

        String userJson = JSON.toJSONString(o);
        request.source(userJson, XContentType.JSON);
        return restHighLevelClient.index(request, RequestOptions.DEFAULT);
    }


    /**
     * ??????id??????
     * @return
     */
    public String queryById(String indexName, String id) throws IOException {
        GetRequest getRequest = new GetRequest(indexName, id);
        GetResponse getResponse = restHighLevelClient.get(getRequest, RequestOptions.DEFAULT);
        return getResponse.getSourceAsString();
    }

    /**
     * ??????????????????json?????????
     * @param indexName
     * @param searchSourceBuilder
     * @return
     * @throws IOException
     */
    public String search(String indexName, SearchSourceBuilder searchSourceBuilder) throws IOException {
        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.source(searchSourceBuilder);
        searchRequest.scroll(TimeValue.timeValueMinutes(1L));
        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        String scrollId = searchResponse.getScrollId();
        SearchHits hits = searchResponse.getHits();
        JSONArray jsonArray = new JSONArray();
        for (SearchHit hit : hits) {
            String sourceAsString = hit.getSourceAsString();
            JSONObject jsonObject = JSON.parseObject(sourceAsString);
            jsonArray.add(jsonObject);
        }
        log.info("??????????????????" + hits.getTotalHits());
        return jsonArray.toJSONString();
    }

    /**
     * ????????????????????????
     * @param searchSourceBuilder
     * @param pageNum
     * @param pageSize
     * @param s
     * @param <T>
     * @return
     * @throws IOException
     */
    public <T> Page<T> search(SearchSourceBuilder searchSourceBuilder, int pageNum, int pageSize, Class<T> s) throws Exception {
        Document declaredAnnotation = s.getDeclaredAnnotation(Document.class);
        if(declaredAnnotation == null){
            throw new Exception(String.format("class name: %s can not find Annotation [Document], please check", s.getName()));
        }
        String indexName = declaredAnnotation.index();
        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        SearchHits hits = searchResponse.getHits();
        JSONArray jsonArray = new JSONArray();
        for (SearchHit hit : hits) {
            String sourceAsString = hit.getSourceAsString();
            JSONObject jsonObject = JSON.parseObject(sourceAsString);
            jsonArray.add(jsonObject);
        }
        log.info("??????????????????" + hits.getTotalHits());
        int total = (int)hits.getTotalHits().value;

        // ????????????
        List<T> list = jsonArray.toJavaList(s);
        Page<T> page = new Page<>(pageNum,pageSize);
        page.setRecords(list);
        page.setTotal(total);
        page.setPages(total== 0 ? 0: (total%pageSize == 0 ? total / pageSize : (total / pageSize) + 1));
        return page;
    }

    /**
     * ???????????????????????????
     * @param searchSourceBuilder
     * @param s
     * @param <T>
     * @return
     * @throws IOException
     */
    public <T> List<T> search(SearchSourceBuilder searchSourceBuilder, Class<T> s) throws Exception {
        Document declaredAnnotation = s.getDeclaredAnnotation(Document.class);
        if(declaredAnnotation == null){
            throw new Exception(String.format("class name: %s can not find Annotation [Document], please check", s.getName()));
        }
        String indexName = declaredAnnotation.index();
        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.source(searchSourceBuilder);
        searchRequest.scroll(TimeValue.timeValueMinutes(1L));
        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        String scrollId = searchResponse.getScrollId();
        SearchHits hits = searchResponse.getHits();
        JSONArray jsonArray = new JSONArray();
        for (SearchHit hit : hits) {
            String sourceAsString = hit.getSourceAsString();
            JSONObject jsonObject = JSON.parseObject(sourceAsString);
            jsonArray.add(jsonObject);
        }
        // ????????????
        return jsonArray.toJavaList(s);
    }

    /**
     * ??????????????????
     * ???????????? ?????????
     * ??????????????? ?????????
     * @param list
     * @return
     */
    public <T> boolean batchSaveOrUpdate(List<T> list) throws Exception {
        Object o1 = list.get(0);
        Document declaredAnnotation = (Document )o1.getClass().getDeclaredAnnotation(Document.class);
        if(declaredAnnotation == null){
            throw new Exception(String.format("class name: %s can not find Annotation [@Document], please check", o1.getClass().getName()));
        }
        String indexName = declaredAnnotation.index();

        BulkRequest request = new BulkRequest(indexName);
        for (Object o : list) {
            String jsonStr = JSON.toJSONString(o);
            IndexRequest indexReq = new IndexRequest().source(jsonStr, XContentType.JSON);

            Field fieldByAnnotation = getFieldByAnnotation(o, EsId.class);
            if (fieldByAnnotation != null) {
                fieldByAnnotation.setAccessible(true);
                try {
                    Object id = fieldByAnnotation.get(o);
                    indexReq = indexReq.id(id.toString());
                } catch (IllegalAccessException e) {
                    log.error("??????id???????????????{}", e);
                }
            }
            request.add(indexReq);
        }
        BulkResponse bulkResponse = restHighLevelClient.bulk(request, RequestOptions.DEFAULT);

        for(BulkItemResponse bulkItemResponse : bulkResponse){
            DocWriteResponse itemResponse = bulkItemResponse.getResponse();
            IndexResponse indexResponse = (IndexResponse) itemResponse;
            log.info("?????????????????????{}", indexResponse);
            if(bulkItemResponse.isFailed()){
                log.error("es ????????????{}",bulkItemResponse.getFailureMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * ????????????
     * @param indexName??? ????????????
     * @param docId???     ??????id
     */
    public boolean deleteDoc(String indexName, String docId) throws IOException {
        DeleteRequest request = new DeleteRequest(indexName, docId);
        DeleteResponse deleteResponse = restHighLevelClient.delete(request, RequestOptions.DEFAULT);
        // ??????response
        String index = deleteResponse.getIndex();
        String id = deleteResponse.getId();
        long version = deleteResponse.getVersion();
        ReplicationResponse.ShardInfo shardInfo = deleteResponse.getShardInfo();
        if (shardInfo.getFailed() > 0) {
            for (ReplicationResponse.ShardInfo.Failure failure :
                    shardInfo.getFailures()) {
                String reason = failure.reason();
                log.info("???????????????????????? {}", reason);
            }
        }
        return true;
    }

    /**
     * ??????json??????????????????
     * @param indexName
     * @param docId
     * @param o
     * @return
     * @throws IOException
     */
    public boolean updateDoc(String indexName, String docId, Object o) throws IOException {
        UpdateRequest request = new UpdateRequest(indexName, docId);
        request.doc(JSON.toJSONString(o), XContentType.JSON);
        UpdateResponse updateResponse = restHighLevelClient.update(request, RequestOptions.DEFAULT);
        String index = updateResponse.getIndex();
        String id = updateResponse.getId();
        long version = updateResponse.getVersion();
        if (updateResponse.getResult() == DocWriteResponse.Result.CREATED) {
            return true;
        } else if (updateResponse.getResult() == DocWriteResponse.Result.UPDATED) {
            return true;
        } else if (updateResponse.getResult() == DocWriteResponse.Result.DELETED) {
        } else if (updateResponse.getResult() == DocWriteResponse.Result.NOOP) {

        }
        return false;
    }

    /**
     * ??????Map??????????????????
     * @param indexName
     * @param docId
     * @param map
     * @return
     * @throws IOException
     */
    public boolean updateDoc(String indexName, String docId, Map<String, Object> map) throws IOException {
        UpdateRequest request = new UpdateRequest(indexName, docId);
        request.doc(map);
        UpdateResponse updateResponse = restHighLevelClient.update(request, RequestOptions.DEFAULT);
        String index = updateResponse.getIndex();
        String id = updateResponse.getId();
        long version = updateResponse.getVersion();
        if (updateResponse.getResult() == DocWriteResponse.Result.CREATED) {
            return true;
        } else if (updateResponse.getResult() == DocWriteResponse.Result.UPDATED) {
            return true;
        } else if (updateResponse.getResult() == DocWriteResponse.Result.DELETED) {
        } else if (updateResponse.getResult() == DocWriteResponse.Result.NOOP) {

        }
        return false;
    }


    private XContentBuilder generateBuilder(Class clazz) throws IOException {
        // ???????????????????????????
        Document doc = (Document) clazz.getAnnotation(Document.class);
        System.out.println(doc.index());
        System.out.println(doc.type());

        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        builder.startObject("properties");
        Field[] declaredFields = clazz.getDeclaredFields();
        for (Field f : declaredFields) {
            if (f.isAnnotationPresent(com.free.es.annotation.Field.class)) {
                // ????????????
                com.free.es.annotation.Field  declaredAnnotation = f.getDeclaredAnnotation(com.free.es.annotation.Field.class);

                // ?????????????????????
                /**
                 * {
                 *   "mappings": {
                 *     "properties": {
                 *       "region": {
                 *         "type": "keyword"
                 *       },
                 *       "manager": {
                 *         "properties": {
                 *           "age":  { "type": "integer" },
                 *           "name": {
                 *             "properties": {
                 *               "first": { "type": "text" },
                 *               "last":  { "type": "text" }
                 *             }
                 *           }
                 *         }
                 *       }
                 *     }
                 *   }
                 * }
                 */
                if (declaredAnnotation.type() == FieldType.OBJECT) {
                    // ????????????????????????-- Action
                    Class<?> type = f.getType();
                    Field[] df2 = type.getDeclaredFields();
                    builder.startObject(f.getName());
                    builder.startObject("properties");
                    // ?????????????????????????????????
                    for (Field f2 : df2) {
                        if (f2.isAnnotationPresent(com.free.es.annotation.Field.class)) {
                            // ????????????
                            com.free.es.annotation.Field declaredAnnotation2 = f2.getDeclaredAnnotation(com.free.es.annotation.Field.class);
                            builder.startObject(f2.getName());
                            builder.field("type", declaredAnnotation2.type().getType());
                            // keyword???????????????
                            if (declaredAnnotation2.type() == FieldType.TEXT) {
                                builder.field("analyzer", declaredAnnotation2.analyzer().getType());
                            }
                            builder.endObject();
                        }
                    }
                    builder.endObject();
                    builder.endObject();

                }else{
                    builder.startObject(f.getName());
                    builder.field("type", declaredAnnotation.type().getType());
                    // keyword???????????????
                    if (declaredAnnotation.type() == FieldType.TEXT) {
                        builder.field("analyzer", declaredAnnotation.analyzer().getType());
                    }
                    builder.endObject();
                }
            }
        }
        // ??????property
        builder.endObject();
        builder.endObject();
        return builder;
    }


    private static Field getFieldByAnnotation(Object o, Class annotationClass){
        Field[] declaredFields = o.getClass().getDeclaredFields();
        if (declaredFields != null && declaredFields.length >0) {
            for(Field f : declaredFields){
                if (f.isAnnotationPresent(annotationClass)) {
                    return f;
                }
            }
        }
        return null;
    }

  /*
    *//**
     * ????????????????????????
     *
     * @param index
     * @return
     *//*
    public boolean isIndexExist(String index) {
        GetIndexRequest request = new GetIndexRequest(index);
        try {
            return client.indices().exists(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.error("??????????????????", e);
            return false;
        }
    }

    *//**
     * ???????????????
     *
     * @param index
     *//*
    public boolean deleteIndexRequest(String index) {
        if (!isIndexExist(index)) {
            log.error("Index is not exits!");
            return false;
        }
        //??????????????????
        DeleteIndexRequest request = new DeleteIndexRequest(index);
        try {
            //?????????????????????
            AcknowledgedResponse delete = client.indices().delete(request, RequestOptions.DEFAULT);
            log.info("???????????????{}??????", index);
            return delete.isAcknowledged();
        } catch (IOException e) {
            log.error("??????????????? {" + index + "}??????", e);
            return false;
        }
    }

    *//**
     * ??????id??????????????????
     *
     * @param index
     * @param id
     * @param object
     *//*
    public void updateRequestById(String index, String id, Object object) {
        UpdateRequest updateRequest = new UpdateRequest(index, id);
        updateRequest.doc(BeanUtil.beanToMap(object), XContentType.JSON);
        try {
            client.update(updateRequest, RequestOptions.DEFAULT);
            log.info("?????????: {}, id???: {}, ??????????????????", index, id);
        } catch (IOException e) {
            log.error("?????????????????? {" + index + "} ?????? {" + object + "} ??????", e);
        }
    }

    *//**
     * ??????????????????
     *
     * @param index
     * @param id
     * @param object
     *//*
    public void insertRequest(String index, String id, Object object) {
        IndexRequest indexRequest = new IndexRequest(index).id(id).source(BeanUtil.beanToMap(object), XContentType.JSON);
        try {
            IndexResponse response = client.index(indexRequest, RequestOptions.DEFAULT);
            log.info("?????????????????? ?????????: {}, response ??????: {}, id???: {}", index, response.status().getStatus(), response.getId());
        } catch (IOException e) {
            log.error("?????????????????? {" + index + "} ?????? {" + object + "} ??????", e);
        }
    }

    *//**
     * ??????id??????????????????
     *
     * @param index
     * @param id
     *//*
    public void deleteRequestById(String index, String id) {
        DeleteRequest deleteRequest = new DeleteRequest(index, id);
        try {
            client.delete(deleteRequest, RequestOptions.DEFAULT);
            log.info("?????????: {}, id???: {}??????????????????", index, id);
        } catch (IOException e) {
            log.error("?????????????????? {" + index + "} ??????id {" + id + "} ??????", e);
        }
    }


    *//**
     * ??????ID????????????
     *
     * @param index  ?????????
     * @param id     ??????ID
     * @param fields ???????????????????????????????????????????????????????????????
     * @return
     *//*
    public Map<String, Object> searchDataById(String index, String id, String fields) {
        GetRequest request = new GetRequest(index, id);
        if (StringUtils.isNotEmpty(fields)) {
            //???????????????????????????????????????????????????????????????????????????
            request.fetchSourceContext(new FetchSourceContext(true, fields.split(","), Strings.EMPTY_ARRAY));
        }
        GetResponse response = null;
        try {
            response = client.get(request, RequestOptions.DEFAULT);
            Map<String, Object> map = response.getSource();
            //????????????????????????id
            map.put("id", response.getId());
            return map;
        } catch (IOException e) {
            log.error("??????????????????", e);
            return null;
        }
    }

    *//**
     * ??????ID????????????????????????
     *
     * @param index ????????????????????????
     * @param id    ??????ID
     * @return
     *//*
    public boolean existsById(String index, String id) {
        GetRequest request = new GetRequest(index, id);
        //??????????????????_source????????????
        request.fetchSourceContext(new FetchSourceContext(false));
        request.storedFields("_none_");
        try {
            return client.exists(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.error("????????????", e);
            return false;
        }
    }

    *//**
     * ??????????????????
     *
     * @param index
     * @param queryBuilder
     *//*
    public SearchResponse search(String index, QueryBuilder queryBuilder) {
        SearchRequest searchRequest = new SearchRequest(index);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(queryBuilder);
        // ??????
        searchSourceBuilder.sort("createTime", SortOrder.DESC);
        ////???????????????fuzzy?????????????????? 2??????????????????????????????
        //QueryBuilders.fuzzyQuery("title", "ceshi").fuzziness(Fuzziness.build("2"));
        searchRequest.source(searchSourceBuilder);
        System.out.println(searchSourceBuilder.toString());
        System.out.println(JSONUtil.parseObj(searchSourceBuilder.toString()).toStringPretty());
        SearchResponse searchResponse = null;
        try {
            searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
            log.error("??????????????????", e);
        }
        return searchResponse;
    }*/
}
