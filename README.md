# Financial Data Pipeline

A real-time financial data processing pipeline that ingests high-frequency market data, processes it using Apache Flink for trend analysis, and serves results via REST APIs and WebSocket feeds. The system is designed to handle 10,000+ events per second with high availability.

## Architecture

The pipeline consists of three main components:

1. **Ingestion Service** (Go): Simulates real-time market data ingestion and publishes to Kafka
2. **Flink Processor** (Java): Performs stateful stream processing with 1-minute windows for trend analysis and anomaly detection
3. **Kafka**: Message broker that decouples components and ensures data durability

```
External Data Source → Ingestion Service → Kafka → Flink Processor → Kafka → API Service → Consumers
```

## Prerequisites

Before starting, ensure you have the following installed:

- **Docker** and **Docker Compose** (for Kafka infrastructure)
- **Java 8+** (JDK)
- **Maven 3.6+**
- **Go 1.24+**

Verify installations:
```bash
docker --version
docker-compose --version
java -version
mvn -version
go version
```

## Setup Instructions

### Step 1: Start Kafka Infrastructure

Start Zookeeper and Kafka using Docker Compose:

```bash
# Navigate to project root
cd /path/to/findata

# Start Kafka and Zookeeper
docker-compose up -d
```

Wait 10-15 seconds for Kafka to fully start, then verify:

```bash
# Check if containers are running
docker ps

# You should see both zookeeper and kafka containers
```

### Step 2: Create Kafka Topics

Create the required Kafka topics. If topics already exist, you can either delete them first or skip this step:

```bash
# Option 1: Delete existing topics and recreate (if you want a fresh start)
docker exec kafka kafka-topics --delete --bootstrap-server localhost:9092 --topic raw_stock_ticks 2>/dev/null || true
docker exec kafka kafka-topics --delete --bootstrap-server localhost:9092 --topic analyzed_market_trends 2>/dev/null || true

# Create the raw_stock_ticks topic (will error if it already exists, which is safe to ignore)
docker exec kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic raw_stock_ticks \
  --partitions 3 \
  --replication-factor 1 \
  2>/dev/null || echo "Topic raw_stock_ticks already exists, skipping..."

# Create the analyzed_market_trends topic (will error if it already exists, which is safe to ignore)
docker exec kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic analyzed_market_trends \
  --partitions 3 \
  --replication-factor 1 \
  2>/dev/null || echo "Topic analyzed_market_trends already exists, skipping..."

# Verify topics exist
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

### Step 3: Build the Flink Processor

Build the Java Flink job:

```bash
# Navigate to flink-processor directory
cd flink-processor

# Build the project (creates a JAR file)
mvn clean package

# This will create: target/flink-processor-0.1.jar
```

### Step 4: Set Up Go Dependencies

Install Go dependencies for the ingestion service:

```bash
# Navigate to ingestion-service directory
cd ../ingestion-service

# Download Go dependencies
go mod download

# Build the service (optional, but recommended)
go build -o ingestion-service main.go
```

## Running the Services

Run the services in separate terminal windows/tabs.

### Terminal 1: Ingestion Service

```bash
# From project root
cd ingestion-service

# Run the ingestion service
go run main.go

# OR if you built it:
./ingestion-service
```

You should see output like:
```
Ingestion Service starting...
Delivered message to raw_stock_ticks[0]@0
Delivered message to raw_stock_ticks[1]@0
...
```

### Terminal 2: Flink Job

**Option A: Run from IDE (Recommended for Development)**
- Open `flink-processor/src/main/java/com/mycompany/app/MarketAnalysisJob.java` in your IDE
- Run the `main` method directly
- Flink will run in local mode

**Option B: Run using Flink CLI (If you have Flink installed)**
```bash
cd flink-processor
/path/to/flink/bin/flink run target/flink-processor-0.1.jar
```

**Option C: Run as Java Application**
```bash
cd flink-processor
java -jar target/flink-processor-0.1.jar
```

The Flink job will print analysis results to the console every minute:
```
--- Wiretapping AnalyzedTrend Stream ---
AnalyzedTrend{symbol='AAPL', averagePrice=150.25, isVolatilityAnomaly=false, windowEndTimestamp=...}
```

## Verifying the Pipeline

### Monitor Kafka Topics

You can monitor the data flowing through Kafka:

```bash
# Monitor raw_stock_ticks topic
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic raw_stock_ticks \
  --from-beginning

# In another terminal, monitor analyzed_market_trends topic
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic analyzed_market_trends \
  --from-beginning
```

You should see:
- JSON stock tick data in `raw_stock_ticks`
- Analyzed trend data in `analyzed_market_trends` (after 1-minute windows)

## Stopping the Services

```bash
# Stop ingestion service: Ctrl+C in its terminal

# Stop Flink job: Ctrl+C in its terminal (or stop from IDE)

# Stop Kafka infrastructure
docker-compose down

# Or stop and remove volumes (clean slate)
docker-compose down -v
```

## Troubleshooting

### Kafka Connection Issues
```bash
# Check if Kafka is running
docker ps | grep kafka

# Check Kafka logs
docker logs kafka

# Restart if needed
docker-compose restart kafka
```

### Go Dependencies Issues
```bash
cd ingestion-service
go mod tidy
go mod download
```

### Maven Build Issues
```bash
cd flink-processor
mvn clean
mvn dependency:resolve
mvn package
```

### Flink Job Not Running
- Ensure Kafka is running and accessible at `localhost:9092`
- Check that topics exist using `kafka-topics --list`
- Verify the JAR file was built successfully
- Check Flink console output for errors

## Project Structure

```
findata/
├── ingestion-service/     # Go service that produces stock tick data
├── flink-processor/       # Java Flink job for stream processing
├── api-service/          # API service (to be implemented)
├── alerting-service/     # Alerting service (to be implemented)
├── docker-compose.yml    # Kafka infrastructure setup
└── architecture.md       # System architecture diagram
```

## Key Features

- **Real-time Processing**: Processes market data as it arrives with sub-second latency
- **Stateful Stream Processing**: Maintains state across windows for accurate trend calculations
- **Anomaly Detection**: Identifies volatility anomalies (>2% price swings) in real-time
- **Scalable Architecture**: Designed to handle 10,000+ events per second
- **Fault Tolerant**: Graceful error handling and data durability via Kafka

## Technologies Used

- **Apache Flink 1.17.1**: Stream processing engine
- **Apache Kafka**: Message broker and event streaming platform
- **Go**: Ingestion service implementation
- **Java 8**: Flink job implementation
- **Maven**: Build tool for Java components
