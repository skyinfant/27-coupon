package com.tomorrowcat.couponserviceapi.dto;

import java.io.Serializable;
import java.util.Date;
//rpc是以二进制形式传输数据的，所以要实现序列化接口
public class UserCouponDto implements Serializable {


    private Integer couponId;

    private Integer userId;

    private Integer orderId;

    //这个是雪花算法生成的，和coupon表的code是不一样的
    private String userCouponCode;

    public String getUserCouponCode() {
        return userCouponCode;
    }

    public void setUserCouponCode(String userCouponCode) {
        this.userCouponCode = userCouponCode;
    }

    @Override
    public String toString() {
        return "UserCouponDto{" +
                "couponId=" + couponId +
                ", userId=" + userId +
                ", orderId=" + orderId +
                '}';
    }

    public Integer getCouponId() {
        return couponId;
    }

    public void setCouponId(Integer couponId) {
        this.couponId = couponId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

}