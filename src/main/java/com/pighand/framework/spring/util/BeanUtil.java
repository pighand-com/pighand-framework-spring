package com.pighand.framework.spring.util;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * bean工具类
 *
 * @author wangshuli
 */
public class BeanUtil {

    /**
     * 关联查询
     * <p>
     * 用于：单张子表查询
     *
     * @param mainList
     * @param mainIdGetter
     * @param subTableQuery
     * @param subTableIdGetter
     * @param subResultSetter
     * @param <T>
     * @param <K>
     */
    public static <T, K> void queryWithRelatedData(List<T> mainList, Function<T, K> mainIdGetter,
        Function<Set<K>, List> subTableQuery, Function<Object, K> subTableIdGetter,
        BiConsumer<T, List> subResultSetter) {

        queryWithRelatedData(mainList, Collections.singletonList(mainIdGetter),
            Collections.singletonList(subTableQuery), Collections.singletonList(subTableIdGetter),
            Collections.singletonList(subResultSetter));
    }

    /**
     * 关联查询
     * <p>
     * 用于：所有子表外键，是主表的同一个字段
     *
     * @param mainList
     * @param mainIdGetter
     * @param subTableQueries
     * @param subTableIdGetters
     * @param subResultSetters
     * @param <T>
     * @param <K>
     */
    public static <T, K> void queryWithRelatedData(List<T> mainList, Function<T, K> mainIdGetter,
        List<Function<Set<K>, List>> subTableQueries, List<Function<Object, K>> subTableIdGetters,
        List<BiConsumer<T, List>> subResultSetters) {

        // 根据子表数量，初始化外键获取方法
        List<Function<T, K>> mainIdGetters = new ArrayList<>(subTableQueries.size());
        subTableQueries.forEach(item -> {
            mainIdGetters.add(mainIdGetter);
        });

        queryWithRelatedData(mainList, mainIdGetters, subTableQueries, subTableIdGetters, subResultSetters);
    }

    /**
     * 关联查询
     * <p>
     * 建议使用场景：
     * 一对多查询，时间复杂度：3n+1
     * <p>
     * 查询逻辑：
     * 1. 遍历主表，取出子表对应主表的外键（n）
     * 2. 查询字表数据（1）
     * 3. 使用外键作为key，将字表数据转为map（n）
     * 4. 遍历主表，根据key回填数据（n）
     *
     * @param mainList          主表数据
     * @param mainIdGetters     子表外键 对应主表的字段 的获取方法
     * @param subTableQueries   子表查询方法
     * @param subTableIdGetters 子表外键 的获取方法
     * @param subResultSetters  主表回填子表的方法
     * @param <T>               主表Entity
     * @param <K>               外键数据类型
     */
    public static <T, K> void queryWithRelatedData(List<T> mainList, List<Function<T, K>> mainIdGetters,
        List<Function<Set<K>, List>> subTableQueries, List<Function<Object, K>> subTableIdGetters,
        List<BiConsumer<T, List>> subResultSetters) {

        if (VerifyUtils.isEmpty(mainList)) {
            return;
        }

        if (mainIdGetters.size() != subTableQueries.size() || mainIdGetters.size() != subTableIdGetters.size()) {
            throw new IllegalArgumentException(
                "The size of mainIdGetters, subTableQueries, and subTableIdGetters must be the same.");
        }

        // 取出子表对应主表的外键
        List<Set<K>> mainIds = new ArrayList<>(mainIdGetters.size());
        mainIdGetters.forEach(item -> {
            int initSize = mainList.size() >= 16 ? 16 : mainList.size();
            mainIds.add(new HashSet<>(initSize));
        });

        mainList.forEach(mainItem -> {
            for (int i = 0; i < mainIdGetters.size(); i++) {
                mainIds.get(i).add(mainIdGetters.get(i).apply(mainItem));
            }
        });

        for (int i = 0; i < subTableQueries.size(); i++) {
            // 执行子表查询
            List<?> subResultList = subTableQueries.get(i).apply(mainIds.get(i));

            // list to map
            Function<Object, K> subTableIdGetter = subTableIdGetters.get(i);
            Map<K, List<Object>> subTableMap = subResultList.stream().collect(Collectors.groupingBy(subTableIdGetter));

            // 回填主表
            Function<T, K> mainIdGetter = mainIdGetters.get(i);
            BiConsumer<T, List> subResultSetter = subResultSetters.get(i);
            mainList.forEach(mainItem -> {
                K mainId = mainIdGetter.apply(mainItem);
                subResultSetter.accept(mainItem, subTableMap.get(mainId));
            });
        }
    }

    /**
     * 关联查询
     * <p>
     * 用于：单张子表查询
     *
     * @param main
     * @param mainIdGetter
     * @param subTableQuery
     * @param subResultSetter
     * @param <T>
     * @param <K>
     */
    public static <T, K> void queryWithRelatedData(T main, Function<T, K> mainIdGetter, Function<K, List> subTableQuery,
        BiConsumer<T, List> subResultSetter) {

        queryWithRelatedData(main, Collections.singletonList(mainIdGetter), Collections.singletonList(subTableQuery),
            Collections.singletonList(subResultSetter));
    }

    /**
     * 关联查询
     * <p>
     * 用于：所有子表外键，是主表的同一个字段
     *
     * @param main
     * @param mainIdGetter
     * @param subTableQueries
     * @param subResultSetters
     * @param <T>
     * @param <K>
     */
    public static <T, K> void queryWithRelatedData(T main, Function<T, K> mainIdGetter,
        List<Function<K, List>> subTableQueries, List<BiConsumer<T, List>> subResultSetters) {

        // 根据子表数量，初始化外键获取方法
        List<Function<T, K>> mainIdGetters = new ArrayList<>(subTableQueries.size());
        subTableQueries.forEach(item -> {
            mainIdGetters.add(mainIdGetter);
        });

        queryWithRelatedData(main, mainIdGetters, subTableQueries, subResultSetters);
    }

    /**
     * 关联查询
     * <p>
     * 建议使用场景：
     * 一对多查询，时间复杂度：2n+1
     * <p>
     * 查询逻辑：
     * 1. 遍历主表，取出子表对应主表的外键（n）
     * 2. 查询字表数据（1）
     * 3. 遍历主表，根据key回填数据（n）
     *
     * @param main             主表数据
     * @param mainIdGetters    子表外键 对应主表的字段 的获取方法
     * @param subTableQueries  子表查询方法
     * @param subResultSetters 主表回填子表的方法
     * @param <T>              主表Entity
     * @param <K>              外键数据类型
     */
    public static <T, K> void queryWithRelatedData(T main, List<Function<T, K>> mainIdGetters,
        List<Function<K, List>> subTableQueries, List<BiConsumer<T, List>> subResultSetters) {

        if (mainIdGetters.size() != subTableQueries.size()) {
            throw new IllegalArgumentException(
                "The size of mainIdGetters, subTableQueries, and subTableIdGetters must be the same.");
        }

        // 取出子表对应主表的外键
        List<K> mainIds = new ArrayList<>(mainIdGetters.size());

        for (int i = 0; i < mainIdGetters.size(); i++) {
            mainIds.add(mainIdGetters.get(i).apply(main));
        }

        for (int i = 0; i < subTableQueries.size(); i++) {
            // 执行子表查询
            List<?> subResultList = subTableQueries.get(i).apply(mainIds.get(i));

            // 回填主表
            BiConsumer<T, List> subResultSetter = subResultSetters.get(i);
            subResultSetter.accept(main, subResultList);
        }
    }
}
