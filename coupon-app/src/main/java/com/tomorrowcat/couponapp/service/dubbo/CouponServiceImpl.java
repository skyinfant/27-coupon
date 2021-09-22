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

    @Reference   //这个接口来自 user-service-api
    private UserService userServce;

    @Resource
    private TUserCouponMapper userCouponMapper;

    @Resource
    private RedisTemplate redisTemplate;

    private static final Logger logger = LoggerFactory.getLogger(CouponServiceImpl.class);

    private static final String COUPON_NOTICE_KEY = "couponSet";

    private static final int COUPON_NOTICE_NUM = 5;


    /**
     * @description: 把当前有效的coupon列表数据load到缓存
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


    //创建coupon缓存   guva cache
    LoadingCache<Integer, List<TCoupon>> couponCache = CacheBuilder.newBuilder()
            //过期时间
            .expireAfterWrite(10, TimeUnit.MINUTES)
            //刷新时间
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .build(new CacheLoader<Integer, List<TCoupon>>() {
                @Override
                public List<TCoupon> load(Integer key) throws Exception {
                    return loadCoupon(key);
                }
            });


    //创建coupon缓存  caffeine
    com.github.benmanes.caffeine.cache.LoadingCache<Integer, List<TCoupon>> couponCaffeine = Caffeine.newBuilder()
            //过期时间
            .expireAfterWrite(10, TimeUnit.MINUTES)
            //刷新时间
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .build(new com.github.benmanes.caffeine.cache.CacheLoader<Integer, List<TCoupon>>() {
                @Override
                public List<TCoupon> load(Integer key) throws Exception {
                    return loadCoupon(key);
                }
            });


    /***
     * 获取有效时间内的可用优惠券列表    caffeine   比guava性能更好，推荐
     * @return
     */
    public List<CouponDto> getCouponList() {
        List<TCoupon> coupons = Lists.newArrayList();
        List<CouponDto> dtos = Lists.newArrayList();

        try {
            //这个缓存key没有意义，随便用一个数字都可以获取
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
     * @description: 用户领券
     * @param: userCouponDto
     * @return: String
     */
    @Override
    public String saveUserCoupon(UserCouponDto dto) {
//        System.out.println("进入领券方法。。。");
        String result = check(dto);
        if (result != null) {
            return result;
        }
        TCoupon coupon = couponMapper.selectByPrimaryKey(dto.getCouponId());
        if (coupon == null) {
            return "coupon 无效";
        }

        boolean isDuplicate = isDuplicateUserCoupon(dto.getUserId(), coupon.getId());
        if (isDuplicate) {
            return "该券您已经领取过了";
        }

        return save2DB(dto, coupon);

    }

    /**
     * @description: 检查某张券是否已经领取过，同一个用户同一张优惠券只能领取一次
     * @param: userId
     * @return: int
     */
    private boolean isDuplicateUserCoupon(int userId, int couponId) {
        //查出用户未使用的券
        TUserCouponExample example = new TUserCouponExample();
        example.createCriteria().andUserIdEqualTo(userId)
                .andCouponIdEqualTo(couponId)
                .andStatusEqualTo(0);
        List<TUserCoupon> userCoupons = userCouponMapper.selectByExample(example);

        return userCoupons.size() > 0 ? true : false;
    }


    /**
     * @description: 根据用户id，获取该用户拥有的优惠券
     * @param: userId
     * @return: List<UserCouponInfoDto>
     */
    @Override
    public List<UserCouponInfoDto> userCouponList(Integer userId) {
        List<UserCouponInfoDto> dtos = Lists.newArrayList();
        if (userId == null) {
            return dtos;
        }
        //获取此用户的UserCoupon
        List<TUserCoupon> userCoupons = getUserCoupons(userId);
        if (CollectionUtils.isEmpty(userCoupons)) {
            return dtos;
        }

        //获取 Map<couponId,TCoupon>
        Map<Integer, TCoupon> couponMap = getCouponMap(userCoupons);

        //封装coupon   返回1个List<UserCouponInfoDto>
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
     * 获取couponIds          List<TUserCoupon> ---> Set<CouponId>
     */
    private Set<Integer> getCouponIds(List<TUserCoupon> userCoupons) {
        Set<Integer> couponIds = userCoupons.stream().map(userCoupon -> userCoupon.getCouponId()).collect(Collectors.toSet());
        return couponIds;


    }

    /**
     * @description: 从数据库获查询用户的优惠券
     * @param: userId
     * @return: List<TUserCoupon>
     */
    private List<TUserCoupon> getUserCoupons(Integer userId) {
        //查出用户未使用的券
        TUserCouponExample example = new TUserCouponExample();
        example.createCriteria().andUserIdEqualTo(userId)
                .andStatusEqualTo(0);
        List<TUserCoupon> userCoupons = userCouponMapper.selectByExample(example);
        return userCoupons;
    }


    /**
     * @description: 把用户领取的优惠券入库
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

        //userCoupon表的code和coupon的code是不一样的，前者由雪花算法生成，保证唯一性
        userCoupon.setUserCouponCode(worker.nextId() + "");

        userCouponMapper.insertSelective(userCoupon);
        logger.info("save coupon sccess:{}", JSON.toJSONString(userCoupon));
        return "领取成功";

    }

    public String check(UserCouponDto dto) {
        Integer couponId = dto.getCouponId();
        Integer userId = dto.getUserId();
        if (couponId == null || userId == null) {
            return "couponId或者userId为空";
        }
        return null;
    }


    /***
     * 获取有效时间内的可用优惠券列表  guava cache
     * @return
     */
    public List<TCoupon> getCouponListGuava() {
        List<TCoupon> coupons = Lists.newArrayList();
        try {
            //这个缓存key没有意义，随便用一个数字都可以获取
            coupons = couponCache.get(1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return coupons;

    }


    //创建缓存，以单个couponId为key，一个key对应一张coupon
    LoadingCache<Integer, TCoupon> couponIdsCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .build(new CacheLoader<Integer, TCoupon>() {
                @Override
                public TCoupon load(Integer id) throws Exception {
                    return loadIdsCoupon(id);
                }
            });

    //根据id获取coupon加载到缓存
    private TCoupon loadIdsCoupon(Integer id) {
        return couponMapper.selectByPrimaryKey(id);
    }

    /**
     * @description: 批量获取coupon
     * @param: ids   多个couponId   1,2,3
     * @return: List<TCoupon>
     */
    public List<TCoupon> getCouponListByIds(String ids) {
        //couponId数组
        String[] idStr = ids.split(",");
        //存放缓存中没有的coupon的id
        List<Integer> loadFromDB = Lists.newArrayList();
        //存放方法返回的所有coupon
        List<TCoupon> coupons = Lists.newArrayList();
        //couponID List
        List<String> idList = Lists.newArrayList(idStr);
        for (String id : idList) {
            //缓存有就返回，没有就返回null，不会阻塞
            TCoupon coupon = couponIdsCache.getIfPresent(Integer.parseInt(id));
            if (coupon == null) {
                //从数据库获取
                loadFromDB.add(Integer.parseInt(id));
            } else {
                coupons.add(coupon);
            }

        }

        //从数据库获取缓存中没有的coupon
        List<TCoupon> coupons1 = getCouponByIds(loadFromDB);
        //jdk8写法
        Map<Integer, TCoupon> couponMap = coupons1.stream().collect(Collectors.toMap(TCoupon::getId, TCoupon -> TCoupon));

        //传统写法
        //        Map<Integer, TCoupon> tCouponMap = new HashMap<>();
//        for (com.xdclass.couponapp.domain.TCoupon TCoupon : tCoupons1) {
//            //如果插入的 key 对应的 value 已经存在，则执行 value 替换操作，返回旧的 value 值，如果不存在则执行插入，返回 null
//            //                 TCoupon::getId, TCoupon -> TCoupon
//            if (tCouponMap.put(TCoupon.getId(), TCoupon) != null) {
//                throw new IllegalStateException("Duplicate key");
//            }
//        }


        coupons.addAll(coupons1);
        //将返回结果回写到缓存里面
        couponIdsCache.putAll(couponMap);

        return coupons;

    }

    /**
     * @description: 从数据库获取缓存中没有的coupon
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


    //用户缓存
    LoadingCache<Integer, UserVO> userCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(1000)   //最大容量10000个key
            .build(new CacheLoader<Integer, UserVO>() {
                @Override
                public UserVO load(Integer userId) throws Exception {
                    return loadUser(userId);
                }
            });

    //把用户数据load到缓存
    public UserVO loadUser(Integer userId) {
        return userServce.getUserById(userId);
    }


    /**
     * @description: 根据券码查询优惠券
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
     * @description: 根据id查询用户信息
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
     * @description: 容器启动即加载coupon数据到缓存
     * @param:
     * @return: void
     */
    @PostConstruct
    void loadData() throws ExecutionException {

        couponCache.get(1);
    }

    //================================使用concurrentHashMap实现coupon列表缓存（guava cache底层也是使用concurrentHashMap）
    private Map couponMap = new ConcurrentHashMap();

    //更新concurrentHashMap
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

    //从ConcurrentHashMap中获取coupon列表
    public List<TCoupon> getCouponList4Map() {
        return (List<TCoupon>) couponMap.get(1);
    }

    //=============================================================================

    /**
     * 用户下单后，绑定coupon与订单的关系
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
            logger.warn("此 coupon 不存在  userCouponCode={}", couponCode);
            return;
        }
        TUserCoupon userCoupon = tUserCoupons.get(0);

        userCoupon.setStatus(0);  //0--未核销
        userCoupon.setOrderId(orderId);
        userCouponMapper.updateByPrimaryKeySelective(userCoupon);

    }

    /**
     * 用户支付后，更新coupon为已核销,并且更新coupon公告栏
     *
     * @param orderId
     * @param couponCode
     */
    public void updateCouponStatusAfterPay(int orderId, int userId) {
        List<TUserCoupon> tUserCoupons = getUserrCouponByUserIdAndOrderId(userId, orderId);
        if (CollectionUtils.isEmpty(tUserCoupons)) {
            logger.warn("这笔订单不存在 userId={}  orderId={}", userId, orderId);
            return;
        }
        TUserCoupon userCoupon = tUserCoupons.get(0);

        //核销优惠券
        userCoupon.setStatus(1); //1--已核销
        userCouponMapper.updateByPrimaryKeySelective(userCoupon);

        //更新coupon公告栏
        int couponId = userCoupon.getCouponId();

        String userCouonStr = userId + "_" + couponId;
        updateCouponNotice(userCouonStr);

    }


    /**
     * @description: 取消订单后，要解除优惠券和订单的关系，并初始化优惠券的使用状态
     * @param: userId
     * @param: orderId
     * @return: void
     */
    public void updateCouponStatusAfterCancelOrder(Integer userId, Integer orderId) {
        List<TUserCoupon> tUserCoupons = getUserrCouponByUserIdAndOrderId(userId, orderId);
        if (CollectionUtils.isEmpty(tUserCoupons)) {
            logger.warn("这笔订单不存在 userId={}  orderId={}", userId, orderId);
            return;
        }
        TUserCoupon userCoupon = tUserCoupons.get(0);
        userCoupon.setOrderId(0); //解绑优惠券
        userCoupon.setStatus(0); //改为未核销
        userCouponMapper.updateByPrimaryKeySelective(userCoupon);


    }


    /**
     * @description: 根据userId和orderId查询用户优惠券
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
     * @description: 用户核销coupon后，把coupon信息保存到redis，同时删除redis中多余的coupon
     * 接收coupon优惠券核销mq的时候被调用,以时间窗口展示前N条数据,userCouponStr代表userId_couponId
     * @param: userCouonStr
     * @return: void
     */
    public void updateCouponNotice(String userCouonStr) {
        //把 userId_CouponId 存入redis的sortedSet
        redisTemplate.opsForZSet().add(COUPON_NOTICE_KEY, userCouonStr, System.currentTimeMillis());
        //按score升序排，取出sortedSet中全部数据
        Set couponSet = redisTemplate.opsForZSet().range(COUPON_NOTICE_KEY, 0, -1);
        //如果couponSet中的数据超额了，则删除score最小，即是第一条数据
        if (couponSet.size() > COUPON_NOTICE_NUM) {
            String remUserCouponStr = (String) couponSet.stream().findFirst().get();
            redisTemplate.opsForZSet().remove(COUPON_NOTICE_KEY, remUserCouponStr);
        }

    }

    /**
     * @description: 从redis查询coupon公告栏前N条数据       有bug，转为map那里可能会报duplicate key
     * @param:
     * @return: List<String>
     */
//    @Override
//    public List<CouponNoticeDto> queryCouponNotice(){
//        //set  userId_couponId                             按score倒序排
//        Set<String> couponSet = redisTemplate.opsForZSet().reverseRange(COUPON_NOTICE_KEY, 0, -1);
//
//        if(couponSet != null && couponSet.size() > 0){
//            //获取set里面前N条数据   userId_couponId
//            List<String> userCouponStrs = couponSet.stream().limit(COUPON_NOTICE_NUM).collect(Collectors.toList());
//            //map  couponId:userId
//            Map<String, String> couponUserMap = userCouponStrs.stream().collect(Collectors.toMap(o -> o.split("_")[1], o -> o.split("_")[0]));
//            //List   couponId
//            List<String> couponIds = userCouponStrs.stream().map(o -> o.split("_")[1]).collect(Collectors.toList());
//            //String   "couponId1,couponId2,couponId3"
//            String couponIdsStr = StringUtils.join(couponIds, ",");
//            //通过couponIdStrs批量获取coupon缓存数据
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
     * @description: 从redis查询coupon公告栏前N条数据
     * @param:
     * @return: List<String>
     */
    public List<CouponNoticeDto> queryCouponNotice() {
        //set  userId_couponId                             按score倒序排
//        Set<String> couponSet = redisTemplate.opsForZSet().reverseRange(COUPON_NOTICE_KEY, 0, -1);

        Set<ZSetOperations.TypedTuple<String>> couponSet = redisTemplate.opsForZSet().reverseRangeWithScores(COUPON_NOTICE_KEY, 0, -1);

        if (couponSet != null && couponSet.size() > 0) {
            //获取set里面前N条数据   userId_couponId + score
            List<ZSetOperations.TypedTuple<String>> userCouponStrs = couponSet.stream().limit(COUPON_NOTICE_NUM).collect(Collectors.toList());

            List<CouponNoticeDto> dtos = Lists.newArrayList();

            userCouponStrs.forEach(o -> {
                String couponStr = o.getValue();

                //score值，即是存入该条数据的时间戳，科学计数法表示
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