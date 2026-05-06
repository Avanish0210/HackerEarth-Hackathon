package com.HackerEarth.Hackathon.ubidbridge.config;

import com.HackerEarth.Hackathon.ubidbridge.dto.PropagationTask;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {
    // Fix 1: correct import — org.springframework.beans.factory.annotation.Value
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ── Topics ────────────────────────────────────────────────────────────────

    @Bean
    public NewTopic propagationTasksTopic() {
        return TopicBuilder.name("ubid-propagation-tasks")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic propagationRetryTopic() {
        return TopicBuilder.name("ubid-propagation-retry")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic propagationDlqTopic() {
        return TopicBuilder.name("ubid-propagation-dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic conflictEventsTopic() {
        return TopicBuilder.name("ubid-conflict-events")
                .partitions(1)
                .replicas(1)
                .build();
    }

    // ── Producer ──────────────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, PropagationTask> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,      bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,     true);
        config.put(ProducerConfig.ACKS_CONFIG,                   "all");
        config.put(ProducerConfig.RETRIES_CONFIG,                3);
        // Don't embed type headers — keeps messages clean
        config.put(JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS,         false);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, PropagationTask> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ── Consumer ──────────────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, PropagationTask> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG,           "ubid-bridge-group");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);

        // Fix 2: Spring Kafka 4.0 way — configure via properties, not deprecated constructor
        config.put(JacksonJsonDeserializer.TRUSTED_PACKAGES,      "*");
        config.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE,
                "com.HackerEarth.Hackathon.ubidbridge.dto.PropagationTask");
        config.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PropagationTask>
    kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PropagationTask> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // Manual ack — we commit offset only after successful write + audit
        factory.getContainerProperties().setAckMode(
                ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(3);
        return factory;
    }
}
