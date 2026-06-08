# Insight Sales Hub

Insight Sales Hub is a modern, enterprise-grade Java Spring Boot demonstration application designed to showcase key web development paradigms, microservice architectures, and data structures following **KISS** and **DRY** principles.

---

## 🚀 Key Features Demonstrated

1. **Spring IoC (Inversion of Control)**:
   - Implements the **Strategy Pattern** for dynamic payment method processing (Credit Card and E-Wallet).
   - Showcases dynamic bean resolution at runtime via a strategy registry.

2. **Java Streams**:
   - Performs memory-efficient data pipeline operations (filter, map, reduce, group, and partition) on transactional collections.

3. **Advanced Native SQL Queries**:
   - Integrates complex PostgreSQL/H2 kueri using Common Table Expressions (CTEs) and Window Functions (`DENSE_RANK() OVER (PARTITION BY ...)` and running totals).

4. **Containerization & Microservices**:
   - Packages the Spring Boot service via a multi-stage `Dockerfile`.
   - Orchestrates PostgreSQL, Redis, Apache Kafka (KRaft mode), and the application using `docker-compose.yml`.

5. **Kafka & Stream-Based Application**:
   - Processes orders asynchronously using non-blocking Kafka event loops.

6. **Redis Caching & Data Grid**:
   - Implements `@Cacheable` caching strategies with custom TTLs.
   - Computes a real-time sales leaderboard using Redis Sorted Sets (`ZSET`).
   - Safeguards inventory transactions against race conditions using a Redis-backed distributed lock with retries.

---

## 🛠️ Prerequisites

- **Java**: 17 or higher
- **Maven**: 3.8.x or higher
- **Docker / Docker Compose** (Optional, only needed for full containerized profile)

---

## 🏃 How to Run the Application

The application supports three environment profiles to match your local setup:

### Profile 1: Standalone Mock Mode (Zero Setup - Recommended)
If you do not have Docker, Postgres, Redis, or Kafka installed, you can boot the entire application using built-in mock configurations (using H2 database and in-memory mocks):
```bash
SPRING_PROFILES_ACTIVE=mock mvn spring-boot:run
```
*The server will start on port **8081**.*

### Profile 2: Local Services Mode
If you have PostgreSQL, Redis, and Apache Kafka running directly on your host machine's `localhost`:
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```
*The server will start on port **8080**.*

### Profile 3: Fully Containerized Mode
To orchestrate and run all components inside Docker containers:
```bash
docker compose up --build
```
*The server will start on port **8080**.*

---

## 📡 API Endpoints

Once the application is running, you can interact with the following REST API endpoints:

### Products
- **List All Products**: `GET /api/products`
- **Get Product Details (Cached)**: `GET /api/products/{id}`
- **Create Product**: `POST /api/products`

### Orders
- **Place New Order**: `POST /api/orders`
  - *Example Request Body:*
    ```json
    {
      "customerName": "Daniel",
      "paymentMethod": "CREDIT_CARD",
      "items": [
        { "productId": 1, "quantity": 2 },
        { "productId": 3, "quantity": 1 }
      ]
    }
    ```

### Analytics & Reports
- **In-Memory Java Streams Aggregation**: `GET /api/analytics/streams/summary`
- **Native SQL Daily Sales Cumulative Trend**: `GET /api/analytics/native/sales-trends`
- **Native SQL Category Top Products (DENSE_RANK)**: `GET /api/analytics/native/top-products?maxRank=3`
- **Redis Sales Leaderboard (ZSET)**: `GET /api/analytics/leaderboard?limit=5`

---

## 🧪 Running Tests
To run compile checks and the automated unit tests:
```bash
mvn test
```
