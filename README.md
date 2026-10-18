# E-commerce microservices

## Run with Docker Compose

Install Docker Desktop, open a terminal in this directory, then run:

```powershell
docker compose up --build
```

The first run builds all five Spring Boot services and may take several minutes. The API gateway is available at `http://localhost:8080`, Eureka at `http://localhost:8761`, Zipkin at `http://localhost:9411`, and the Config Server at `http://localhost:8888`.

Configuration is served locally by the Config Server from `config-repo/`; edit `order-service.yaml`, `inventory-service.yaml`, or `api-gateway.yaml` there to change service settings. Service-to-service URLs and database hostnames must use the Compose service names (such as `order-db` and `discovery-service`), not `localhost`. Recreate the affected service after changing config:

```powershell
docker compose up --build -d order-service
```

The Compose file uses `user` / `password` and a development JWT signing key by default. Set `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `JWT_SECRET_KEY` in the environment before starting the stack when using non-development values. Database contents persist in named volumes; `docker compose down` stops the stack without deleting them.
