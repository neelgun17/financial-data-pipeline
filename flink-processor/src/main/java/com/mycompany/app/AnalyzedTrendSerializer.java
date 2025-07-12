package com.mycompany.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.kafka.clients.producer.ProducerRecord;
import javax.annotation.Nullable;

public class AnalyzedTrendSerializer implements KafkaRecordSerializationSchema<AnalyzedTrend> {

    private static final long serialVersionUID = 1L;
    private final String topic;
    // Create the ObjectMapper once and reuse it - it's thread-safe
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public AnalyzedTrendSerializer(String topic) {
        this.topic = topic;
    }

    @Override
    public ProducerRecord<byte[], byte[]> serialize(
            AnalyzedTrend element, KafkaSinkContext context, Long timestamp) {
        try {
            // The key can be null. The value is our object serialized to a JSON byte array.
            return new ProducerRecord<>(topic, null, objectMapper.writeValueAsBytes(element));
        } catch (Exception e) {
            // It's good practice to wrap the exception in a RuntimeException
            throw new RuntimeException("Failed to serialize AnalyzedTrend", e);
        }
    }
}