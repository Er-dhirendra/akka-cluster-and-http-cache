# Akka Cluster Cache System

A distributed in-memory key-value cache system built using **Akka Typed**, **Akka Cluster**, and **Akka HTTP**. This app demonstrates actor-based caching with cluster awareness, a RESTful API, and in-memory storage.

---

## 🛠 Features

* Akka Typed Actor-based caching
* Cluster-aware service discovery using `Receptionist`
* In-memory key-value storage (`TrieMap`)
* REST API for `PUT`, `GET`, and `DELETE` operations
* CBOR-based serialization for efficient inter-node communication

---

## 📦 Project Structure

```text
src/main/scala
├── com
│   ├── cache              # CacheActor + ServiceKey
│   ├── cluster            # Cluster logger + bootstrap
│   ├── repository         # In-memory storage
│   ├── utils              # CborSerializable trait
│   └── AkkaHttpTypedClient.scala # HTTP Server entrypoint
```

---

## ▶️ How to Run

You can start multiple cluster nodes on different ports.

### 1. Run Cluster Nodes

Open **2 or more terminals** and run the following with different ports (e.g., 2551 and 2552):

```bash
sbt "runMain com.cluster.ClusterApplication 2551"
sbt "runMain com.cluster.ClusterApplication 2552"
sbt "runMain com.cluster.ClusterApplication 0"
```

> Ensure ports `2551` and `2552` match the `seed-nodes` in `application.conf`.

### 2. Start HTTP API Server

In a **separate terminal**, run:

```bash
sbt "runMain com.AkkaHttpTypedClient"
```

This will start the API server at:

```
http://localhost:8080
```

---

## ✅ Health Check

```bash
curl http://localhost:8080/health
# Response: 200 OK
```

---

## 🧪 Test the Cache API

### PUT a key-value pair

```bash
curl -X PUT http://localhost:8080/cache/mykey \
  -H "Content-Type: application/json" \
  -d '{"value": "myvalue"}'
```

### GET a value by key

```bash
curl http://localhost:8080/cache/mykey
# Response: "myvalue"
```

### DELETE a key

```bash
curl -X DELETE http://localhost:8080/cache/mykey
```

---

## 🧠 Notes

* The system uses **Akka Receptionist** for dynamic actor discovery.
* Cluster event logging is handled by `ClusterLogger`.
* `replySink` is used as a dummy actor for fire-and-forget PUT and DELETE operations.
* Uses `jackson-cbor` serialization for cluster messages.

---

## 🧰 Requirements

* Java 11+
* SBT
* Internet access to fetch dependencies

---