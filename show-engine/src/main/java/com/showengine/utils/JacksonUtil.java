package com.showengine.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON utility methods.
 */
public final class JacksonUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(JacksonUtil.class);
    private static final ObjectMapper OBJECT_MAPPER;
    private static final ObjectMapper OBJECT_MAPPER_NONNULL;

    private JacksonUtil() {
    }

    static {
        OBJECT_MAPPER = new ObjectMapper();
        // Ignore JSON keys that do not have matching setters on the target object.
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        OBJECT_MAPPER_NONNULL = new ObjectMapper();
        OBJECT_MAPPER_NONNULL.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    }

    /**
     * Converts a JSON string into a JsonNode.
     *
     * @param jsonStr JSON string to convert
     *
     * @return JsonNode, or null when conversion fails or the string is blank
     */
    public static JsonNode toJsonNode(String jsonStr) throws IOException {
        if (StringUtils.isBlank(jsonStr)) {
            return null;
        }

        return OBJECT_MAPPER.readTree(jsonStr);
    }

    /**
     * Converts an object into a JSON string.
     *
     * @param obj object to convert
     *
     * @return JSON string, or an empty string when the object is null or conversion fails
     */
    public static String toJsonStrWithEmptyDefault(Object obj) {
        String jsonStr = "";

        try {
            jsonStr = toJsonStr(obj);
        } catch (Exception e) {
            LOGGER.warn("将对象转成Json字符串抛出异常, obj: {}", jsonStr, obj, e);
        }

        return jsonStr;
    }

    public static String toJsonStrNonNull(Object obj) {
        if (obj == null) {
            return null;
        }
        String jsonStr = "";
        try {

            return OBJECT_MAPPER_NONNULL.writeValueAsString(obj);
        } catch (Exception e) {
            LOGGER.warn("将对象转成Json字符串抛出异常, obj: {}", jsonStr, obj, e);
        }

        return jsonStr;
    }


    public static <K, V> Map<K, V> toMapWithEmptyDefault(Object obj, Class<K> keyClass, Class<V> valueClass){

        String jsonStr = toJsonStrNonNull(obj);
        if (StringUtils.isBlank(jsonStr)) {
            return new HashMap<>(0);
        }

        try {
            return toMap(jsonStr, keyClass, valueClass);
        } catch (Exception e) {
            LOGGER.warn("将对象转成Map抛出异常, obj: {}", jsonStr, obj, e);
        }

        return new HashMap<K, V>();

    }



    /**
     * Converts an object into a JSON string.
     *
     * @param obj object to convert
     *
     * @return JSON string, or null when the object is null
     */
    public static String toJsonStr(Object obj) throws JsonProcessingException {
        if (obj == null) {
            return null;
        }

        return OBJECT_MAPPER.writeValueAsString(obj);
    }

    /**
     * Converts a JSON string into an object of the specified type.
     *
     * @param jsonStr     JSON string to convert
     * @param targetClass target type
     *
     * @return object, or null when conversion fails or the string is blank
     */
    public static <T> T toBeanWithNullDefault(String jsonStr, Class<T> targetClass) {
        T bean = null;
        if (StringUtils.isBlank(jsonStr)) {
            return null;
        }

        try {
            bean = toBean(jsonStr, targetClass);
        } catch (IOException e) {
            LOGGER.warn("将Json字符串转成对象抛出异常, JsonString: {} , targetClass: {}", jsonStr, targetClass, e);
        }

        return bean;
    }

    public static <T> T toBeanWithNullDefault(String jsonStr, Type type) {
        T bean = null;
        if (StringUtils.isBlank(jsonStr)) {
            return null;
        }

        try {
            JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructType(type);
            bean = OBJECT_MAPPER.readValue(jsonStr, javaType);
        } catch (IOException e) {
            LOGGER.warn("将Json字符串转成对象抛出异常, JsonString: {} , type: {}", jsonStr, type, e);
        }

        return bean;
    }

    public static <T> T toBean(JsonNode node, Class<T> targetClass) throws JsonProcessingException {
        return OBJECT_MAPPER.treeToValue(node, targetClass);
    }

    /**
     * Converts a JSON string into an object of the specified type.
     *
     * @param jsonStr     JSON string
     * @param targetClass target type
     *
     * @return Java object
     */
    public static <T> T toBean(String jsonStr, Class<T> targetClass) throws IOException {
        return OBJECT_MAPPER.readValue(jsonStr, targetClass);
    }

    /**
     * Converts a JSON string into a Set containing the specified element type.
     *
     * @param jsonStr   JSON string to convert
     * @param itemClass element type for the target Set
     *
     * @return empty Set when the JSON string is blank
     */
    public static <T> Set<T> toSetWithEmptyDefault(String jsonStr, Class<T> itemClass) throws IOException {
        if (StringUtils.isBlank(jsonStr)) {
            return new HashSet<>(0);
        }

        return toSet(jsonStr, itemClass);
    }

    /**
     * Converts a JSON string into a Set containing the specified element type.
     *
     * @param jsonStr   JSON string to convert
     * @param itemClass element type for the target Set
     *
     * @return Set containing the specified element type
     */
    public static <T> Set<T> toSet(String jsonStr, Class<T> itemClass) throws IOException {
        JavaType javaType = OBJECT_MAPPER.getTypeFactory()
                .constructCollectionType(Set.class, itemClass);
        return OBJECT_MAPPER.readValue(jsonStr, javaType);
    }

    /**
     * Converts a JSON string into a List containing the specified element type.
     *
     * @param jsonStr   JSON string to convert
     * @param itemClass element type for the target List
     *
     * @return empty List when the JSON string is blank
     */
    public static <T> List<T> toListWithEmptyDefault(String jsonStr, Class<T> itemClass) {
        if (StringUtils.isBlank(jsonStr)) {
            return Lists.newArrayList();
        }

        try {
            return toList(jsonStr, itemClass);
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Converts a JSON string into a List containing the specified element type.
     *
     * @param jsonStr   JSON string to convert
     * @param itemClass element type for the target List
     *
     * @return List containing the specified element type
     */
    public static <T> List<T> toList(String jsonStr, Class<T> itemClass) throws IOException {
        JavaType javaType = OBJECT_MAPPER.getTypeFactory()
                .constructCollectionType(List.class, itemClass);
        return OBJECT_MAPPER.readValue(jsonStr, javaType);
    }

    /**
     * Converts a JSON string into a Map containing the specified key and value types.
     *
     * @param jsonStr    JSON string to convert
     * @param keyClass   key type for the target Map
     * @param valueClass value type for the target Map
     *
     * @return empty Map when the JSON string is blank
     */
    public static <K, V> Map<K, V> toMapWithEmptyDefault(String jsonStr, Class<K> keyClass, Class<V> valueClass) throws IOException {
        if (StringUtils.isBlank(jsonStr)) {
            return new HashMap<>(0);
        }

        return toMap(jsonStr, keyClass, valueClass);
    }

    /**
     * Converts a JSON string into a Map containing the specified key and value types.
     *
     * @param jsonStr    JSON string to convert
     * @param keyClass   key type for the target Map
     * @param valueClass value type for the target Map
     *
     * @return Map containing the specified key and value types
     */
    public static <K, V> Map<K, V> toMap(String jsonStr, Class<K> keyClass, Class<V> valueClass) throws IOException {
        JavaType javaType = OBJECT_MAPPER.getTypeFactory()
                .constructMapType(Map.class, keyClass, valueClass);
        return OBJECT_MAPPER.readValue(jsonStr, javaType);
    }

    /**
     * Converts a JSON string into a ConcurrentHashMap containing the specified key and value types.
     *
     * @param jsonStr    JSON string to convert
     * @param keyClass   key type for the target Map
     * @param valueClass value type for the target Map
     *
     * @return Map containing the specified key and value types
     */
    public static <K, V> ConcurrentHashMap<K, V> toConcurrentMap(String jsonStr, Class<K> keyClass, Class<V> valueClass) throws IOException {
        JavaType javaType = OBJECT_MAPPER.getTypeFactory()
                .constructMapType(ConcurrentHashMap.class, keyClass, valueClass);
        return OBJECT_MAPPER.readValue(jsonStr, javaType);
    }

    /**
     * Converts a JSON string into a ConcurrentHashMap whose values are Lists of the specified element type.
     *
     * @param jsonStr    JSON string to convert
     * @param keyClass   key type for the target Map
     * @param valueClass element type inside each List value
     *
     * @return Map containing the specified key and List value element types
     */
    public static <K, V> ConcurrentHashMap<K, List<V>> toConcurrentMapWithListValue(String jsonStr, Class<K> keyClass, Class<V> valueClass)
            throws IOException {
        JavaType keyJavaType = OBJECT_MAPPER.getTypeFactory()
                .constructType(keyClass);
        JavaType valueJavaType = OBJECT_MAPPER.getTypeFactory()
                .constructCollectionType(List.class, valueClass);
        JavaType javaType = OBJECT_MAPPER.getTypeFactory()
                .constructMapType(ConcurrentHashMap.class, keyJavaType, valueJavaType);
        return OBJECT_MAPPER.readValue(jsonStr, javaType);
    }
}
