package com.tomorrowcat.couponapp;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * @description: jmh测试。这里是简单测试，不需要spring参与
 * @author: kim
 * @create: 2021-07-27 18:55
 * @version: 1.0.0
 */
public class JMHHelloworld {

    public static void main(String[] args) throws RunnerException {
        //预热2次，测试2次,1个线程
        Options options = new OptionsBuilder().warmupIterations(2)
                .measurementIterations(2)
                .forks(1).build();
        new Runner(options).run();

    }

    @Benchmark
    public void testStringAdd(){
        String s = "";
        for (int i = 0; i < 10; i++) {
            s +=i;
        }
    }

    @Benchmark
    public void testStringBuild(){
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            b.append(i);
        }
        b.toString();
    }
}