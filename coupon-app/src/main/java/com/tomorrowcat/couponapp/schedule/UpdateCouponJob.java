package com.tomorrowcat.couponapp.schedule;

import com.tomorrowcat.couponapp.service.dubbo.CouponServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @description:
 * @author: kim
 * @create: 2021-07-28 21:31
 * @version: 1.0.0
 */
//@Service
public class UpdateCouponJob {

    private static final Logger logger = LoggerFactory.getLogger(UpdateCouponJob.class);


    @Resource
    private CouponServiceImpl couponService;

    @Scheduled(cron = "0/5 * * * * *")
    public void setCouponService(){
//        System.out.println("enter update coupon job");
        logger.info("enter update coupon job");
        couponService.updateCouponMap();
    }
}