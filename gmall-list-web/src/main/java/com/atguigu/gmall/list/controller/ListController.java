package com.atguigu.gmall.list.controller;


import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.BaseAttrInfo;
import com.atguigu.gmall.bean.SkuLsAttrValue;
import com.atguigu.gmall.bean.SkuLsInfo;
import com.atguigu.gmall.bean.SkuLsParam;
import com.atguigu.gmall.service.AttrInfoService;
import com.atguigu.gmall.service.ListService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
public class ListController {

    @Reference
    AttrInfoService attrInfoService;

    @Reference
    ListService listService;

    @RequestMapping("list.html")
    public String search(SkuLsParam skuLsParam, ModelMap map){

        List<SkuLsInfo> skuLsInfos=null;
        //通过es查询索引库中的数据
        skuLsInfos = listService.search(skuLsParam);
        //封装平台属性
        List<BaseAttrInfo> attrInfos = getAttrValueIds(skuLsInfos);
        //拼接地址栏地址（catalog3Id,keyword,valueId）
        String urlParam = getUrlParam(skuLsParam);
        map.put("urlParam", urlParam);
        map.put("skuLsInfoList",skuLsInfos);
        map.put("attrList",attrInfos);

        return "list";
    }

    //通过搜索到的skuLsInfos获取平台属性值集合,再封装到平台属性中
    private List<BaseAttrInfo> getAttrValueIds(List<SkuLsInfo> skuLsInfos) {
        Set<String> valueIds = new HashSet<>();
        for (SkuLsInfo skuLsInfo : skuLsInfos) {
            List<SkuLsAttrValue> skuAttrValueList = skuLsInfo.getSkuAttrValueList();
            for (SkuLsAttrValue skuLsAttrValue : skuAttrValueList) {
                String valueId = skuLsAttrValue.getValueId();
                valueIds.add(valueId);
            }
        }
        // 根据去重后的id集合检索，关联到的平台属性列表
        List<BaseAttrInfo> attrInfos = new ArrayList<>();
        attrInfos = attrInfoService.getAttrListByValueIds(valueIds);
        return attrInfos;
    }

    // 制作普通url
    private String getUrlParam(SkuLsParam skuLsParam) {

        String urlParam = "";

        String[] valueId = skuLsParam.getValueId();
        String keyword = skuLsParam.getKeyword();
        String catalog3Id = skuLsParam.getCatalog3Id();

        if (StringUtils.isNotBlank(keyword)) {
            if (StringUtils.isBlank(urlParam)) {

                urlParam = "keyword=" + keyword;

            } else {
                urlParam = urlParam + "&keyword=" + keyword;
            }
        }

        if (StringUtils.isNotBlank(catalog3Id)) {
            if (StringUtils.isBlank(urlParam)) {

                urlParam = "catalog3Id=" + catalog3Id;

            } else {
                urlParam = urlParam + "&catalog3Id=" + catalog3Id;
            }
        }

        if (valueId != null && valueId.length > 0) {
            for (String s : valueId) {
                urlParam = urlParam + "&valueId=" + s;
            }
        }

        return urlParam;
    }

    @RequestMapping("index")
    public String index(){
        return "index";
    }
}
