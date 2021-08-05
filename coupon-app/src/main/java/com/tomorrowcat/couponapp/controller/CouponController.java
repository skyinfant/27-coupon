package com.tomorrowcat.couponapp.controller;

import com.tomorrowcat.couponapp.service.dubbo.CouponServiceImpl;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("api/v1/coupon")
public class CouponController {

    @Resource
    private CouponServiceImpl couponService;



    @RequestMapping("query")
    public Object testQuery(){
        return couponService.query();

    }

    @RequestMapping("getUser")
    public Object getUserById(int id){
        return couponService.getUserById(id);
    }

    /**
     * @description: 获取优惠券列表
     * @param: 
     * @return: Object
     */
    @RequestMapping("list")
    public Object getCouponList(){
        return couponService.getCouponList();
    }

    /**
     * @description: 批量获取优惠券
     * @param: ids
     * @return: Object
     */
    @RequestMapping("listByIds")
    public Object getCouponsByIds(String ids){
        return couponService.getCouponListByIds(ids);
    }


}
