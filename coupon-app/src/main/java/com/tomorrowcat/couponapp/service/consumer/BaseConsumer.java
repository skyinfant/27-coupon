package com.tomorrowcat.couponapp.service.consumer;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @description: 消费者基类
 * @author: kim
 * @create: 2021-08-02 21:36
 * @version: 1.0.0
 */

@Configuration
public abstract class BaseConsumer {

    private static final Logger logger = LoggerFactory.getLogger(BaseConsumer.class);

    /**
     * @description:  消费消息
     * @param: topic
     * @param: tag
     * @return: void
     */
    public void consume(String groupName,String namesrvAddr,String topic,String tag) throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(groupName);
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.subscribe(topic, tag);
        //为消费者添加监听器
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            //收到消息就调用这个方法    ConsumeConcurrentlyStatus是消费结果
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                //消费消息的逻辑
                return dealBody(msgs);
            }
        });

        consumer.start();
        logger.info("rocketmq启动成功-----------------------");

    }

    public abstract  ConsumeConcurrentlyStatus dealBody(List<MessageExt> msgs);
}