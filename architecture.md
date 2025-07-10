# My Project Architecture

Here is a diagram of the system architecture:

```mermaid
graph TD
  subgraph Ingestion Zone
    A["External Data Source<br>(e.g., Polygon.io API)"]
    A -- HTTPS --> B["Ingestion Service / Producer<br>[Go / Java]"]
    B -- "raw_stock_ticks (JSON)<br>Key: Stock Symbol" --> C{Kafka Cluster}
  end

  subgraph Processing Zone
    C -- "raw_stock_ticks" --> D["Apache Flink Job<br>[Stateful Stream Processor]"]
    D -- "analyzed_market_trends (JSON)" --> C
    D -- "alerts (JSON)" --> C
  end

  subgraph Serving & Consumption Zone
    E["API & Serving Service<br>[Go / Java]"] -- "Consumes raw_stock_ticks" --> C
    E -- "REST API (HTTP GET)" --> F["On-demand Consumers<br>(e.g., Mobile App)"]
    E -- "Real-time Feed (WebSockets)" --> G["Live Dashboard<br>(React/Vue)"]

    C -- "alerts (JSON)" --> H["Alerting Service<br>[Go / Java]"]
    H -- "Triggers notifications" --> I["Notification Systems<br>(Slack, Twilio SMS)"]
  end

  style C fill:#f9f,stroke:#333,stroke-width:2px
  style D fill:#bbf,stroke:#333,stroke-width:2px
