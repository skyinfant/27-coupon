package com.tomorrowcat.couponserviceapi.service;

import com.tomorrowcat.couponserviceapi.dto.CouponDto;
import com.tomorrowcat.couponserviceapi.dto.CouponNoticeDto;
import com.tomorrowcat.couponserviceapi.dto.UserCouponDto;
import com.tomorrowcat.couponserviceapi.dto.UserCouponInfoDto;

import java.util.List;

/**
 * @description:
 * @author: kim
 * @create: 2021-07-30 17:45
 * @version: 1.0.0
 */
public interface CouponService {
    /**
     * @description:  优惠券列表
     * @param:
     * @return: List<CouponDto>
     */
    List<CouponDto> getCouponList();

    /**
     * @description:  用户领券
     * @param: userCouponDto
     * @return: String
     */
    String saveUserCoupon(UserCouponDto userCouponDto);

    /**
     * @description:  根据用户id，获取该用户拥有的优惠券
     * @param: userId
     * @return: List<UserCouponInfoDto>
     */
    List<UserCouponInfoDto> userCouponList(Integer userId);


    /**
     * @description: 从redis查询coupon公告栏前N条数据
     * @param:
     * @return: List<String>
     */
    List<CouponNoticeDto> queryCouponNotice();

}