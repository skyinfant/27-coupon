package com.tomorrowcat.couponapp.controller;

import com.tomorrowcat.couponapp.service.dubbo.CouponServiceImpl;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
public class CouponController {

    @Resource
    private CouponServiceImpl couponService;


    /**
     * @description: 根据券码查询优惠券
     * @param:
     * @return: Object
     */
    @RequestMapping("getByCode")
    public Object testQuery(){
        return couponService.query();

    }

    /**
     * @description: 根据id获取user
     * @param: id
     * @return: Object
     */
    @RequestMapping("getUser")
    public Object getUserById(int id){
        return couponService.getUserById(id);
    }


    /**
     * @description: 测试负载均衡
     * @param: 
     * @return: Object
     */
    @RequestMapping("getUser2")
    public Object getUser2(){
        return couponService.loadUser(1);
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
