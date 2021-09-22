package com.tomorrowcat.couponapp;

import com.tomorrowcat.couponapp.service.dubbo.CouponServiceImpl;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @description: jmh基准测试，测试springboot接口
 * @author: kim
 * @create: 2021-07-27 20:13
 * @version: 1.0.0
 */

@State(Scope.Thread)
public class JMHSpringbootTest {

    private ConfigurableApplicationContext context;
    private CouponServiceImpl couponService;

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder().include(
                JMHSpringbootTest.class.getName() + ".*")
                //预热4次，测量4次,2个线程
                .warmupIterations(2)
                .measurementIterations(2)
                .forks(1).build();
        new Runner(options).run();
    }

    /**
     * 基准测试开始前执行     启动容器，创建bean
     */
    @Setup(Level.Trial)   //测试级别
    public void init(){
        String arg = "";
        context = SpringApplication.run(CouponAppApplication.class,arg);
        couponService = context.getBean(CouponServiceImpl.class);
    }

    /**
     * @description: 基准测试完成后执行此方法
     * @param: 
     * @return: void
     */
    @TearDown
    public void finish(){
        context.close();
    }


    //-------------------------------------------------
    /**
     * benchmark可执行多次，此注解代表触发我们所要进行基准测试的方法
     * 测试优惠券列表接口性能    从guava cache中取数据
     */
    @Benchmark
    public void testGetCouponList4Cache(){
        System.out.println(couponService.getCouponListGuava());
    }

    /**
     * 测试优惠券列表接口性能    从caffeine中取数据
     */
    @Benchmark
    public void testGetCouponList4Caffeine(){
        System.out.println(couponService.getCouponList());
    }


    /**
     * @description:  从concurrentHashMap中取数据
     * @param: 
     * @return: void
     */
    @Benchmark
    public void testGetCouponList4Map(){
        System.out.println(couponService.getCouponList4Map());
    }


    /**
     * @description:  从db中取数据
     * @param:
     * @return: void
     */
    @Benchmark
    public void testGetCouponList4DB(){
        System.out.println(couponService.loadCoupon(1));
    }


}