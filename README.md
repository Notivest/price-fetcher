# 📈 Price Fetcher Service

Un microservicio robusto y escalable para obtener cotizaciones de acciones en tiempo real y datos históricos, construido con **Spring Boot** y **Kotlin**.

## 🚀 Características Principales

- **🔄 Cotizaciones en Tiempo Real**: Integración con Finnhub y Polygon APIs
- **📊 Datos Históricos**: Velas (candles) con diferentes timeframes
- **🔒 Seguridad Dual**: Soporte para usuarios (vía Gateway) y servicios M2M
- **📝 Watchlist**: Gestión de símbolos favoritos con prioridades
- **⚡ Caché Inteligente**: Sistema de caché en memoria con TTL configurable
- **📋 Auditoría**: Sistema completo de logging para compliance
- **🏥 Health Checks**: Monitoreo y métricas integradas
- **🔧 Configuración Flexible**: Perfiles para desarrollo, testing y producción

## 🏗️ Arquitectura

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   API Gateway   │────│  Price Fetcher   │────│  External APIs  │
│  (Users/Auth)   │    │    Service       │    │ (Finnhub/Polygon)│
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                       ┌────────┴────────┐
                       │   In-Memory     │
                       │     Cache       │
                       └─────────────────┘
```

### 📦 Componentes Principales

- **Controllers**: Endpoints REST con validación de seguridad
- **Services**: Lógica de negocio y orchestración
- **Clients**: Integración con APIs externas (Finnhub/Polygon)
- **Repositories**: Gestión de caché en memoria
- **Security**: Autenticación dual (usuarios + servicios M2M)
- **Models**: DTOs y entidades de dominio

## 🛠️ Stack Tecnológico

- **Lenguaje**: Kotlin 1.9.25
- **Framework**: Spring Boot 3.4.6
- **JVM**: Java 21
- **Seguridad**: Spring Security + OAuth2 JWT
- **HTTP Client**: WebFlux WebClient
- **Testing**: JUnit 5 + Mockito
- **Build**: Gradle
- **Linting**: KtLint

## 🚀 Inicio Rápido

### Prerrequisitos

- **Java 21** o superior
- **API Keys**:
  - Finnhub API Key ([obtener aquí](https://finnhub.io/))
  - Polygon API Key ([obtener aquí](https://polygon.io/)) - opcional

### 1. Clonar el Repositorio

```bash
git clone <repository-url>
cd price-fetcher
```

### 2. Configurar Variables de Entorno

```bash
# APIs de cotizaciones
export FINNHUB_API_KEY=your_finnhub_api_key
export POLYGON_API_KEY=your_polygon_api_key  # opcional

# Configuración de Auth0 (solo para producción)
export AUTH0_DOMAIN=your-domain.auth0.com
```

### 3. Ejecutar en Modo Desarrollo

```bash
# Modo desarrollo (sin autenticación)
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### 4. Verificar que Funciona

```bash
# Health check
curl http://localhost:8080/health

# Obtener cotizaciones (modo dev)
curl "http://localhost:8080/quotes?symbols=AAPL,TSLA,MSFT"
```

## 📋 API Endpoints

### 🏥 Health & Monitoring

| Endpoint | Método | Descripción |
|----------|---------|-------------|
| `/health` | GET | Estado del servicio y métricas |
| `/actuator/health` | GET | Spring Boot health checks |

### 📊 Cotizaciones

| Endpoint | Método | Descripción | Scopes Requeridos |
|----------|---------|-------------|-------------------|
| `/quotes` | GET | Cotizaciones actuales | `read:prices` |
| `/historical` | GET | Datos históricos | `read:market-data` |
| `/prefetch` | POST | Forzar actualización | `write:prices` |

### 📝 Watchlist

| Endpoint | Método | Descripción | Scopes Requeridos |
|----------|---------|-------------|-------------------|
| `/watchlist` | GET | Listar símbolos | `read:prices` |
| `/watchlist` | POST | Agregar símbolo | `write:prices` |
| `/watchlist/{symbol}` | PATCH | Actualizar símbolo | `write:prices` |
| `/watchlist/{symbol}` | DELETE | Eliminar símbolo | `write:prices` |

### 📖 Ejemplos de Uso

#### Obtener Cotizaciones

```bash
# Múltiples símbolos
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8080/quotes?symbols=AAPL,TSLA,MSFT"

# Respuesta
{
  "quotes": [
    {
      "symbol": "AAPL",
      "last": 150.25,
      "open": 149.80,
      "high": 151.00,
      "low": 149.50,
      "prevClose": 149.90,
      "currency": "USD",
      "source": "FINNHUB",
      "ts": "2024-01-15T15:30:00Z",
      "stale": false
    }
  ]
}
```

#### Datos Históricos

```bash
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8080/historical?symbol=AAPL&from=2024-01-01T00:00:00Z&to=2024-01-02T00:00:00Z&tf=T1D"
```

#### Gestionar Watchlist

```bash
# Agregar símbolo
curl -X POST -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"symbol":"AAPL","enabled":true,"priority":1}' \
  http://localhost:8080/watchlist

# Actualizar símbolo
curl -X PATCH -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"enabled":false}' \
  http://localhost:8080/watchlist/AAPL
```

## 🔒 Autenticación y Autorización

El servicio soporta **dos tipos de autenticación**:

### 1. 👥 Usuarios (vía API Gateway)

Para usuarios finales que acceden a través del API Gateway:

```bash
curl -H "X-User-ID: user123" \
     -H "X-User-Scopes: read:prices,user:profile" \
     -H "X-Session-ID: session456" \
     "http://localhost:8080/quotes?symbols=AAPL"
```

### 2. 🤖 Servicios (M2M)

Para comunicación service-to-service con JWT:

```bash
curl -H "Authorization: Bearer <jwt-token>" \
     -H "X-Service-Name: portfolio-service" \
     "http://localhost:8080/quotes?symbols=AAPL"
```

### 🔑 Scopes Disponibles

| Scope | Descripción |
|-------|-------------|
| `read:prices` | Leer cotizaciones y watchlist |
| `write:prices` | Modificar watchlist y prefetch |
| `read:market-data` | Acceso a datos históricos |
| `service:internal` | Operaciones internas entre servicios |

## ⚙️ Configuración

### 🔧 Variables de Entorno

| Variable | Descripción | Requerida | Default |
|----------|-------------|-----------|---------|
| `FINNHUB_API_KEY` | API Key de Finnhub | ✅ | - |
| `POLYGON_API_KEY` | API Key de Polygon | ❌ | - |
| `AUTH0_DOMAIN` | Dominio de Auth0 | ✅ (prod) | your-domain.auth0.com |

### 📁 Perfiles de Configuración

#### Desarrollo (`dev`)
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```
- ❌ Sin autenticación
- 📝 Logs verbosos
- 🚫 Sin auditoría

#### Testing (`test`)
```bash
./gradlew test
```
- 🧪 Configuración para tests
- 🎭 Mocks de APIs externas

#### Producción (`prod`)
```bash
java -jar price-fetcher.jar --spring.profiles.active=prod
```
- ✅ Autenticación completa
- 📋 Auditoría habilitada
- 🔒 Validación JWT

### 🎛️ Configuraciones Principales

```properties
# Proveedor de datos (FINNHUB o POLYGON)
pricefetcher.providers.primary=FINNHUB

# Configuración de mercado
pricefetcher.market.timezone=America/New_York
pricefetcher.market.schedule.regular=09:30-16:00

# Configuración de cotizaciones
pricefetcher.quotes.refresh-ms=2000
pricefetcher.quotes.ttl-seconds=45

# Seguridad
pricefetcher.security.enabled=true
pricefetcher.security.audit.enabled=true
```

## 🏃‍♂️ Desarrollo

### 🔧 Setup de Desarrollo

```bash
# Instalar dependencias
./gradlew build

# Ejecutar tests
./gradlew test

# Linting
./gradlew ktlintCheck

# Formatear código
./gradlew ktlintFormat

# Ejecutar en modo desarrollo
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### 🧪 Testing

```bash
# Todos los tests
./gradlew test

# Tests específicos
./gradlew test --tests "*SecurityTest*"

# Tests con coverage
./gradlew test jacocoTestReport
```

### 📝 Estructura del Proyecto

```
src/main/kotlin/com/notivest/pricefetcher/
├── 🎮 controllers/          # REST endpoints
├── 🏢 service/             # Lógica de negocio
├── 🌐 client/              # Integración APIs externas
├── 💾 repositories/        # Gestión de datos/caché
├── 🏗️ models/             # Entidades de dominio
├── 📦 dto/                # Data Transfer Objects
├── 🔒 security/           # Autenticación y autorización
└── ⚙️ config/             # Configuraciones
```

### 🔍 Debugging

#### Logs de Desarrollo
```bash
# Habilitar logs debug para el servicio
logging.level.com.notivest.pricefetcher=DEBUG

# Logs de seguridad
logging.level.com.notivest.pricefetcher.security=DEBUG
```

#### Monitoreo de Requests
Todos los requests incluyen automáticamente en los logs:
- `requestId`: ID único del request
- `method`: Método HTTP
- `uri`: Endpoint llamado
- `callType`: "gateway" o "service"
- `callerId`: Identificación del caller

## 🚀 Despliegue

### 🐳 Docker

```dockerfile
# Dockerfile incluido
docker build -t price-fetcher .
docker run -p 8080:8080 \
  -e FINNHUB_API_KEY=your_key \
  -e AUTH0_DOMAIN=your-domain.auth0.com \
  price-fetcher
```

### ☁️ Producción

1. **Configurar variables de entorno**:
   ```bash
   export SPRING_PROFILES_ACTIVE=prod
   export FINNHUB_API_KEY=your_production_key
   export AUTH0_DOMAIN=your-auth0-domain.auth0.com
   ```

2. **Construir aplicación**:
   ```bash
   ./gradlew bootJar
   ```

3. **Ejecutar**:
   ```bash
   java -jar build/libs/price-fetcher-0.0.1-SNAPSHOT.jar
   ```

## 📊 Monitoreo y Observabilidad

### 🏥 Health Checks

```bash
curl http://localhost:8080/health
```

Respuesta incluye:
- ✅ Estado del servicio
- 📊 Métricas de caché
- 📈 Estado del mercado
- 📝 Información de watchlist
- 🔢 Versión de la aplicación

### 📋 Auditoría

Los siguientes eventos se auditan automáticamente:
- 🔐 Intentos de autenticación
- 🚫 Fallos de autorización
- 🔍 Validación de scopes
- 📞 Llamadas a endpoints
- ⏱️ Duración de requests

Logs de auditoría usan el logger `AUDIT` separado para compliance.

### 📈 Métricas

- **Cache hits/misses**
- **Duración de requests**
- **Errores por endpoint**
- **Calls por caller type**

## 🤝 Contribuir

### 📋 Proceso de Desarrollo

1. **Fork** el repositorio
2. **Crear** branch de feature: `git checkout -b feature/nueva-funcionalidad`
3. **Commits** siguiendo [Conventional Commits](https://www.conventionalcommits.org/)
4. **Tests**: Asegurar 100% de cobertura para nuevas funcionalidades
5. **Linting**: Ejecutar `./gradlew ktlintFormat`
6. **Pull Request** con descripción detallada

### ✅ Checklist para PRs

- [ ] Tests unitarios agregados/actualizados
- [ ] Documentación actualizada
- [ ] Linting pasando (`./gradlew ktlintCheck`)
- [ ] Tests pasando (`./gradlew test`)
- [ ] Configuración de seguridad revisada
- [ ] Logs de auditoría apropiados

## 📄 Licencia

Este proyecto está bajo la licencia MIT. Ver [LICENSE](LICENSE) para más detalles.

## 🆘 Soporte

### 🐛 Reportar Bugs

1. Verificar que no exista un issue similar
2. Incluir pasos para reproducir
3. Incluir logs relevantes
4. Especificar versión y entorno

### 💡 Solicitar Features

1. Describir el caso de uso
2. Explicar el beneficio esperado
3. Proponer implementación si es posible

### 📞 Contacto

- **Issues**: [GitHub Issues](link-to-issues)
- **Discusiones**: [GitHub Discussions](link-to-discussions)
- **Email**: [team@notivest.com](mailto:team@notivest.com)

---

## 📚 Documentación Adicional

- [🔒 Guía de Seguridad](SECURITY_GUIDE.md) - Configuración detallada de seguridad
- [🏗️ Arquitectura](docs/ARCHITECTURE.md) - Diseño técnico detallado
- [🚀 Despliegue](docs/DEPLOYMENT.md) - Guías de despliegue por entorno
- [📋 API Reference](docs/API.md) - Documentación completa de endpoints

---

*Construido con ❤️ por el equipo de Notivest*
