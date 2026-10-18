# Microservices: Why Use Multiple Services?

Microservices split an application into smaller services, each responsible for a distinct business capability. Instead of building one large application that handles everything, each service can be developed, deployed, and scaled independently.

In this e-commerce project:

- **Inventory service** manages products and stock.
- **Order service** creates and tracks orders.
- **Discovery service** helps services find one another.
- **API gateway** provides a common entry point for client requests and routes them to the right service.

This separation helps teams change or scale one capability without having to redeploy the entire application. Services can also have clear ownership and communicate through APIs.

Microservices add complexity too: services must communicate over the network, and their configuration, monitoring, deployment, and data consistency need to be managed. They are most useful when the benefits of independent ownership and scaling justify that overhead.

## Service Communication and Discovery

### The communication problem

In a monolithic application, one component can call another directly inside the same process. Microservices run as separate applications, often on separate machines or containers. A service cannot assume another service is running at a fixed address: instances can restart, move, or scale up and down. Network calls can also be slow or fail.

Services therefore communicate over the network using a defined contract, commonly HTTP APIs. REST is one valid option. In Spring applications, calls can be made with `RestClient` or declared as interfaces using Spring Cloud OpenFeign. Feign can use service discovery to locate an instance; with Spring Cloud LoadBalancer on the calling application, it can also select among instances. A `RestClient` can use a fixed URL or be configured for discovery and load balancing.

All of the applications in this project are Spring Boot applications: inventory, order, discovery (Eureka Server), and API gateway. Spring Cloud provides integrations that make common microservice tasks—such as discovery, routing, and declarative HTTP calls—easier to configure.

### Eureka Server (discovery service)

Eureka Server is a separate application that acts as a service registry. Client applications register their service name and instance information with it and send periodic heartbeats. Other applications can query the registry to find available instances by service name instead of hard-coding each host and port. If multiple instances are registered, a discovery-aware client can select an instance, typically with load balancing.

The registry maintains registration and lease/status information. It helps clients discover instances; it is not a replacement for application monitoring and does not guarantee that every endpoint is healthy. Eureka itself does not distribute or proxy business requests.

#### Eureka Server dependency and configuration

The discovery service needs Spring Cloud Netflix Eureka Server. The project declares this dependency in `discovery-service/pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
</dependency>
```

The application class enables the server with `@EnableEurekaServer`. Its current `application.properties` sets:

```properties
spring.application.name=discovery-service
server.port=8761
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false
```

The Eureka server is itself capable of acting as a Eureka client. In this single-server setup, it does not need to register itself or fetch a registry from a peer server, so both client behaviors are disabled. This avoids treating the registry as an application instance that should discover itself. A multi-server Eureka cluster has different peer configuration.

The Eureka Server POM also imports the Spring Cloud BOM in its `<dependencyManagement>` section. A BOM (Bill of Materials) supplies a compatible set of dependency versions. Importing it lets the POM omit explicit versions for Spring Cloud artifacts such as the Eureka Server starter; it manages versions but does not add those dependencies by itself. Each standalone application POM that uses Spring Cloud dependencies imports the BOM too.

In Maven, `<dependencyManagement>` centralizes or imports dependency version rules. Entries there do not add a library to the application; the library must still be declared under `<dependencies>`. Importing the Spring Cloud BOM keeps Spring Cloud modules on compatible versions and avoids manually versioning each starter.

#### Registering client applications

An application registers as a Eureka client by including Spring Cloud Netflix Eureka Client and pointing its client configuration to the Eureka server. The project includes the client dependency in the inventory service and order service POMs (and also in the API gateway POM):

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

Each client needs a unique application name and the Eureka server address. For example, the inventory service currently has:

```properties
spring.application.name=inventory-service
eureka.client.service-url.defaultZone=http://localhost:8761/eureka
```

The order service uses `spring.application.name=order-service` and the same `defaultZone`. The gateway uses `spring.application.name=api-gateway` and also points to `http://localhost:8761/eureka`. With the Eureka client dependency present, registration and registry fetching are enabled by default; explicitly setting `eureka.client.register-with-eureka=true` or `eureka.client.fetch-registry=true` is usually unnecessary.

Clients should use the service name as the discovery key—for example, Feign can target `order-service`—rather than hard-coding an instance's host and port. The discovery server must be running and reachable for registration and registry updates.

### Calling another service with Spring Cloud OpenFeign

OpenFeign lets a Spring application describe an HTTP client as a Java interface. Add the Spring Cloud OpenFeign starter to the calling application's `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

The Spring Cloud BOM manages its version. Enable Feign client scanning on the calling application's main class with `@EnableFeignClients`. In this project, both `InventoryServiceApplication` and `OrderServiceApplication` have this annotation.

`@EnableFeignClients` enables Feign client support and tells Spring to scan for interfaces annotated with `@FeignClient` (by default, starting from the package of the application class and its subpackages). Spring then creates and registers a client proxy bean for each discovered interface so it can be injected into services or controllers. Without this annotation—or another configuration that enables and scans Feign clients—the interface is just an interface and no injectable client bean is created.

For example, the order service calls inventory to reduce product stock:

```java
@FeignClient(name = "inventory-service", path = "/inventory")
public interface InventoryOpenFeignClient {
    @PutMapping("/products/reduce-stock")
    Double reduceStock(@RequestBody OrderRequestDto request);
}
```

- `name = "inventory-service"` is the Eureka application/service ID. It must match the target application's `spring.application.name`.
- `path = "/inventory"` is an optional common path prefix. Here it matches the inventory service's `server.servlet.context-path=/inventory`.
- `@PutMapping("/products/reduce-stock")` gives the endpoint path and HTTP method. Together with the context path, the request path is `/inventory/products/reduce-stock`.
- `@RequestBody` tells Spring/Feign to serialize the DTO as the request body. The target endpoint must accept the same request shape and return a response compatible with `Double`.

You can also set an absolute `url` in `@FeignClient`, for example `url = "http://localhost:9010"`. That makes the client call that fixed address directly rather than resolve instances by the Eureka service name. Use a fixed URL only when that is intentional (for example, a service not registered in Eureka); avoid it for normal Eureka-managed service-to-service calls because it hard-codes the destination and does not use discovery-based instance selection.

#### What happens when the Feign call runs?

The interface is a contract, not a class you implement by hand. At startup, Spring Cloud OpenFeign scans it and creates a managed client proxy (a generated runtime implementation/bean). When application code calls `inventoryOpenFeignClient.reduceStock(request)`, that proxy:

1. Builds an HTTP `PUT` request from the Feign annotations and serializes the request DTO.
2. Uses the service ID `inventory-service` to ask the discovery integration for registered instances. When Spring Cloud LoadBalancer is present in the calling service, it selects an instance using its address and port.
3. Sends the HTTP request to the selected instance, including `/inventory/products/reduce-stock`, then converts the HTTP response to the declared return type (`Double`).

Eureka provides registry information; it does not forward or execute the business API call. The calling application's Feign client makes the network request using the selected instance address and port. If several instances are registered and Spring Cloud LoadBalancer is on the caller's classpath, it can select among them. Do not add a load-balancer dependency to the Eureka Server just to balance application calls; add it to the client that makes the calls.

### API Gateway

An API Gateway is an HTTP service that provides clients with a single public entry point to multiple backend services. It can route requests, apply shared cross-cutting behavior, and keep clients from needing to know each backend's host, port, or internal service layout. In this project, the gateway is a separate Spring Boot application, runs on port `8080` by default, and registers with Eureka as `api-gateway`.

The gateway is an entry point by design, not a network rule that makes direct access impossible. If every external request must go through it, backend services should be protected from direct public access with network, firewall, or authentication policy. Internal service-to-service calls such as order-to-inventory Feign calls normally use service discovery directly and do not automatically pass through the gateway.

#### Gateway dependencies and Eureka registration

The current `api-gateway/pom.xml` includes:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

The Gateway starter provides gateway routing/filter capabilities. The Eureka Client starter lets the gateway register itself and discover backend instances. Its Spring Cloud BOM is imported under `<dependencyManagement>` to manage Spring Cloud dependency versions; it does not itself include the gateway or Eureka dependencies.

Routes with `lb://SERVICE-ID` need Spring Cloud LoadBalancer in the gateway application. If it is not already present through the resolved dependency graph, add this dependency to the gateway POM (not to the Eureka Server):

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

The gateway's current `application.yml` contains:

```yaml
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://ORDER-SERVICE
          predicates:
            - Path=/api/v1/orders/**
          filters:
            - StripPrefix=2
        - id: inventory-service
          uri: lb://INVENTORY-SERVICE
          predicates:
            - Path=/api/v1/inventory/**
          filters:
            - StripPrefix=2
            - AddRequestHeader=X-Custom-Header, Dhanush
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka
```

`spring.application.name=api-gateway` is the name it registers under. Since no `server.port` is specified, Spring Boot uses its default port, `8080`. The `lb://...` route URIs refer to service IDs in discovery; the gateway must have Spring Cloud LoadBalancer available to resolve and distribute those requests across service instances.

#### Routes: predicates and filters

A route has an ID, a destination URI, one or more **predicates** that decide whether the route matches an incoming request, and optional **filters** that modify the request or response. Predicates select a route; filters transform or process a request/response before or after forwarding.

- `Path=/api/v1/orders/**` matches paths under `/api/v1/orders/`. The inventory predicate similarly matches `/api/v1/inventory/**`.
- `Method=GET` or `Method=POST` can be added as a predicate to restrict a route by HTTP method. Without a method predicate, the gateway does not limit the route to one HTTP verb: methods such as `GET`, `POST`, `PUT`, `PATCH`, and `DELETE` can be forwarded. The backend still needs a matching endpoint and can return `405 Method Not Allowed` if it does not support that method.
- `Header=User-Agent, Mozilla/*` matches requests whose `User-Agent` header matches the configured pattern. Header predicates can route or restrict traffic based on request metadata; they are not a substitute for authentication.
- `StripPrefix=2` removes the first two path segments before forwarding. For `/api/v1/orders/core/helloOrders`, the backend receives `/orders/core/helloOrders`. This matters because the order service has context path `/orders`; its controller maps `/core/helloOrders`.
- `AddRequestHeader=X-Custom-Header, Dhanush` adds the named header to requests forwarded on the inventory route. Headers can carry metadata such as a correlation ID or a trusted internal value. Do not trust client-supplied identity headers for authorization; authenticate and validate them appropriately.
- `AddResponseHeader=X-Response-Id, abcd` adds a header to the response sent back to the client. `RemoveRequestHeader=Cookie` removes a request header before forwarding it. Use header filters carefully: removing authentication cookies or forwarding an untrusted identity header may alter security behavior.
- `RedirectTo` can return an HTTP redirect (for example, a `302`) to a client. It is useful for moving a public URL, directing traffic to a canonical URL, or temporarily sending clients elsewhere. It does not proxy the request to the backend; the client receives the redirect and makes another request. Use a route destination for ordinary internal service forwarding.

Gateway filters can also support concerns such as authentication integration, CORS, logging, rate limiting, retries, and request/response header or path rewriting, depending on the chosen filter and configuration. Centralizing these concerns can simplify services, but the gateway should not become the only security control or a dumping ground for all business logic.

#### Request flow and example URLs

For a request such as:

```text
GET http://localhost:8080/api/v1/orders/actuator/health
```

the gateway matches the orders path predicate, strips `/api/v1`, resolves `ORDER-SERVICE` through discovery/load balancing, and forwards `/orders/actuator/health` to the chosen order-service instance. The `/orders` prefix is the order service's servlet context path; `/actuator/health` is the Actuator health endpoint. Calling `/api/v1/orders/actuator` without `/health` targets the Actuator base path, not the health endpoint, and may not return the health status.

Similarly, a product request can use:

```text
GET http://localhost:8080/api/v1/inventory/products
```

The inventory route strips `/api/v1` and forwards `/inventory/products` to the selected inventory-service instance. The inventory controller then handles `/products` after the `/inventory` context path is applied.

#### Eureka, Feign, and load balancing: which component does what?

For a client request, the gateway is the entry point: **client → API Gateway → selected backend instance**. For an internal Feign call, the gateway is usually not involved: **order service → Feign client → selected inventory instance**.

Eureka Server stores registrations and serves discovery information. It does not choose an instance for each HTTP request and does not load-balance traffic. The calling component performs selection using Spring Cloud LoadBalancer:

- For gateway routes using `lb://ORDER-SERVICE` or `lb://INVENTORY-SERVICE`, Spring Cloud LoadBalancer must be available to the gateway application. The gateway receives the client request, looks up instances, selects an instance, and proxies the request.
- For Feign clients using `@FeignClient(name = "inventory-service")`, Spring Cloud LoadBalancer must be available to the calling service for client-side instance selection. In this case the calling service looks up and calls inventory directly; the public API gateway is not automatically part of that internal call.

Add the Spring Cloud LoadBalancer starter to each caller that needs discovery-based balancing (for this project, the gateway for its `lb://` routes and the order service for its Feign-to-inventory call, if not already transitively available), not to the Eureka Server merely because it is a registry. Feign provides the declarative HTTP-client interface; it does not mean that every application automatically has load balancing. The calling service needs Spring Cloud LoadBalancer integration for Feign service-name calls to select among multiple instances.

If only one instance is registered, there is no distribution to perform; with multiple instances, each caller selects among registered instances. Separate service instances also need to be configured with distinct ports/addresses and registered under the same service name.

The diagrams show the logical responsibility flow. Eureka is a registry that the caller consults (and whose registry data may be cached); it does not proxy every business request:

```text
Internal service-to-service call:
Order Service -> Feign client + Spring Cloud LoadBalancer
              -> discovers inventory-service instances via Eureka
              -> selected Inventory Service instance

External client call:
Client -> API Gateway + Spring Cloud LoadBalancer
       -> discovers the target service instances via Eureka
       -> selected backend service instance
```

The internal Feign call does **not** need to go through the API Gateway. Sending it through the gateway would add another network hop and invoke gateway routing and filters unnecessarily unless the architecture deliberately requires that. The gateway is mainly the unified entry point for external clients; internal services can call each other directly by discovered service name.

**Interview summary:** Eureka registers and provides information about available service instances. Spring Cloud LoadBalancer, running in the caller, selects an instance. The API Gateway uses this for routed client traffic; an internal service such as order-service uses it with Feign for its own inventory-service call. Eureka is not the load balancer, and Feign does not require the API Gateway.

### What the API Gateway can be used for

Common gateway responsibilities include:

- **Routing and a unified API:** expose stable client-facing paths and route them to backend services. This reduces the need for clients to know internal hosts, ports, or how many service instances exist.
- **Request/response transformation:** add, remove, or modify headers; rewrite or trim paths; and, where appropriate, transform request or response data.
- **Traffic policy:** route by path, HTTP method, headers, host, query parameters, or other supported predicates; restrict routes to selected methods; and redirect clients when a public URL changes.
- **Cross-cutting capabilities:** integrate authentication/authorization, CORS, rate limiting, logging, tracing, metrics, and resilience controls where configured.
- **Load-balanced forwarding:** when configured with `lb://` and Spring Cloud LoadBalancer, resolve a service ID through discovery and proxy each request to a selected instance.

These are available capabilities, not automatic behavior just because a gateway dependency exists. Each route, predicate, filter, security rule, and operational capability must be configured and tested. The gateway can simplify a system's external API, but backend services should still enforce appropriate authorization and validation.

## Resilience with Resilience4j

Microservices call each other over a network, so a downstream service can be slow, temporarily unavailable, or overloaded. Resilience4j provides policies that help a caller control these failures. In this project, order-service calls inventory-service through Feign while creating an order; the resilience policies belong in **order-service**, the caller that needs to tolerate inventory failures.

### Dependency and project setup

The current order-service POM includes Spring Cloud's Resilience4j circuit-breaker integration:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
```

The resolved dependency tree includes `resilience4j-spring-boot3`, which provides the annotation integration used by `@CircuitBreaker`, `@Retry`, and `@RateLimiter`. The order service also declares `spring-boot-starter-actuator`, used for health, metrics, and Resilience4j actuator endpoints. Resilience4j's Spring Boot integration expects Actuator and Spring AOP; this POM does not explicitly declare `spring-boot-starter-aop` (JPA brings in Spring AOP transitively). Declaring the AOP starter explicitly makes the annotation-proxy requirement clear and avoids relying on an unrelated transitive dependency.

Spring Cloud's BOM in `<dependencyManagement>` manages the Spring Cloud starter version; it does not add the resilience starter by itself. The resilience starter is declared under `<dependencies>`.

The Spring Boot AOP starter, if declared explicitly, is:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

Add resilience dependencies to each **calling application** that needs these policies, not to the inventory service just because it is the downstream target.

### The three policies and when to use them

**Retry** repeats an operation after configured failures, which can help with brief transient issues such as a momentary network interruption. Keep attempts limited and add a controlled wait/backoff. Retrying a permanently failing request or non-idempotent operation can increase load or repeat side effects. In Resilience4j, `maxAttempts` is the total number of attempts, including the original call.

**Circuit breaker** watches call outcomes and temporarily stops calls after failures cross configured thresholds. It protects the caller and downstream service from a continuing stream of calls that are likely to fail:

```text
CLOSED     Calls pass through; outcomes are recorded.
   |       Failure threshold is reached
   v
OPEN       Calls fail fast without calling inventory; fallback may run.
   |       Configured open-state wait elapses
   v
HALF_OPEN  A limited number of real calls are allowed as recovery probes.
   | \
   |  \ probe calls still fail -> OPEN
   \ successful probes -> CLOSED
```

The circuit breaker normally probes using real downstream requests in `HALF_OPEN`; it does not necessarily call a separate health-check endpoint. A fallback is optional error-handling behavior invoked when the protected call fails or is rejected by the open circuit.

**Rate limiter** controls how frequently calls are admitted. It is useful for protecting a dependency or enforcing a request quota. Depending on its `timeoutDuration`, a call with no permit can wait for a permit to become available; if it cannot obtain one before the timeout, it is rejected and the fallback can run. A rate limiter does not detect service health.

Together, conceptually, these can control traffic and failures around a downstream call:

```text
Order Service
    -> Retry: retry a failed protected operation, if configured
    -> CircuitBreaker: pass, record, or fail fast, if configured
    -> RateLimiter: acquire a permit, if configured
    -> Inventory Service
```

For Resilience4j's default Spring annotation aspect order, the nesting is `Retry(CircuitBreaker(RateLimiter(call)))`: Retry is outermost, then CircuitBreaker, then RateLimiter around the actual call. Aspect order is configurable. This affects what each policy observes—for example, a circuit breaker outside a retry can record the final result after retries, while a circuit breaker inside can observe individual attempts. Rate-limit rejections and retries also interact. Verify the effective order for the project/version instead of inferring it from the visual order of annotations. Keep retries limited, waits controlled, and operations safe to repeat.

### Applying annotations and handling fallback

The current `OrderService.createOrder` performs these business steps:

1. Calls `inventoryOpenFeignClient.reduceStock(request)` and receives the total price from inventory.
2. Creates fresh `Orders` and `OrderItem` entities, links the items to the order, and sets the price and `CONFIRMED` status.
3. Saves the order and maps it back to the response DTO.

Its resilience annotations currently look like this:

```java
@CircuitBreaker(
    name = "inventoryCircuitBreaker",
    fallbackMethod = "fallbackCreateOrder"
)
public OrderRequestDto createOrder(OrderRequestDto request) {
    Double totalPrice = inventoryOpenFeignClient.reduceStock(request);
    // Build and save the order after inventory confirms stock reduction.
}
```

The name must match a configured instance, such as `inventoryCircuitBreaker`. The two other annotations are currently present but commented out:

```java
// @Retry(name = "inventoryRetry", fallbackMethod = "fallbackCreateOrder")
// @RateLimiter(name = "inventoryRateLimiter", fallbackMethod = "fallbackCreateOrder")
```

Only the circuit breaker is active on `createOrder`; Retry and RateLimiter are commented out. Uncommenting them enables those policies too, provided their named instances are configured. Because these annotations currently surround the **entire `createOrder` method**, not just the Feign call, Resilience4j may record failures from order mapping or database persistence as failures of the inventory circuit breaker. It also means the rate limiter, if enabled here, limits order-creation method invocations, and Retry may re-run the whole method. Prefer wrapping only the remote inventory operation with the dependency-specific policies, especially before enabling retries.

The method is also annotated `@Transactional`. A remote HTTP call inside a database transaction can keep a DB transaction open while the network call waits. A fallback returning normally may allow the method transaction to complete with no saved order; if fallback throws, the transaction typically rolls back. Keep transaction boundaries and fallback semantics intentional.

A fallback method must have compatible arguments and return type; for this method, the form with a cause is:

```java
public OrderRequestDto fallbackCreateOrder(
        OrderRequestDto request,
        Throwable cause) {
    // Log and translate the failure into an explicit API error.
}
```

Resilience4j treats the fallback much like a `catch` block: it selects a compatible fallback overload using the original method arguments plus one final exception parameter. A `Throwable` parameter is a broad fallback; narrower overloads can handle specific cases such as `CallNotPermittedException` (circuit open) or `RequestNotPermitted` (rate limit denied). The fallback must be in the same class and return a compatible type. It is invoked for exceptions from the protected operation as well as policy rejections, regardless of whether the circuit is already open.

The fallback can return a deliberate fallback result, or throw an exception to report failure. For order creation, returning `new OrderRequestDto()` is misleading: the controller may return HTTP 200 even though no order was created. Instead, throw a meaningful application exception from fallback and map it to an HTTP error in a `@RestControllerAdvice`/`@ExceptionHandler` (for example, `503 Service Unavailable` for inventory being unavailable, or `429 Too Many Requests` for rate limiting). Distinguish the cause if the API contract needs different status codes. The fallback should not claim that the order was created.

For example, if the protected operation has been narrowed to the inventory call, conceptually:

```java
public OrderRequestDto fallbackCreateOrder(
        OrderRequestDto request,
        Throwable cause) {
    log.error("Order creation failed during inventory operation", cause);
    throw new InventoryUnavailableException(
            "Order could not be created because inventory is unavailable", cause);
}
```

An exception handler then converts `InventoryUnavailableException` into a structured error response and an appropriate status. Returning or throwing from fallback does not undo side effects already performed by the downstream service; design retries and order creation with idempotency/compensation in mind.

For a simpler application, the fallback can throw Spring's `ResponseStatusException` directly. A more maintainable API can throw a domain-specific exception and translate it centrally with `@RestControllerAdvice`; either way, the client should receive an explicit non-2xx status and useful error body rather than an empty order.

Example central mapping for a domain exception:

```java
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(InventoryUnavailableException.class)
    ResponseEntity<Map<String, String>> handleInventoryUnavailable(
            InventoryUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", exception.getMessage()));
    }
}
```

This is an example pattern; `InventoryUnavailableException` and the handler are not currently in the project.

For example, if translating directly in the fallback:

```java
public OrderRequestDto fallbackCreateOrder(
        OrderRequestDto request,
        Throwable cause) {
    if (cause instanceof RequestNotPermitted) {
        throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Order request rate limit exceeded",
                cause);
    }
    throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Inventory is unavailable; order was not created",
            cause);
}
```

In production, distinguish known causes (rate-limit rejection, open circuit, downstream HTTP error, validation error) rather than mapping every failure to the same status. Do not return an apparently successful order unless it was actually persisted.

Keep the protected boundary focused on the remote inventory operation when possible. If the circuit breaker wraps the entire transactional `createOrder` method, unrelated database or mapping failures can also be counted as circuit-breaker failures for inventory. Separating the protected Feign call from the local persistence transaction makes the policy's meaning clearer. Also consider that an HTTP call inside a database transaction holds that transaction open while waiting for the network.

### Configuration in the current order service

The current `order-service/src/main/resources/application.yaml` configures these named instances:

```yaml
resilience4j:
  retry:
    configs:
      default:
        maxRetryAttempts: 3
        waitDuration: 100ms
    instances:
      inventoryRetry:
        baseConfig: default
        waitDuration: 200ms
  ratelimiter:
    instances:
      inventoryRateLimiter:
        limitRefreshPeriod: 1s
        limitForPeriod: 3
        timeoutDuration: 10ms
  circuitbreaker:
    instances:
      inventoryCircuitBreaker:
        registerHealthIndicator: true
        slidingWindowSize: 10
        slidingWindowType: COUNT_BASED
        minimumNumberOfCalls: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 1s
        permittedNumberOfCallsInHalfOpenState: 3
        eventConsumerBufferSize: 10
```

The current YAML uses `maxRetryAttempts: 3`, but Resilience4j's Spring Boot property is `maxAttempts`. Change it to `maxAttempts: 3` to configure three total tries (the initial attempt plus up to two retries). Until corrected, do not assume that the current YAML actually applies the intended attempt limit. With the corrected key, the default config's wait is `100ms`; `inventoryRetry` overrides it to `200ms`.

For `inventoryRateLimiter`:

| Property | Meaning | Current value |
|---|---|---|
| `limitForPeriod` | Permits available in each refresh period | 3 |
| `limitRefreshPeriod` | How often permits refresh | 1 second |
| `timeoutDuration` | Maximum time a call waits for a permit | 10 ms |

Thus, up to three calls can acquire permits per one-second refresh period. If permits are exhausted, a later call can wait up to `10ms`; it is rejected if a permit does not become available in that time. It does **not** necessarily wait the whole second for the next refresh. Increase the timeout if the desired behavior is to wait longer, while considering the latency added to the request.

For comparison, a configuration of `limitForPeriod: 10`, `limitRefreshPeriod: 1s`, and `timeoutDuration: 500ms` allows up to ten permits per refresh period. An eleventh call with no permit can wait at most 500ms; it proceeds only if a permit becomes available before the timeout, otherwise Resilience4j rejects it. `timeoutDuration` is not a command to wait exactly until the next refresh, and the refresh period should not be confused with a sliding-window throughput guarantee.

For `inventoryCircuitBreaker`:

| Property | Meaning |
|---|---|
| `registerHealthIndicator` | Include circuit-breaker state in Actuator health information; this is observability, not the opening rule. |
| `slidingWindowSize: 10` | Evaluate the most recent 10 calls for a count-based window. |
| `minimumNumberOfCalls: 10` | Do not calculate the failure rate until at least 10 calls are recorded. |
| `failureRateThreshold: 50` | Open when the measured failure rate reaches at least 50%, once the minimum is met. |
| `waitDurationInOpenState: 1s` | Remain open for at least one second before moving to half-open. |
| `permittedNumberOfCallsInHalfOpenState: 3` | Allow up to three probe calls while testing recovery. |
| `eventConsumerBufferSize: 10` | Buffer capacity for circuit-breaker events; it does not affect the failure threshold. |

`minimumNumberOfCalls` and `slidingWindowSize` are both 10 here, so the first failure-rate decision can happen after 10 recorded calls. Five failures out of those ten reaches 50% and opens the circuit. A failure can include a thrown Feign/network error; configure ignored or recorded exception types if some failures should not count.

#### Reusable configs and named instances

`configs` contains reusable templates; `instances` names the actual policy instances that annotations refer to. For example:

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10
        minimumNumberOfCalls: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
    instances:
      inventoryCircuitBreaker:
        baseConfig: default
        waitDurationInOpenState: 1s
      paymentCircuitBreaker:
        baseConfig: default
```

`configs.default` is a shared template, not itself the breaker called `default`. `inventoryCircuitBreaker` and `paymentCircuitBreaker` are actual named instances; each inherits the shared values, and an instance-level setting such as the inventory wait overrides that one value. `@CircuitBreaker(name = "inventoryCircuitBreaker")` selects that instance. Use names that represent the protected downstream dependency/operation; separate instances are useful when dependencies or policies need different settings. The same template-versus-instance idea applies to Retry and RateLimiter configuration.

### Observing state and testing failure behavior

Actuator and circuit-breaker health are configured in the order-service YAML:

```yaml
management:
  health:
    circuitbreakers:
      enabled: true
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always
```

With the order service's `/orders` context path and port `9020`, the circuit-breaker endpoint can be inspected at:

```text
http://localhost:9020/orders/actuator/circuitbreakers
```

The circuit-breakers endpoint lists configured/created breaker names and runtime state/details (for example, `inventoryCircuitBreaker`). Related Resilience4j Actuator endpoints, when exposed and supported by the resolved integration, include:

```text
http://localhost:9020/orders/actuator/circuitbreakers
http://localhost:9020/orders/actuator/circuitbreakerevents
http://localhost:9020/orders/actuator/retries
http://localhost:9020/orders/actuator/retryevents
http://localhost:9020/orders/actuator/ratelimiters
http://localhost:9020/orders/actuator/ratelimiterevents
http://localhost:9020/orders/actuator/health
http://localhost:9020/orders/actuator/metrics
```

The `circuitbreakerevents`, `retryevents`, and `ratelimiterevents` endpoints show recent recorded events (errors, successes, state transitions, retries, or denied permits); buffers are bounded, so they are not permanent logs. The circuit-breaker details can include:

```json
{
  "circuitBreakers": {
    "inventoryCircuitBreaker": {
      "failureRate": "-1.0%",
      "failureRateThreshold": "50.0%",
      "bufferedCalls": 2,
      "failedCalls": 2,
      "slowCalls": 0,
      "notPermittedCalls": 0,
      "state": "HALF_OPEN"
    }
  }
}
```

`state` is CLOSED, OPEN, or HALF_OPEN. `bufferedCalls` is the number of calls recorded in the current window; `failedCalls` and `slowCalls` report recorded outcomes; `notPermittedCalls` counts calls rejected because the breaker was open or otherwise did not permit them. `failureRate` of `-1.0%` means there are not yet enough calls to calculate a rate, not that failures are negative. `failureRateThreshold` is the configured opening threshold. The HALF_OPEN state means the breaker is testing recovery using its limited permitted calls.

The event endpoints expose recent events; they are useful for observing why state changed or why a request was rejected. `/actuator/metrics` lists available metric names, and `/actuator/metrics/resilience4j.circuitbreaker.calls` can show call counts with tags such as circuit-breaker name and outcome. A metric is an aggregate measurement, whereas the event endpoints show recent individual events.

Actuator reports **runtime status, events, and metrics**; it does not necessarily expose every effective YAML setting as a configuration dump. Check the Actuator root at `/orders/actuator` for the links exposed by the running application, because available endpoints depend on the integration/version and exposure settings. The health endpoint is `/orders/actuator/health`; with circuit-breaker health enabled it can report breaker health as a health contributor. `registerHealthIndicator: true` exposes circuit-breaker health information; it does not decide when the breaker opens. An OPEN breaker can cause the aggregate health status to be DOWN, which may or may not be appropriate for the whole application's readiness policy.

The current YAML uses `management.endpoints.web.exposure.include: "*"`, which exposes every web Actuator endpoint, and `show-details: always`. This is convenient locally but unsafe as a production default: expose only required endpoints and secure them.

To test locally, start Eureka, order-service, and inventory-service. Create an order using `POST http://localhost:9020/orders/core/create-order` with a JSON body such as:

```json
{
  "items": [
    { "productId": 1, "quantity": 2 }
  ]
}
```

A successful inventory response should be followed by a saved order and a `CLOSED` breaker. Stop inventory-service (or deliberately make its reduce-stock endpoint fail) and submit enough requests to reach the configured minimum of 10 recorded calls. Observe failed calls and the transition to `OPEN`; further calls should not reach inventory while open, and should invoke fallback. After the one-second open wait, the breaker moves to `HALF_OPEN` and permits up to three actual probe calls. Restart inventory before those probes: successful probes close the circuit; failing probes open it again.

In the current implementation, the fallback returns an empty `OrderRequestDto`, and the controller wraps that return in `ResponseEntity.ok(...)`; therefore a failed create request may look like HTTP 200 despite creating no order. Change the fallback to throw/map an explicit error before treating that response as correct.

Test retry and rate limiting separately as well: with retry enabled, a transient failure may recover on a later attempt; with the current rate-limit configuration, rapid bursts can exhaust three permits in a refresh period and a request may fail after waiting up to `10ms`. The fallback log currently prints the cause. Avoid returning an empty order DTO in this scenario; make the failure explicit to the API client.

### Current project configuration note

Inventory, order, and discovery currently use Spring Boot `3.5.16` with Spring Cloud `2025.0.0`. The API gateway currently uses Spring Boot `3.3.4` with Spring Cloud `2023.0.3`; those versions form a separate older release train. Align versions deliberately across the project and verify Boot/Cloud compatibility when upgrading.
