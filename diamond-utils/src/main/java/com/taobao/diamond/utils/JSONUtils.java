package com.taobao.diamond.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

public class JSONUtils {

    public static String serializeObject(Object o) throws Exception {
        return JSON.toJSONString(o);
    }

    public static Object deserializeObject(String s, Class<?> clazz) throws Exception {
        return JSON.parseObject(s, clazz);
    }
    
    public static Object deserializeObject(String s,TypeReference<?> typeReference) throws Exception {
        return JSON.parseObject(s, typeReference);
    }
    
}
