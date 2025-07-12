package com.mycompany.app;

import java.time.Instant;

// this is to match to the JSON structure set in go main file
public class StockTick {
    public String symbol;
    public double price;
    public int volume;
    public Instant timestamp;

    public StockTick() {}
    // ADD THIS METHOD
    @Override
    public String toString() {
        return "StockTick{" +
                "symbol='" + symbol + '\'' +
                ", price=" + price +
                ", volume=" + volume +
                ", timestamp=" + timestamp +
                '}';
    }
}

