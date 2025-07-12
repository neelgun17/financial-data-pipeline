// file for the output of the analyzed trends
package com.mycompany.app;

// This will be the output of our analysis
public class AnalyzedTrend {
    public String symbol;
    public double averagePrice;
    public boolean isVolatilityAnomaly;
    public long windowEndTimestamp;

    public AnalyzedTrend() {}
    // In flink-processor/src/main/java/com/mycompany/app/AnalyzedTrend.java
    @Override
    public String toString() {
        return "AnalyzedTrend{" +
                "symbol='" + symbol + '\'' +
                ", averagePrice=" + averagePrice +
                ", isVolatilityAnomaly=" + isVolatilityAnomaly +
                ", windowEndTimestamp=" + windowEndTimestamp +
                '}';
    }
}