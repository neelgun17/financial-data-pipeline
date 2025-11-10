package com.mycompany.app;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.connector.kafka.sink.KafkaSink;


public class MarketAnalysisJob {

    public static void main(String[] args) throws Exception {
        // 1. Set up the Flink execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 2. Create a Kafka Source
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers("localhost:9092")
                .setTopics("raw_stock_ticks")
                .setGroupId("flink-market-analyzer")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();
        
        // 3. Create a Kafka Sink for the results
        // In MarketAnalysisJob.java\
        // KafkaSink<AnalyzedTrend> sink = KafkaSink.<AnalyzedTrend>builder()
        // .setBootstrapServers("localhost:9092")
        // .setRecordSerializer(new AnalyzedTrendSerializer("analyzed_market_trends"))
        // .build();
        // In MarketAnalysisJob.java

        // 3. Create a Kafka Sink for the results with an explicit producer config
// In MarketAnalysisJob.java

        // 3. Create a Kafka Sink for the results with an aggressive flush setting
        KafkaSink<AnalyzedTrend> sink = KafkaSink.<AnalyzedTrend>builder()
                .setBootstrapServers("localhost:9092")
                .setRecordSerializer(new AnalyzedTrendSerializer("analyzed_market_trends"))
                
                // Let's use a more direct property for local dev.
                // This tells the producer to send any buffered messages every 100ms
                // instead of waiting for a full batch.
                .setProperty("linger.ms", "100")
                
                .build();


        // 4. Create the DataStream from the source
        // We map the raw JSON strings to our StockTick POJO
        // DataStream<StockTick> ticks = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source")
        //         .map(jsonString -> {
        //             ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        //             return mapper.readValue(jsonString, StockTick.class);
        //         });
        // In MarketAnalysisJob.java

        // 4. Create the DataStream from the source
        DataStream<StockTick> ticks = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source")
            .map(jsonString -> {
                try {
                    // Parse JSON manually to handle timestamp format issues
                    org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode rootNode = 
                        new org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonString);
                    
                    StockTick tick = new StockTick();
                    tick.symbol = rootNode.get("symbol").asText();
                    tick.price = rootNode.get("price").asDouble();
                    tick.volume = rootNode.get("volume").asInt();
                    
                    // Parse timestamp - handle both UTC (Z) and timezone offset formats
                    String timestampStr = rootNode.get("timestamp").asText();
                    try {
                        // Try parsing as ISO-8601 with timezone offset (RFC3339)
                        if (timestampStr.endsWith("Z")) {
                            // UTC format
                            tick.timestamp = java.time.Instant.parse(timestampStr);
                        } else {
                            // Has timezone offset - parse as ZonedDateTime then convert to Instant
                            java.time.ZonedDateTime zdt = java.time.ZonedDateTime.parse(timestampStr);
                            tick.timestamp = zdt.toInstant();
                        }
                    } catch (Exception e) {
                        // Fallback: try parsing as Instant directly
                        tick.timestamp = java.time.Instant.parse(timestampStr);
                    }
                    
                    return tick;
                } catch (Exception e) {
                    // !!! THIS IS THE CRITICAL PART !!!
                    // If there is a parsing error, we log it clearly and return null.
                    System.err.println("Failed to parse JSON: " + jsonString);
                    e.printStackTrace(); // Print the full error details
                    return null; // Return null for bad records
                }
            })
            // Filter out the null records that we created from bad JSON.
            .filter(tick -> tick != null);
        

        // ticks.print();
        // 5. The core processing logic: key, window, and apply analysis
        DataStream<AnalyzedTrend> analysis = ticks
                // Key the stream by stock symbol. All ticks for "AAPL" go to one task, "GOOG" to another.
                .keyBy((KeySelector<StockTick, String>) tick -> tick.symbol)
                // Group ticks into 1-minute tumbling windows
                .window(TumblingProcessingTimeWindows.of(Time.minutes(1)))
                // Apply our custom analysis function to each window
                .apply(new TrendAnalysisFunction());
        // !!! THIS IS THE NEW DIAGNOSTIC LINE !!!
        // Print the results of the analysis function directly to the console.
        // Set parallelism to 1 to prevent interleaved output from multiple tasks.
        System.out.println("--- Wiretapping AnalyzedTrend Stream ---");
        analysis.print().setParallelism(1);
        // 6. Sink the results of the analysis back to Kafka
        analysis.sinkTo(sink);

        // 7. Execute the Flink job
        env.execute("Real-time Market Analysis");
    }

    // Custom WindowFunction to perform our analysis
    public static class TrendAnalysisFunction implements WindowFunction<StockTick, AnalyzedTrend, String, TimeWindow> {
        @Override
        public void apply(String symbol, TimeWindow window, Iterable<StockTick> input, Collector<AnalyzedTrend> out) {
            double sum = 0;
            double minPrice = Double.MAX_VALUE;
            double maxPrice = Double.MIN_VALUE;
            int count = 0;

            // Iterate over all ticks in the window
            for (StockTick tick : input) {
                sum += tick.price;
                if (tick.price < minPrice) minPrice = tick.price;
                if (tick.price > maxPrice) maxPrice = tick.price;
                count++;
            }

            if (count > 0) {
                AnalyzedTrend result = new AnalyzedTrend();
                result.symbol = symbol;
                result.averagePrice = sum / count;
                result.windowEndTimestamp = window.getEnd();

                // Simple anomaly detection: if price swings by more than 2% in the window
                double volatility = (maxPrice - minPrice) / minPrice;
                result.isVolatilityAnomaly = volatility > 0.02;

                out.collect(result); // Emit the result
            }
        }
    }
}