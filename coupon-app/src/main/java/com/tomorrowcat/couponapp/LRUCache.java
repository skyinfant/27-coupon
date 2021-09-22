package com.tomorrowcat.couponapp;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @description: 使用linkedHashMap实现基于lru淘汰算法的缓存
 * lru算法：维护一个固定长度的队列，新插入的元素放到队首，访问过的元素也移动到队首，如果队列满了后再插入一个，则会把队尾那个删除
 * 利用linkedHashMap实现lru：创建一个固定长度（假设为3），具有访问顺序的linkedHashMap, 假如插入 A，B，C三个元素，
 *                          则输出为A，B，C，把A叫做队首，把C叫做队尾，假如访问B，再次输出为 A,C,B  ,假如插入D，再次输出为 C,B,D，A被淘汰了
 *
 * @author: kim
 * @create: 2021-07-29 21:43
 * @version: 1.0.0
 */
public class LRUCache<K,V> extends LinkedHashMap<K,V> {
    //缓存容量
    private int cacheSize;

    public LRUCache(int cacheSize){
        //初始容量为16，负载因子为0.75，元素具有访问顺序，被访问过的元素会被移到队尾
        super(16,0.75F,true);
        this.cacheSize = cacheSize;
    }

    /**
     * @description:  缓存淘汰策略，删除最近最少访问的元素（队首）
     *                刚插入的元素放队尾，被访问过后也是移到队尾，所以队首的元素是最少访问的
     *                当put元素时调用此方法，检查是否需要淘汰缓存
     * @param: eldest
     * @return: boolean
     */
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        //超过设定的缓存容量就删除队首
        boolean isrm = size() > cacheSize;
        if(isrm){
            System.out.println("调用removeEldestEntry，清除缓存key: " +eldest.getKey());
        }
        //返回true则删除 eldest（队首）
        return isrm;
    }

    public static void main(String[] args) {
        LRUCache<String, String> cache = new LRUCache<>(5);
        cache.put("A","A");
        cache.put("B","B");
        cache.put("C","C");
        cache.put("D","D");
        cache.put("E","E");

        System.out.println("初始化:"+cache.keySet());    // abcde
        System.out.println("访问C:"+cache.get("C"));    // c
        System.out.println("访问C后:"+cache.keySet());    //abdec
        System.out.println("插入F:"+cache.put("F","F"));  //null
        System.out.println("put F后:"+cache.keySet());    //bdecf




    }
}