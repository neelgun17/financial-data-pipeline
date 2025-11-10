// In ingestion-service/main.go

package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"maps"
	"math/rand"
	"os"
	"os/signal"
	"slices"
	"syscall"
	"time"

	"github.com/confluentinc/confluent-kafka-go/v2/kafka"
)

// StockTick struct and stockPrices map remain the same...
type StockTick struct {
	Symbol    string    `json:"symbol"`
	Price     float64   `json:"price"`
	Volume    int       `json:"volume"`
	Timestamp time.Time `json:"timestamp"`
}

var stockPrices = map[string]float64{
	"AAPL": 150.00,
	"GOOG": 2750.00,
	"MSFT": 300.00,
}

// generateFakeTick function remains the same...
func generateFakeTick(symbol string) StockTick {
	lastPrice := stockPrices[symbol]
	changePercent := (rand.Float64() - 0.5) * 0.02
	newPrice := lastPrice * (1 + changePercent)
	stockPrices[symbol] = newPrice

	return StockTick{
		Symbol:    symbol,
		Price:     newPrice,
		Volume:    rand.Intn(1000) + 100,
		Timestamp: time.Now().UTC(),
	}
}

func main() {
	fmt.Println("Ingestion Service starting...")
	topic := "raw_stock_ticks"

	// 1. Create a Kafka Producer Configuration
	p, err := kafka.NewProducer(&kafka.ConfigMap{
		// This is the address of the Kafka broker from our docker-compose.yml
		"bootstrap.servers": "localhost:9092",
	})
	if err != nil {
		log.Fatalf("Failed to create Kafka producer: %s", err)
	}
	defer p.Close() // Ensure the producer is closed when main() exits

	// 2. Start a goroutine to handle delivery reports
	// This is how we know if our messages were successfully sent.
	go func() {
		for e := range p.Events() {
			switch ev := e.(type) {
			case *kafka.Message:
				if ev.TopicPartition.Error != nil {
					log.Printf("Delivery failed: %v\n", ev.TopicPartition)
				} else {
					log.Printf("Delivered message to %v\n", ev.TopicPartition)
				}
			}
		}
	}()

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	go func() {
		<-ctx.Done()
		fmt.Print("Shutting down...")

		p.Flush(15_000)
		os.Exit(0)
	}()

	// 3. The main loop to produce messages
	symbols := slices.Collect(maps.Keys(stockPrices))
	for {
		select {
		case <-ctx.Done():
			return
		default:

			// Pick a random stock symbol
			symbol := symbols[rand.Intn(len(symbols))]

			// Generate the data
			tick := generateFakeTick(symbol)
			tickJSON, err := json.Marshal(tick)
			if err != nil {
				log.Printf("Failed to marshal tick to JSON: %s", err)
				continue
			}

			// 4. Produce the message to the Kafka topic
			// All messages with the same key are guaranteed
			// to go to the same partition, preserving their order.
			p.Produce(&kafka.Message{
				TopicPartition: kafka.TopicPartition{Topic: &topic, Partition: kafka.PartitionAny},
				Key:            []byte(tick.Symbol),
				Value:          tickJSON,
			}, nil)

			// Wait for a second before producing the next message
			time.Sleep(100 * time.Millisecond)
		}
	}
}

// changes to add implement pulling data from API
// split high volume index funds to have even distribution among partitions
