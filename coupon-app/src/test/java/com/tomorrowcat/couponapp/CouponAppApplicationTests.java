package com.tomorrowcat.couponapp;


import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.tomorrowcat.couponapp.domain.TCoupon;
import com.tomorrowcat.couponapp.domain.TCouponExample;
import com.tomorrowcat.couponapp.mapper.TCouponMapper;
import com.tomorrowcat.couponapp.service.dubbo.CouponServiceImpl;
import com.tomorrowcat.couponserviceapi.dto.CouponDto;
import com.tomorrowcat.couponserviceapi.dto.UserCouponDto;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;


import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = CouponAppApplication.class)
public class CouponAppApplicationTests {

    @Resource
    private TCouponMapper couponMapper;

    @Resource
    private CouponServiceImpl couponService;

    @Resource
    private RedisTemplate redisTemplate;

    @Test
    public void testInsert() {
        for (int i = 0; i <10 ; i++) {
            TCoupon tCoupon = new TCoupon();
            tCoupon.setAchieveAmount(888);
            tCoupon.setReduceAmount(500);
            tCoupon.setCreateTime(new Date());
            tCoupon.setCode(UUID.randomUUID().toString());
            tCoupon.setPicUrl("1.jpg");
            tCoupon.setStatus(0);
            tCoupon.setStock(38L);
            tCoupon.setTitle("测试coupon");
            tCoupon.setStartTime(new Date());
            tCoupon.setEndTime(new Date());
            couponMapper.insert(tCoupon);
        }



    }

    @Test
    public void testDelete() {
        couponMapper.deleteByPrimaryKey(1);
    }

    @Test
    public void testUpdate() {
        TCoupon tCoupon = new TCoupon();
        tCoupon.setId(2);
        tCoupon.setAchieveAmount(8888);
        tCoupon.setReduceAmount(20);
//        tCoupon.setCreateTime(new Date());
//        tCoupon.setCode(UUID.randomUUID().toString());
//        tCoupon.setPicUrl("333.jpg");
//        tCoupon.setStatus(0);
//        tCoupon.setStock(100);
//        tCoupon.setTitle("测试coupon");
        couponMapper.updateByPrimaryKeySelective(tCoupon);
//        couponMapper.updateByPrimaryKey(tCoupon);

    }

    @Test
    public void testSelect() {

        TCouponExample example = new TCouponExample();
        example.createCriteria().andCodeEqualTo("0055d3d2-bc05-4b2b-be91-cdae68d7de38").andStatusEqualTo(0)
                .andAchieveAmountBetween(100, 500).andTitleNotLike("aaaa");
        List<TCoupon> list = couponMapper.selectByExample(example);
        System.out.println(list);


    }

    /***
     * 获取有效时间内的可用优惠券列表
     * @return
     */
    @Test
    public void testGetCouponList(){
        List<CouponDto> couponList = couponService.getCouponList();
        System.out.println(couponList);

    }


    /**
     * @description: 测试用户领券
     * @param:
     * @return: void
     */
    @Test
    public void testSaveUserCoupon(){
        UserCouponDto dto = new UserCouponDto();
        for (int i = 1; i <11 ; i++) {
            dto.setUserId(1);
            dto.setOrderId(1);
            dto.setCouponId(i);
            System.out.println(couponService.saveUserCoupon(dto));

        }



    }


    /**
     * @description:  测试用户优惠券列表
     * @param:
     * @return: void
     */
    @Test
    public void testGetUserCouponList(){
        System.out.println(JSONObject.toJSON(couponService.userCouponList(1)));
    }


    /**
     * @description:  测试redis
     * @param:
     * @return: void
     */
    @Test
    public void testRedis(){
//        System.out.println(redisTemplate.opsForValue().get("name"));
//        redisTemplate.opsForZSet().add("tony", "aa", 90);
//        redisTemplate.opsForZSet().add("tony", "bb", 10);
//        redisTemplate.opsForZSet().add("tony", "cc", 65);
//        redisTemplate.opsForZSet().add("tony", "dd", 77);
        redisTemplate.opsForZSet().remove("tony","aa");
        System.out.println(redisTemplate.opsForZSet().range("tony", 0, -1));

    }



    /**
     * @description: 测试增加和删除coupon公告栏数据
     * @param: 
     * @return: void
     */
    @Test
    public void testupdateCouponForNews(){
        for (int i = 1; i <11 ; i++) {
            couponService.updateCouponNotice(1+"_"+i);
        }



    }

    /**
     * @description:  测试 从redis查询coupon公告栏前N条数据
     * @param:
     * @return: void
     */
    @Test
    public void testqueryCouponListForNews(){
        System.out.println(JSONObject.toJSON(couponService.queryCouponNotice()));
    }






}





















