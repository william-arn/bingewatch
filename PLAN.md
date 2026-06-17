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
│       ├── java/com/bingewatch/catalog/
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
| Group | `com.bingewatch` |
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

Under `src/main/java/com/bingewatch/catalog/`:

**`domain/Movie.java`**
```java
package com.bingewatch.catalog.domain;

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
package com.bingewatch.catalog.repo;

import com.bingewatch.catalog.domain.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long> {
    List<Movie> findByGenre(String genre);
}
```

**`web/CatalogController.java`**
```java
package com.bingewatch.catalog.web;

import com.bingewatch.catalog.domain.Movie;
import com.bingewatch.catalog.repo.MovieRepository;
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

On Windows/PowerShell, prefer `Invoke-RestMethod` — the Unix-style `curl -d '{\"..\"}'`
escaping gets mangled by PowerShell's parser.

```powershell
# Create
Invoke-RestMethod -Uri http://localhost:8080/movies -Method Post `
  -ContentType 'application/json' `
  -Body '{"title":"The Matrix","genre":"SciFi","releaseYear":1999}'
# List all
Invoke-RestMethod http://localhost:8080/movies
# By id
Invoke-RestMethod http://localhost:8080/movies/1
# Filter by genre
Invoke-RestMethod "http://localhost:8080/movies?genre=SciFi"
# Delete
Invoke-RestMethod -Uri http://localhost:8080/movies/1 -Method Delete
```

> Note: the JSON field is `releaseYear` (it matches the entity's `getReleaseYear`/
> `setReleaseYear` accessors — Jackson derives JSON property names from the getters/setters,
> not the column name). Even better than the shell for repeated testing: a REST client like
> **Bruno** or the **IntelliJ HTTP Client** (a `.http` scratch file).

---

## 5b. Phase 1B — harden catalog-service before gRPC

Phase 1 gives a *working* service. Phase 1B makes it a *well-architected* one — and
every item here pays off in Phase 2, because the gRPC server will reuse the same service
layer, DTO-mapping discipline, and schema. Do these in order; each builds on the last.

### Step 1 — Introduce a service layer (`CatalogService`)

**Why:** The controller currently talks to the repository directly. Once gRPC arrives,
*two* entry points (REST + gRPC) will need the same business logic. Putting that logic in
a `CatalogService` means it's written once and shared, and the controllers stay thin.

- Create `service/CatalogService.java`, annotate it `@Service`.
- Move the read/create/delete logic into it (e.g. `list(genre)`, `getById(id)`,
  `create(...)`, `delete(id)`).
- Inject `CatalogService` into `CatalogController` (constructor injection) instead of the
  repository. The controller becomes purely "HTTP in, HTTP out."

```
Controller (HTTP)  ─┐
                    ├─▶  CatalogService (business logic)  ─▶  MovieRepository  ─▶  DB
gRPC handler (P2)  ─┘
```

### Step 2 — Introduce DTOs (stop exposing the entity)

**Why:** Right now the JPA entity *is* the API contract — `@RequestBody Movie` and
returning `Movie` directly. That couples your database schema to your public API: a column
rename leaks to clients, and clients can POST fields like `id` that you don't want them to
set. DTOs (Data Transfer Objects) decouple the two.

- `web/dto/MovieRequest.java` — fields clients may send (`title`, `genre`, `releaseYear`);
  **no** `id`.
- `web/dto/MovieResponse.java` — fields you return (includes `id`).
- Map between DTO ↔ entity in the service (or a small mapper). Start by hand; a library
  like MapStruct is a later option.
- **Tip:** Java `record` types are perfect for DTOs — immutable, concise:
  `public record MovieRequest(String title, String genre, Integer releaseYear) {}`

### Step 3 — Add Bean Validation

**Why:** Reject bad input at the edge with clear 400 errors instead of letting nulls/blanks
reach the database.

- Add constraints to `MovieRequest`: `@NotBlank` on `title`, `@NotBlank` on `genre`,
  optionally `@Positive`/`@Min(1888)` on `releaseYear`.
- Add `@Valid` before `@RequestBody MovieRequest` in the controller.
- (Dependency `spring-boot-starter-validation` is already on the classpath.)

### Step 4 — Global exception handling (`@RestControllerAdvice`)

**Why:** Pairs with Steps 2–3. Centralize error responses so clients get clean, consistent
JSON instead of stack traces. This also replaces the ad-hoc `Optional.orElse(notFound())`
pattern with a tidy "throw + handle" flow.

- Create a `NotFoundException` and have `CatalogService.getById` throw it when missing.
- Create `web/ApiExceptionHandler.java` annotated `@RestControllerAdvice` that maps:
  - `NotFoundException` → `404` with a small error body,
  - `MethodArgumentNotValidException` (validation failures) → `400` with field errors.
- Turn off the leaking stack traces: `server.error.include-stacktrace: never` in YAML.

### Step 5 — Tighten HTTP semantics

**Why:** Make the API correct and idiomatic — good portfolio signal.

- `POST` returns **`201 Created`** (optionally with a `Location` header via
  `ServletUriComponentsBuilder`).
- Add **`PUT /movies/{id}`** for full update (and/or `PATCH` for partial).
- Confirm `DELETE` returns **`204 No Content`**.
- Decide a convention: every endpoint returns `ResponseEntity<…>` for uniformity, *or*
  plain bodies where always-200 — just be consistent.

### Step 6 — Add tests

**Why:** A repo with no tests is a red flag to a reviewer; this is also where you learn
Spring's test slices (cheaper than always booting the full app).

- `@WebMvcTest(CatalogController.class)` + `MockMvc`, mocking `CatalogService` — tests the
  web layer fast, no DB.
- `@DataJpaTest` — tests `MovieRepository` (including `findByGenre`) against a real Postgres
  via **Testcontainers** (closest to prod) or an in-memory H2 (simplest).
- One `@SpringBootTest` smoke test (you already have `contextLoads`).

### Step 7 — Flyway migrations (replace `ddl-auto`)

**Why:** `ddl-auto: update` is convenient for learning but unsafe and non-explicit for real
apps — it can't do controlled, versioned schema changes. Flyway makes the schema a
reviewed, version-controlled artifact. Do this **last**, once the entity has settled.

- Add dependency `org.flywaydb:flyway-database-postgresql`.
- Create `src/main/resources/db/migration/V1__create_movies.sql` with the `movies` table
  DDL (you can copy the `create table` statement Hibernate logged at startup).
- Set `spring.jpa.hibernate.ddl-auto: validate` (Hibernate now only *checks* that entities
  match the Flyway-managed schema, never alters it).

### Step 8 — Housekeeping

- Silence the startup warning by setting `spring.jpa.open-in-view: false` explicitly (and
  understand why: it keeps the persistence session open during view rendering — fine here,
  but off is the safer default for services).
- Run **Ctrl+Alt+L** in IntelliJ to standardize formatting.
- Write the public `README.md` (the Netflix-DGS/Gradle framing lives here).

**Definition of done for Phase 1B:** thin controller → `@Service` → repository; DTOs at the
boundary; validated input; consistent error JSON; correct status codes; meaningful tests;
Flyway-managed schema with `ddl-auto: validate`. *Then* move to gRPC.

---

## 6. Later phases (contracts for reference)

### Phase 2 — add gRPC server to catalog-service

`proto/catalog.proto`:
```proto
syntax = "proto3";
option java_multiple_files = true;
option java_package = "com.bingewatch.catalog.grpc.proto";

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
