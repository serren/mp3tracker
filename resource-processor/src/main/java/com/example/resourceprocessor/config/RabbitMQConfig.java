package com.example.resourceprocessor.config;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.queue}")
    private String queue;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    @Value("${rabbitmq.dead-letter-exchange}")
    private String deadLetterExchange;

    @Value("${rabbitmq.dead-letter-queue}")
    private String deadLetterQueue;

    @Value("${retry.max-attempts}")
    private int retryMaxAttempts;

    @Value("${retry.initial-interval}")
    private long retryInitialInterval;

    @Value("${retry.multiplier}")
    private double retryMultiplier;

    @Value("${retry.max-delay}")
    private long retryMaxDelay;

    @Bean
    public DirectExchange resourcesExchange() {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    public Queue resourcesQueue() {
        return new Queue(queue, true);
    }

    @Bean
    public Binding resourcesBinding(Queue resourcesQueue, DirectExchange resourcesExchange) {
        return BindingBuilder.bind(resourcesQueue).to(resourcesExchange).with(routingKey);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(deadLetterExchange, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return new Queue(deadLetterQueue, true);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(this.deadLetterQueue);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setTypePrecedence(DefaultJackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, RabbitTemplate rabbitTemplate) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());

        RepublishMessageRecoverer recoverer = new RepublishMessageRecoverer(
                rabbitTemplate, deadLetterExchange, deadLetterQueue);
        Advice retryInterceptor = RetryInterceptorBuilder.stateless()
                .maxAttempts(retryMaxAttempts)
                .backOffOptions(retryInitialInterval, retryMultiplier, retryMaxDelay)
                .recoverer(recoverer)
                .build();
        factory.setAdviceChain(retryInterceptor);
        return factory;
    }
}
