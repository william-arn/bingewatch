# bingewatch — Project Plan

A personal learning project: a small, Netflix-themed media catalog built as a set of
local microservices. The goal is hands-on practice with **Spring Boot, gRPC, GraphQL,
and REST**, leaning on Netflix open-source tooling — and to run entirely locally via
Docker (no significant cloud spend).

> The "Netflix" angle is intentional but lives in the tech choices and this README,
> not in the project name (avoiding the trademark). Headline signals: **Netflix DGS**
> for GraphQL and **Gradle** as the build tool — both are what Netflix actually uses.

---

## 1. Goals

- Learn the four protocols in one coherent system: **REST** (public CRUD), **gRPC**
  (fast internal service-to-service), **GraphQL** (unified client gateway via Netflix DGS).
- Practice microservice patterns: service discovery (Eureka) and resilience (Resilience4j).
- Keep every step independently runnable, building incrementally.
- Everything runs locally with `docker compose` — no AWS required.

## 2. Architecture

Three services plus supporting infrastructure:

| Service | Responsibility | Protocols |
|---|---|---|
| **catalog-service** | Owns movie/show data | REST API (external) + gRPC server (internal) |
| **recommendation-service** | "Because you watched…" logic | gRPC server; gRPC client → catalog |
| **graph-gateway** | Single client-facing API | GraphQL via **Netflix DGS**; gRPC client → both services |

Supporting: **Eureka** (service discovery), **Resilience4j** (circuit breakers around
gRPC calls). Stretch: Netflix **Conductor** (workflow orchestration),
**Prometheus + Grafana** (observability).

```
client ──HTTP/GraphQL──▶ graph-gateway ──gRPC──▶ catalog-service ──▶ Postgres
                                       └──gRPC──▶ recommendation-service ──gRPC──▶ catalog-service
                                 (all services register with Eureka)
```

## 3. Tech stack (all local / free)

- **Java 21** (Eclipse Temurin)
- **Gradle — Kotlin DSL** (`build.gradle.kts`)
- **Spring Boot 3.5.15** (chosen over 4.x for mature Netflix-OSS compatibility)
- **PostgreSQL 16** in Docker (one DB per service)
- gRPC via `protobuf-gradle-plugin` + a Spring Boot gRPC starter
- GraphQL via `com.netflix.graphql.dgs` starter
- Service discovery via Spring Cloud Netflix **Eureka**
- Resilience via **Resilience4j**

## 4. Repository layout

Multi-module Gradle monorepo, rooted at `C:\repos\bingewatch\`:

```
bingewatch/
├── PLAN.md                      # this document
├── README.md
├── settings.gradle.kts          # includes the modules
├── docker-compose.yml           # postgres, eureka, the 3 services (added incrementally)
├── proto/                       # shared .proto contracts (added in Phase 2)
│   ├── catalog.proto
│   └── recommendation.proto
├── catalog-service/             # PHASE 1 — built first
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/yourname/catalog/
│       │   ├── CatalogServiceApplication.java
│       │   ├── domain/Movie.java            # JPA entity
│       │   ├── repo/MovieRepository.java     # Spring Data JPA
│       │   └── web/CatalogController.java     # REST API
│       └── resources/application.yml
├── recommendation-service/      # PHASE 3
└── graph-gateway/               # PHASE 4
```

---

## 5. Phase 1 — catalog-service (REST + Postgres)

The first runnable slice: a `Movie` REST API backed by Postgres. No gRPC yet.

### Step 1 — Generate the project (start.spring.io)

| Field | Value |
|---|---|
| Project | **Gradle - Kotlin** |
| Language | Java |
| Spring Boot | **3.5.15** |
| Group | `com.yourname` |
| Artifact | `catalog-service` |
| Packaging | Jar |
| Java | **21** |
| Dependencies | Spring Web, Spring Data JPA, PostgreSQL Driver, Validation, (Spring Boot DevTools — optional) |

Unzip into `C:\repos\bingewatch\catalog-service`.

### Step 2 — Open in IntelliJ

`File → Open` → select the `catalog-service` folder (the one containing
`build.gradle.kts`). Let Gradle import finish; IntelliJ auto-detects JDK 21.

### Step 3 — Start Postgres in Docker

```powershell
docker run --name catalog-pg `
  -e POSTGRES_DB=catalog -e POSTGRES_USER=catalog -e POSTGRES_PASSWORD=catalog `
  -p 5432:5432 -v catalog-pgdata:/var/lib/postgresql/data `
  -d postgres:16
```

Verify with `docker ps`. Later: `docker stop catalog-pg` / `docker start catalog-pg`.

### Step 4 — Configuration (`src/main/resources/application.yml`)

Rename the generated `application.properties` to `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/catalog
    username: catalog
    password: catalog
  jpa:
    hibernate:
      ddl-auto: update      # auto-creates tables from entities (fine for learning)
    show-sql: true
    properties:
      hibernate.format_sql: true
server:
  port: 8080
```

> `ddl-auto: update` lets Hibernate build tables from entities. A good later upgrade is
> Flyway migrations (also a nice resume item).

### Step 5 — The layers

Under `src/main/java/com/yourname/catalog/`:

**`domain/Movie.java`**
```java
package com.yourname.catalog.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "movies")
public class Movie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String genre;
    private Integer year;

    protected Movie() {}  // JPA needs a no-arg constructor

    public Movie(String title, String genre, Integer year) {
        this.title = title; this.genre = genre; this.year = year;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
}
```

**`repo/MovieRepository.java`**
```java
package com.yourname.catalog.repo;

import com.yourname.catalog.domain.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long> {
    List<Movie> findByGenre(String genre);
}
```

**`web/CatalogController.java`**
```java
package com.yourname.catalog.web;

import com.yourname.catalog.domain.Movie;
import com.yourname.catalog.repo.MovieRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/movies")
public class CatalogController {

    private final MovieRepository repo;
    public CatalogController(MovieRepository repo) { this.repo = repo; }

    @GetMapping
    public List<Movie> all(@RequestParam(required = false) String genre) {
        return (genre == null) ? repo.findAll() : repo.findByGenre(genre);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Movie> one(@PathVariable Long id) {
        return repo.findById(id)
                   .map(ResponseEntity::ok)
                   .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Movie create(@RequestBody Movie movie) {
        return repo.save(movie);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
```

> The service layer is intentionally omitted for this thin CRUD slice. Introduce a
> `CatalogService` once there's real logic — and especially once both REST and gRPC
> need to share it (Phase 2).

### Step 6 — Run

Run `CatalogServiceApplication` from IntelliJ (green ▶). Console should show Hibernate
creating the `movies` table and `Tomcat started on port 8080`.

### Step 7 — Test

```powershell
# Create
curl.exe -X POST http://localhost:8080/movies -H "Content-Type: application/json" -d '{\"title\":\"The Matrix\",\"genre\":\"SciFi\",\"year\":1999}'
# List all
curl.exe http://localhost:8080/movies
# By id
curl.exe http://localhost:8080/movies/1
# Filter by genre
curl.exe "http://localhost:8080/movies?genre=SciFi"
```

### Phase 1 follow-ups (once it runs)
- Add Bean Validation (`@NotBlank` on title, `@Valid` in the controller).
- Introduce a DTO instead of exposing the entity directly.
- Add a `CatalogService` layer.
- Swap `ddl-auto` for Flyway migrations.

---

## 6. Later phases (contracts for reference)

### Phase 2 — add gRPC server to catalog-service

`proto/catalog.proto`:
```proto
syntax = "proto3";
option java_multiple_files = true;
option java_package = "com.yourname.catalog.grpc.proto";

service CatalogService {
  rpc GetMovie (GetMovieRequest) returns (MovieReply);
  rpc ListMovies (ListMoviesRequest) returns (ListMoviesReply);
}

message GetMovieRequest { int64 id = 1; }
message MovieReply { int64 id = 1; string title = 2; string genre = 3; int32 year = 4; }
message ListMoviesRequest { string genre = 1; }
message ListMoviesReply { repeated MovieReply movies = 1; }
```

Add `protobuf-gradle-plugin` + a gRPC Spring Boot starter; implement the service against
the shared `CatalogService` business logic. Test with `grpcurl`.

### Phase 3 — recommendation-service

gRPC server that calls catalog over gRPC (gRPC client). Wrap the client call with a
Resilience4j `@CircuitBreaker` + fallback.

### Phase 4 — graph-gateway (Netflix DGS)

`graph-gateway/src/main/resources/schema/schema.graphqls`:
```graphql
type Query {
  movie(id: ID!): Movie
  recommendations(forMovieId: ID!): [Movie!]!
}

type Movie {
  id: ID!
  title: String!
  genre: String
  year: Int
  recommendations: [Movie!]!   # nested resolver → calls recommendation-service
}
```

DGS data fetchers (`@DgsComponent` / `@DgsQuery` / `@DgsData`) fan out to the two
services over gRPC. Use the built-in **GraphiQL** UI at `/graphiql` to test.

### Phase 5 — discovery + compose

- Stand up a Eureka server (`@EnableEurekaServer`); every service registers as a client.
- `docker-compose.yml` runs Postgres, Eureka, and all three services with one command.

### Stretch
- Netflix **Conductor** for an orchestrated workflow.
- **Micrometer → Prometheus → Grafana** dashboards.

---

## 7. Build order (each step independently runnable)

1. Eureka server → dashboard at `localhost:8761` *(or defer to Phase 5)*
2. **catalog-service REST + Postgres** ← current focus
3. gRPC server on catalog-service (test via `grpcurl`)
4. recommendation-service calling catalog over gRPC
5. graph-gateway with DGS (test via GraphiQL)
6. Wrap everything in Docker Compose
7. Stretch: Conductor and/or Prometheus + Grafana

## 8. Tooling
- **grpcurl** — exercise gRPC endpoints from the terminal
- **DGS GraphiQL** — built-in GraphQL UI (no Postman needed)
- **Postman / Bruno / httpie / curl** — REST testing
