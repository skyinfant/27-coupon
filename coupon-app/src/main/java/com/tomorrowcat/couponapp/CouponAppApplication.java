package com.tomorrowcat.couponapp;

import org.apache.dubbo.config.spring.context.annotation.DubboComponentScan;
import org.apache.dubbo.config.spring.context.annotation.EnableDubboConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.tomorrowcat.couponapp.mapper")
@EnableDubboConfig
//dubbo接口的实现类           扫描user和coupon的dubbo服务
@DubboComponentScan({"com.tomorrowcat.userapp.service.dubbo","com.tomorrowcat.couponapp.service.dubbo"})
@EnableScheduling
public class CouponAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(CouponAppApplication.class, args);

        //JVM优雅关闭   7.5
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("执行JVM ShutdownHook！！");
            }
        }));
    }

}
