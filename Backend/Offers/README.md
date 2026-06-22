# Offers Service

The Offers Service is a Spring Boot microservice designed to manage promotional offers. It uses MongoDB as its primary database.

## Prerequisites
- **Java 17** or higher
- **MongoDB** (running locally or via Docker)

---

## 1. Setting up MongoDB

### Option A: Using Docker (Recommended)
You can quickly run a MongoDB instance using Docker. Run the following command in your terminal:

```bash
docker run --name mongodb-offers -d -p 27017:27017 mongo:latest
```

This starts a MongoDB container named `mongodb-offers` detached in the background, mapping port `27017` to your host machine.

### Option B: Local MongoDB Installation
If you have MongoDB installed directly on your machine:
- Make sure the MongoDB service is running (default port `27017`).
- In Windows: Start it via the Services app or run `net start MongoDB` in an administrative terminal.

---

## 2. Configuration

The configuration details can be found in `src/main/resources/application.properties`:
- **Server Port**: `8085`
- **MongoDB URI**: `mongodb://localhost:27017/offersdb`

If you need to use a different database URI, you can override it by setting the `SPRING_DATA_MONGODB_URI` environment variable, or modifying the configuration file.

---

## 3. Running the Service

You can build and run the service using the Gradle wrapper from the root of the project:

### From the `Backend` directory:
Run:
```bash
./gradlew :Offers:bootRun
```
*(On Windows PowerShell, use `.\gradlew :Offers:bootRun`)*

This compiles the code and starts the Spring Boot application on port `8085`.

---

## 4. Verification

Once started, you can verify that the service is running by hitting the default health check or standard endpoints:
- Check application details/health at: `http://localhost:8085` (or any custom controller endpoint you implement under `com.aisandbox.offers.controller`).
