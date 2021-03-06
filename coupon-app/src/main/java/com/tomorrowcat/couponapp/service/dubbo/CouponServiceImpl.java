package com.tomorrowcat.couponapp.service.dubbo;

import com.alibaba.fastjson.JSON;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.tomorrowcat.couponapp.constant.Constant;
import com.tomorrowcat.couponapp.domain.TCoupon;
import com.tomorrowcat.couponapp.domain.TCouponExample;
import com.tomorrowcat.couponapp.domain.TUserCoupon;
import com.tomorrowcat.couponapp.domain.TUserCouponExample;
import com.tomorrowcat.couponapp.mapper.TCouponMapper;
import com.tomorrowcat.couponapp.mapper.TUserCouponMapper;
import com.tomorrowcat.couponapp.util.SnowflakeIdWorker;
import com.tomorrowcat.couponserviceapi.dto.CouponDto;
import com.tomorrowcat.couponserviceapi.dto.CouponNoticeDto;
import com.tomorrowcat.couponserviceapi.dto.UserCouponDto;
import com.tomorrowcat.couponserviceapi.dto.UserCouponInfoDto;
import com.tomorrowcat.couponserviceapi.service.CouponService;
import com.tomorrowcat.userServiceApi.VO.UserVO;
import com.tomorrowcat.userServiceApi.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.annotation.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.util.CollectionUtils;


import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * @description:
 * @author: Kim
 * @create: 2021-07-26 12:43
 */
@Service
public class CouponServiceImpl implements CouponService {

    @Resource
    private TCouponMapper couponMapper;

    @Reference   //?????????????????? user-service-api
    private UserService userServce;

    @Resource
    private TUserCouponMapper userCouponMapper;

    @Resource
    private RedisTemplate redisTemplate;

    private static final Logger logger = LoggerFactory.getLogger(CouponServiceImpl.class);

    private static final String COUPON_NOTICE_KEY = "couponSet";

    private static final int COUPON_NOTICE_NUM = 5;


    /**
     * @description: ??????????????????coupon????????????load?????????
     * @param: key
     * @return: List<CouponDto>
     */
    public List<TCoupon> loadCoupon(Integer key) {
        TCouponExample example = new TCouponExample();
        example.createCriteria().andStatusEqualTo(Constant.USERFUL)
                .andStartTimeLessThan(new Date())
                .andEndTimeGreaterThan(new Date());

        return couponMapper.selectByExample(example);
    }


    //??????coupon??????   guva cache
    LoadingCache<Integer, List<TCoupon>> couponCache = CacheBuilder.newBuilder()
            //????????????
            .expireAfterWrite(10, TimeUnit.MINUTES)
            //????????????
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .build(new CacheLoader<Integer, List<TCoupon>>() {
                @Override
                public List<TCoupon> load(Integer key) throws Exception {
                    return loadCoupon(key);
                }
            });


    //??????coupon??????  caffeine
    com.github.benmanes.caffeine.cache.LoadingCache<Integer, List<TCoupon>> couponCaffeine = Caffeine.newBuilder()
            //????????????
            .expireAfterWrite(10, TimeUnit.MINUTES)
            //????????????
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .build(new com.github.benmanes.caffeine.cache.CacheLoader<Integer, List<TCoupon>>() {
                @Override
                public List<TCoupon> load(Integer key) throws Exception {
                    return loadCoupon(key);
                }
            });


    /***
     * ?????????????????????????????????????????????    caffeine   ???guava?????????????????????
     * @return
     */
    public List<CouponDto> getCouponList() {
        List<TCoupon> coupons = Lists.newArrayList();
        List<CouponDto> dtos = Lists.newArrayList();

        try {
            //????????????key???????????????????????????????????????????????????
            coupons = couponCaffeine.get(1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        coupons.forEach(coupon -> {
            CouponDto dto = new CouponDto();
            BeanUtils.copyProperties(coupon, dto);
            dtos.add(dto);
        });

        return dtos;

    }

    /**
     * @description: ????????????
     * @param: userCouponDto
     * @return: String
     */
    @Override
    public String saveUserCoupon(UserCouponDto dto) {
//        System.out.println("???????????????????????????");
        String result = check(dto);
        if (result != null) {
            return result;
        }
        TCoupon coupon = couponMapper.selectByPrimaryKey(dto.getCouponId());
        if (coupon == null) {
            return "coupon ??????";
        }

        boolean isDuplicate = isDuplicateUserCoupon(dto.getUserId(), coupon.getId());
        if (isDuplicate) {
            return "???????????????????????????";
        }

        return save2DB(dto, coupon);

    }

    /**
     * @description: ??????????????????????????????????????????????????????????????????????????????????????????
     * @param: userId
     * @return: int
     */
    private boolean isDuplicateUserCoupon(int userId, int couponId) {
        //???????????????????????????
        TUserCouponExample example = new TUserCouponExample();
        example.createCriteria().andUserIdEqualTo(userId)
                .andCouponIdEqualTo(couponId)
                .andStatusEqualTo(0);
        List<TUserCoupon> userCoupons = userCouponMapper.selectByExample(example);

        return userCoupons.size() > 0 ? true : false;
    }


    /**
     * @description: ????????????id????????????????????????????????????
     * @param: userId
     * @return: List<UserCouponInfoDto>
     */
    @Override
    public List<UserCouponInfoDto> userCouponList(Integer userId) {
        List<UserCouponInfoDto> dtos = Lists.newArrayList();
        if (userId == null) {
            return dtos;
        }
        //??????????????????UserCoupon
        List<TUserCoupon> userCoupons = getUserCoupons(userId);
        if (CollectionUtils.isEmpty(userCoupons)) {
            return dtos;
        }

        //?????? Map<couponId,TCoupon>
        Map<Integer, TCoupon> couponMap = getCouponMap(userCoupons);

        //??????coupon   ??????1???List<UserCouponInfoDto>
        return wrapCoupon(userCoupons, couponMap);

    }

    /**
     * @description: List<TUserCoupon> + Map<CouponId,TCoupon> ---> List<UserCouponInfoDto>
     * @param: userCoupons
     * @param: idCouponMap
     * @return: List<UserCouponInfoDto>
     */
    private List<UserCouponInfoDto> wrapCoupon(List<TUserCoupon> userCoupons, Map<Integer, TCoupon> couponMap) {

        List<UserCouponInfoDto> dtos = userCoupons.stream().map(userCoupon -> {
            UserCouponInfoDto dto = new UserCouponInfoDto();
            BeanUtils.copyProperties(userCoupon, dto);
            int couponId = userCoupon.getCouponId();
            TCoupon coupon = couponMap.get(couponId);
            dto.setAchieveAmount(coupon.getAchieveAmount());
            dto.setReduceAmount(coupon.getReduceAmount());
            logger.info("get user coupon list  result:{}", JSONObject.toJSON(dto));
            return dto;

        }).collect(Collectors.toList());
        return dtos;
    }

    /**
     * @description: List<TUserCoupon> ---> Map<couponId,TCoupon>
     * @param: userCoupon
     * @return: Map<Integer   ,   TCoupon>
     */
    private Map<Integer, TCoupon> getCouponMap(List<TUserCoupon> userCoupons) {
        Set<Integer> couponIds = getCouponIds(userCoupons);
        List<TCoupon> coupons = getCouponListByIds(StringUtils.join(couponIds, ","));
        Map<Integer, TCoupon> couponMap = couponList2Map(coupons);
        return couponMap;
    }

    /**
     * @description: List<TCoupon> ----> Map<CouponId,TCoupon>
     * @param: coupons
     * @return: Map<Integer   ,   TCoupon>
     */
    private Map<Integer, TCoupon> couponList2Map(List<TCoupon> coupons) {
        return coupons.stream().collect(Collectors
                .toMap(o -> o.getId(), o -> o));
    }

    /**
     * ??????couponIds          List<TUserCoupon> ---> Set<CouponId>
     */
    private Set<Integer> getCouponIds(List<TUserCoupon> userCoupons) {
        Set<Integer> couponIds = userCoupons.stream().map(userCoupon -> userCoupon.getCouponId()).collect(Collectors.toSet());
        return couponIds;


    }

    /**
     * @description: ???????????????????????????????????????
     * @param: userId
     * @return: List<TUserCoupon>
     */
    private List<TUserCoupon> getUserCoupons(Integer userId) {
        //???????????????????????????
        TUserCouponExample example = new TUserCouponExample();
        example.createCriteria().andUserIdEqualTo(userId)
                .andStatusEqualTo(0);
        List<TUserCoupon> userCoupons = userCouponMapper.selectByExample(example);
        return userCoupons;
    }


    /**
     * @description: ?????????????????????????????????
     * @param: dto
     * @param: coupon
     * @return: String
     */
    private String save2DB(UserCouponDto dto, TCoupon coupon) {
        TUserCoupon userCoupon = new TUserCoupon();
        BeanUtils.copyProperties(dto, userCoupon);
        userCoupon.setPicUrl(coupon.getPicUrl());
        userCoupon.setCreateTime(new Date());
        SnowflakeIdWorker worker = new SnowflakeIdWorker(0, 0);

        //userCoupon??????code???coupon???code???????????????????????????????????????????????????????????????
        userCoupon.setUserCouponCode(worker.nextId() + "");

        userCouponMapper.insertSelective(userCoupon);
        logger.info("save coupon sccess:{}", JSON.toJSONString(userCoupon));
        return "????????????";

    }

    public String check(UserCouponDto dto) {
        Integer couponId = dto.getCouponId();
        Integer userId = dto.getUserId();
        if (couponId == null || userId == null) {
            return "couponId??????userId??????";
        }
        return null;
    }


    /***
     * ?????????????????????????????????????????????  guava cache
     * @return
     */
    public List<TCoupon> getCouponListGuava() {
        List<TCoupon> coupons = Lists.newArrayList();
        try {
            //????????????key???????????????????????????????????????????????????
            coupons = couponCache.get(1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return coupons;

    }


    //????????????????????????couponId???key?????????key????????????coupon
    LoadingCache<Integer, TCoupon> couponIdsCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .build(new CacheLoader<Integer, TCoupon>() {
                @Override
                public TCoupon load(Integer id) throws Exception {
                    return loadIdsCoupon(id);
                }
            });

    //??????id??????coupon???????????????
    private TCoupon loadIdsCoupon(Integer id) {
        return couponMapper.selectByPrimaryKey(id);
    }

    /**
     * @description: ????????????coupon
     * @param: ids   ??????couponId   1,2,3
     * @return: List<TCoupon>
     */
    public List<TCoupon> getCouponListByIds(String ids) {
        //couponId??????
        String[] idStr = ids.split(",");
        //????????????????????????coupon???id
        List<Integer> loadFromDB = Lists.newArrayList();
        //???????????????????????????coupon
        List<TCoupon> coupons = Lists.newArrayList();
        //couponID List
        List<String> idList = Lists.newArrayList(idStr);
        for (String id : idList) {
            //????????????????????????????????????null???????????????
            TCoupon coupon = couponIdsCache.getIfPresent(Integer.parseInt(id));
            if (coupon == null) {
                //??????????????????
                loadFromDB.add(Integer.parseInt(id));
            } else {
                coupons.add(coupon);
            }

        }

        //????????????????????????????????????coupon
        List<TCoupon> coupons1 = getCouponByIds(loadFromDB);
        //jdk8??????
        Map<Integer, TCoupon> couponMap = coupons1.stream().collect(Collectors.toMap(TCoupon::getId, TCoupon -> TCoupon));

        //????????????
        //        Map<Integer, TCoupon> tCouponMap = new HashMap<>();
//        for (com.xdclass.couponapp.domain.TCoupon TCoupon : tCoupons1) {
//            //??????????????? key ????????? value ???????????????????????? value ??????????????????????????? value ????????????????????????????????????????????? null
//            //                 TCoupon::getId, TCoupon -> TCoupon
//            if (tCouponMap.put(TCoupon.getId(), TCoupon) != null) {
//                throw new IllegalStateException("Duplicate key");
//            }
//        }


        coupons.addAll(coupons1);
        //????????????????????????????????????
        couponIdsCache.putAll(couponMap);

        return coupons;

    }

    /**
     * @description: ????????????????????????????????????coupon
     * @param: ids
     * @return: List<TCoupon>
     */
    private List<TCoupon> getCouponByIds(List<Integer> ids) {
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }

        TCouponExample example = new TCouponExample();
        example.createCriteria().andIdIn(ids);
        return couponMapper.selectByExample(example);

    }


    //????????????
    LoadingCache<Integer, UserVO> userCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(1000)   //????????????10000???key
            .build(new CacheLoader<Integer, UserVO>() {
                @Override
                public UserVO load(Integer userId) throws Exception {
                    return loadUser(userId);
                }
            });

    //???????????????load?????????
    public UserVO loadUser(Integer userId) {
        return userServce.getUserById(userId);
    }


    /**
     * @description: ???????????????????????????
     * @param:
     * @return: Object
     */
    public Object query() {
        TCouponExample example = new TCouponExample();
        example.createCriteria().andTitleEqualTo("700ef10c-f9e7-4255-864a-c8200a5ff450");
        List<TCoupon> coupons = couponMapper.selectByExample(example);
        return coupons.get(0);
    }


    /**
     * @return Object
     * @description: ??????id??????????????????
     * @param: id
     * @author: kim
     * @date: 2021/7/27 18:00
     */
    public UserVO getUserById(int id) {
        UserVO userVO = new UserVO();
        try {
            userVO = userCache.get(id);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return userVO;
    }


    /**
     * @description: ?????????????????????coupon???????????????
     * @param:
     * @return: void
     */
    @PostConstruct
    void loadData() throws ExecutionException {

        couponCache.get(1);
    }

    //================================??????concurrentHashMap??????coupon???????????????guava cache??????????????????concurrentHashMap???
    private Map couponMap = new ConcurrentHashMap();

    //??????concurrentHashMap
    public void updateCouponMap() {
        Map couponMap2 = new ConcurrentHashMap();
        List<TCoupon> coupons = Lists.newArrayList();
        try {

            coupons = this.loadCoupon(1);
            couponMap2.put(1, coupons);
            couponMap = couponMap2;
            logger.info("update coupon list:{},coupon list size:{}", JSONObject.toJSON(coupons), coupons.size());
        } catch (Exception e) {
            logger.error("update coupon list:{},coupon list size:{}", JSONObject.toJSON(coupons), coupons.size(), e);
        }

    }

    //???ConcurrentHashMap?????????coupon??????
    public List<TCoupon> getCouponList4Map() {
        return (List<TCoupon>) couponMap.get(1);
    }

    //=============================================================================

    /**
     * ????????????????????????coupon??????????????????
     *
     * @param orderId
     * @param couponCode
     */
    public void updateCouponStatusAfterOrder(int userId, String couponCode, int orderId) {
        TUserCouponExample example = new TUserCouponExample();
        example.createCriteria()
                .andUserCouponCodeEqualTo(couponCode)
                .andUserIdEqualTo(userId);
        List<TUserCoupon> tUserCoupons = userCouponMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(tUserCoupons)) {
            logger.warn("??? coupon ?????????  userCouponCode={}", couponCode);
            return;
        }
        TUserCoupon userCoupon = tUserCoupons.get(0);

        userCoupon.setStatus(0);  //0--?????????
        userCoupon.setOrderId(orderId);
        userCouponMapper.updateByPrimaryKeySelective(userCoupon);

    }

    /**
     * ????????????????????????coupon????????????,????????????coupon?????????
     *
     * @param orderId
     * @param couponCode
     */
    public void updateCouponStatusAfterPay(int orderId, int userId) {
        List<TUserCoupon> tUserCoupons = getUserrCouponByUserIdAndOrderId(userId, orderId);
        if (CollectionUtils.isEmpty(tUserCoupons)) {
            logger.warn("????????????????????? userId={}  orderId={}", userId, orderId);
            return;
        }
        TUserCoupon userCoupon = tUserCoupons.get(0);

        //???????????????
        userCoupon.setStatus(1); //1--?????????
        userCouponMapper.updateByPrimaryKeySelective(userCoupon);

        //??????coupon?????????
        int couponId = userCoupon.getCouponId();

        String userCouonStr = userId + "_" + couponId;
        updateCouponNotice(userCouonStr);

    }


    /**
     * @description: ?????????????????????????????????????????????????????????????????????????????????????????????
     * @param: userId
     * @param: orderId
     * @return: void
     */
    public void updateCouponStatusAfterCancelOrder(Integer userId, Integer orderId) {
        List<TUserCoupon> tUserCoupons = getUserrCouponByUserIdAndOrderId(userId, orderId);
        if (CollectionUtils.isEmpty(tUserCoupons)) {
            logger.warn("????????????????????? userId={}  orderId={}", userId, orderId);
            return;
        }
        TUserCoupon userCoupon = tUserCoupons.get(0);
        userCoupon.setOrderId(0); //???????????????
        userCoupon.setStatus(0); //???????????????
        userCouponMapper.updateByPrimaryKeySelective(userCoupon);


    }


    /**
     * @description: ??????userId???orderId?????????????????????
     * @param: userId
     * @param: orderId
     * @return: TUserCoupon
     */
    private List<TUserCoupon> getUserrCouponByUserIdAndOrderId(int userId, int orderId) {
        TUserCouponExample example = new TUserCouponExample();
        example.createCriteria().andUserIdEqualTo(userId)
                .andOrderIdEqualTo(orderId);
        List<TUserCoupon> tUserCoupons = userCouponMapper.selectByExample(example);

        return tUserCoupons;

    }


    /**
     * @description: ????????????coupon?????????coupon???????????????redis???????????????redis????????????coupon
     * ??????coupon???????????????mq??????????????????,????????????????????????N?????????,userCouponStr??????userId_couponId
     * @param: userCouonStr
     * @return: void
     */
    public void updateCouponNotice(String userCouonStr) {
        //??? userId_CouponId ??????redis???sortedSet
        redisTemplate.opsForZSet().add(COUPON_NOTICE_KEY, userCouonStr, System.currentTimeMillis());
        //???score??????????????????sortedSet???????????????
        Set couponSet = redisTemplate.opsForZSet().range(COUPON_NOTICE_KEY, 0, -1);
        //??????couponSet?????????????????????????????????score??????????????????????????????
        if (couponSet.size() > COUPON_NOTICE_NUM) {
            String remUserCouponStr = (String) couponSet.stream().findFirst().get();
            redisTemplate.opsForZSet().remove(COUPON_NOTICE_KEY, remUserCouponStr);
        }

    }

    /**
     * @description: ???redis??????coupon????????????N?????????       ???bug?????????map??????????????????duplicate key
     * @param:
     * @return: List<String>
     */
//    @Override
//    public List<CouponNoticeDto> queryCouponNotice(){
//        //set  userId_couponId                             ???score?????????
//        Set<String> couponSet = redisTemplate.opsForZSet().reverseRange(COUPON_NOTICE_KEY, 0, -1);
//
//        if(couponSet != null && couponSet.size() > 0){
//            //??????set?????????N?????????   userId_couponId
//            List<String> userCouponStrs = couponSet.stream().limit(COUPON_NOTICE_NUM).collect(Collectors.toList());
//            //map  couponId:userId
//            Map<String, String> couponUserMap = userCouponStrs.stream().collect(Collectors.toMap(o -> o.split("_")[1], o -> o.split("_")[0]));
//            //List   couponId
//            List<String> couponIds = userCouponStrs.stream().map(o -> o.split("_")[1]).collect(Collectors.toList());
//            //String   "couponId1,couponId2,couponId3"
//            String couponIdsStr = StringUtils.join(couponIds, ",");
//            //??????couponIdStrs????????????coupon????????????
//            List<TCoupon> coupons = getCouponListByIds(couponIdsStr);
//            //        List<TCoupon> ---> List<CouponNoticeDto>
//            List<CouponNoticeDto> dtos = coupons.stream().map(coupon -> {
//                CouponNoticeDto dto = new CouponNoticeDto();
//                BeanUtils.copyProperties(coupon, dto);
//                int userId = Integer.parseInt(couponUserMap.get(coupon.getId()+""));
//
//                dto.setUserId(userId);
//                UserVO user = getUserById(userId);
//                dto.setUserName(user.getUsername());
//                return dto;
//            }).collect(Collectors.toList());
//
//            logger.info("coupon notice data:{}",JSONObject.toJSON(dtos));
//            return dtos;
//        }
//
//        return null;
//
//    }


    /**
     * @description: ???redis??????coupon????????????N?????????
     * @param:
     * @return: List<String>
     */
    public List<CouponNoticeDto> queryCouponNotice() {
        //set  userId_couponId                             ???score?????????
//        Set<String> couponSet = redisTemplate.opsForZSet().reverseRange(COUPON_NOTICE_KEY, 0, -1);

        Set<ZSetOperations.TypedTuple<String>> couponSet = redisTemplate.opsForZSet().reverseRangeWithScores(COUPON_NOTICE_KEY, 0, -1);

        if (couponSet != null && couponSet.size() > 0) {
            //??????set?????????N?????????   userId_couponId + score
            List<ZSetOperations.TypedTuple<String>> userCouponStrs = couponSet.stream().limit(COUPON_NOTICE_NUM).collect(Collectors.toList());

            List<CouponNoticeDto> dtos = Lists.newArrayList();

            userCouponStrs.forEach(o -> {
                String couponStr = o.getValue();

                //score??????????????????????????????????????????????????????????????????
                double score = o.getScore();
                BigDecimal db = new BigDecimal(score);
                long longNum = Long.parseLong(db.toPlainString());
                Date saveTime = new Date(longNum);

                int userId = Integer.parseInt(couponStr.split("_")[0]);
                String couponId = couponStr.split("_")[1];

                UserVO user = getUserById(userId);
                TCoupon coupon = getCouponListByIds(couponId).get(0);

                CouponNoticeDto dto = new CouponNoticeDto();
                BeanUtils.copyProperties(coupon, dto);
                dto.setUserId(userId);
                dto.setUserName(user.getUsername());
                dto.setSaveTime(saveTime);

                dtos.add(dto);

            });

            return dtos;

        }

        return null;

    }


}