# Guia local - price-fetcher

## Que es
Servicio de cotizaciones y datos historicos de mercado.

## Prerrequisitos
- Java 21
- (Opcional) Docker para correr dentro del stack del gateway

## Configuracion
Definir variables requeridas, por ejemplo en `.env`:
- `FINNHUB_API_KEY`
- `POLYGON_API_KEY` (opcional)
- `SERVER_PORT` (default local: `8081`)
- `SPRING_PROFILES_ACTIVE` (`auth` o `default`)
- Si usas `auth`: `JWT_ISSUER_URI`, `JWT_AUDIENCE`

## Correr en local (Gradle)

```bash
set -a
source .env
set +a
./gradlew bootRun
```

## Correr dentro del stack completo
Desde `gateway-api/`:

```bash
docker compose up -d price-fetcher
```

## Uso directo
Base URL local:
- `http://localhost:8081`

Ejemplos:

```bash
curl "http://localhost:8081/health"

curl "http://localhost:8081/quotes?symbols=AAPL,MSFT"

curl "http://localhost:8081/historical?symbol=AAPL&from=2025-01-01T00:00:00Z&to=2025-01-31T23:59:59Z&tf=T1D"
```

Si `auth` esta activo, agregar `Authorization: Bearer <jwt>`.

## Uso via gateway

```bash
curl -H "Authorization: Bearer <jwt>" \
  "http://localhost:8080/api/prices/quotes?symbols=AAPL,MSFT"
```

## Referencias
- `README.md`
- `docs/endpoints.md`
