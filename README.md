# PriceFetcher - Market Data Microservice

PriceFetcher es un microservicio Spring Boot (Kotlin) que proporciona cotizaciones en tiempo real y datos históricos de mercado. Mantiene un cache en memoria y administra una watchlist de símbolos para monitoreo automático.

## 🚀 Características

- **Cotizaciones en tiempo real**: Cache en memoria con TTL configurable
- **Datos históricos**: Almacenamiento en cache con múltiples timeframes  
- **Watchlist**: CRUD completo para gestión de símbolos a monitorear
- **Scheduler automático**: Actualización periódica de cotizaciones
- **Múltiples proveedores**: Soporte para Finnhub (quotes) y Polygon (históricos)
- **Health checks**: Endpoint de salud con información de cache y mercado

## 📋 Requisitos

- **Java 21+**
- **API Keys** (opcionales para testing):
  - Finnhub API Key: [https://finnhub.io](https://finnhub.io)
  - Polygon API Key: [https://polygon.io](https://polygon.io)

## 🛠️ Instalación y Ejecución

### Opción 1: Con Gradle (Desarrollo)

```bash
# Clonar repositorio
git clone <repo-url>
cd price-fetcher

# Configurar variables de entorno (opcional)
export FINNHUB_API_KEY=tu_finnhub_key
export POLYGON_API_KEY=tu_polygon_key

# Ejecutar aplicación
./gradlew bootRun

# Ejecutar tests
./gradlew test

# Verificar formato de código
./gradlew ktlintCheck
```

### Opción 2: Con Docker

```bash
# Construir imagen
docker build -t price-fetcher .

# Ejecutar con variables de entorno
docker run -p 8080:8080 \
  -e FINNHUB_API_KEY=tu_key \
  -e POLYGON_API_KEY=tu_key \
  price-fetcher
```

### Opción 3: Docker Compose

```yaml
# docker-compose.yml
version: '3.8'
services:
  price-fetcher:
    build: .
    ports:
      - "8080:8080"
    environment:
      - FINNHUB_API_KEY=${FINNHUB_API_KEY}
      - POLYGON_API_KEY=${POLYGON_API_KEY}
```

```bash
docker-compose up
```

## 📡 API Endpoints Completos

### 🏥 Health & Monitoring

#### `GET /health`
**Descripción**: Endpoint de salud que proporciona información completa del estado de la aplicación, incluyendo tamaños de cache, estado del mercado y estadísticas de la watchlist.

**Parámetros**: Ninguno

**Respuesta**:
```json
{
  "status": "UP",
  "timestamp": "2024-01-15T10:30:00Z",
  "market": {
    "phase": "REGULAR",
    "timezone": "America/New_York"
  },
  "cache": {
    "quotes": 150,
    "candles": 12,
    "symbols": ["AAPL", "TSLA", "MSFT"]
  },
  "watchlist": {
    "total": 25,
    "enabled": 20,
    "disabled": 5,
    "enabled_symbols": ["AAPL", "TSLA"]
  },
  "version": {
    "app": "price-fetcher",
    "version": "0.0.1-SNAPSHOT"
  }
}
```

**Casos de uso**:
- Monitoreo de salud del servicio
- Verificar tamaños de cache
- Conocer fase actual del mercado
- Debugging y observabilidad

---

### 💰 Cotizaciones en Tiempo Real

#### `GET /quotes?symbols=AAPL,TSLA,MSFT.MX`
**Descripción**: Obtiene las cotizaciones actuales para una lista de símbolos. Los datos provienen del cache en memoria y son actualizados automáticamente por el scheduler. No realiza fetch bajo demanda.

**Parámetros**:
- `symbols` *(requerido)*: Lista de símbolos separados por coma. Soporta formato `TICKER` o `TICKER.EXCHANGE`. Máximo 50 símbolos.

**Ejemplos de símbolos válidos**:
- `AAPL` - Apple (NASDAQ)
- `TSLA` - Tesla
- `MSFT.MX` - Microsoft en mercado mexicano

**Respuesta exitosa** (200):
```json
[
  {
    "symbol": "AAPL",
    "last": 172.35,
    "open": 171.00,
    "high": 173.00,
    "low": 170.50,
    "prevClose": 170.90,
    "currency": "USD",
    "source": "FINNHUB",
    "ts": "2024-01-15T15:30:00Z",
    "stale": false
  },
  {
    "symbol": "TSLA",
    "last": 248.50,
    "open": 245.00,
    "high": 250.00,
    "low": 244.75,
    "prevClose": 246.80,
    "currency": "USD",
    "source": "FINNHUB", 
    "ts": "2024-01-15T15:30:00Z",
    "stale": true
  }
]
```

**Errores**:
- `400`: Parámetro symbols vacío, mal formado, o demasiados símbolos (>50)
- `404`: Uno o más símbolos no encontrados en cache

**Campo `stale`**: 
- `false`: Dato fresco (< 45 segundos)
- `true`: Dato obsoleto (> 45 segundos), será actualizado por el scheduler

**Casos de uso**:
- Obtener precios actuales para mostrar en UI
- Validar precios antes de ejecutar operaciones
- Monitorear múltiples símbolos simultáneamente

---

### 📊 Datos Históricos

#### `GET /historical?symbol=AAPL&from=2024-01-01T00:00:00Z&to=2024-01-02T00:00:00Z&tf=T1D&adjusted=true`
**Descripción**: Obtiene datos históricos de candles (OHLCV) para un símbolo específico. Los datos se cachean permanentemente - la primera llamada consulta el proveedor (Polygon), las siguientes devuelven desde cache.

**Parámetros**:
- `symbol` *(requerido)*: Símbolo individual (ej: AAPL, MSFT.MX)
- `from` *(requerido)*: Fecha inicio en formato ISO-8601 (ej: 2024-01-01T00:00:00Z)
- `to` *(requerido)*: Fecha fin en formato ISO-8601
- `tf` *(opcional)*: Timeframe - valores: `T1M`, `T5M`, `T15M`, `T1H`, `T1D` (default: T1D)
- `adjusted` *(opcional)*: Precios ajustados por splits/dividendos - true/false (default: true)

**Respuesta exitosa** (200):
```json
{
  "symbol": {
    "ticker": "AAPL",
    "exchange": null
  },
  "timeframe": "T1D",
  "items": [
    {
      "ts": "2024-01-01T09:30:00Z",
      "o": 170.00,
      "h": 172.00,
      "l": 169.50,
      "c": 171.10,
      "v": 1000000,
      "adjusted": true
    },
    {
      "ts": "2024-01-02T09:30:00Z", 
      "o": 171.10,
      "h": 173.50,
      "l": 170.80,
      "c": 172.85,
      "v": 1200000,
      "adjusted": true
    }
  ]
}
```

**Explicación de campos**:
- `ts`: Timestamp del candle
- `o`: Precio de apertura (Open)
- `h`: Precio máximo (High)
- `l`: Precio mínimo (Low)
- `c`: Precio de cierre (Close)
- `v`: Volumen negociado
- `adjusted`: Si el precio está ajustado

**Errores**:
- `400`: Parámetros faltantes, fechas mal formadas, timeframe inválido, o fecha from > to
- `500`: Error del proveedor externo

**Casos de uso**:
- Generar gráficos de precios históricos
- Análisis técnico y backtesting
- Reportes de rendimiento histórico

---

### 🔄 Prefetch Manual

#### `POST /prefetch`
**Descripción**: Fuerza la obtención inmediata de cotizaciones para todos los símbolos habilitados en la watchlist. Útil para refrescar el cache manualmente o inicializar datos al arrancar.

**Parámetros**: Ninguno

**Body**: No requerido

**Respuesta exitosa** (200):
```json
{
  "prefetched": 15,
  "symbols": ["AAPL", "TSLA", "MSFT", "GOOGL", "AMZN"]
}
```

**Respuesta sin símbolos** (200):
```json
{
  "prefetched": 0,
  "symbols": [],
  "message": "No enabled symbols in watchlist"
}
```

**Errores**:
- `500`: Error al conectar con proveedores externos

**Comportamiento**:
- Solo procesa símbolos con `enabled: true` en la watchlist
- Actualiza el cache de quotes inmediatamente
- Marca los datos como fresh (stale: false)
- Tolerante a fallos: si algún símbolo falla, continúa con los demás

**Casos de uso**:
- Inicialización manual del cache
- Refresh forzado después de cambios en watchlist
- Testing y debugging

---

### 👁️ Watchlist Management

#### `GET /watchlist`
**Descripción**: Obtiene la lista completa de símbolos en la watchlist con su configuración. Los símbolos se ordenan por prioridad (menor número = mayor prioridad) y luego alfabéticamente.

**Parámetros**: Ninguno

**Respuesta** (200):
```json
[
  {
    "symbol": "AAPL",
    "enabled": true,
    "priority": 1
  },
  {
    "symbol": "TSLA",
    "enabled": true,
    "priority": 2
  },
  {
    "symbol": "MSFT",
    "enabled": false,
    "priority": null
  }
]
```

**Campos**:
- `symbol`: Identificador del símbolo (normalizado a mayúsculas)
- `enabled`: Si está habilitado para el scheduler automático
- `priority`: Prioridad para procesamiento (null = sin prioridad específica)

**Casos de uso**:
- Mostrar configuración actual de la watchlist
- Administración de símbolos monitoreados
- Debugging del scheduler

---

#### `POST /watchlist`
**Descripción**: Agrega un nuevo símbolo a la watchlist. El símbolo se normaliza automáticamente (trim + uppercase) y no puede estar duplicado.

**Body** (application/json):
```json
{
  "symbol": "AAPL",
  "enabled": true,
  "priority": 1
}
```

**Campos**:
- `symbol` *(requerido)*: Identificador del símbolo
- `enabled` *(opcional)*: Si debe ser procesado por el scheduler (default: true)
- `priority` *(opcional)*: Prioridad de procesamiento (default: null)

**Respuesta exitosa** (200):
```json
{}
```

**Errores**:
- `400`: Symbol vacío, mal formado, o ya existe

**Ejemplos de errores**:
```json
{
  "error": "symbol is required"
}
```
```json
{
  "error": "symbol already exists" 
}
```

**Casos de uso**:
- Agregar nuevos símbolos para monitoreo
- Configurar prioridades de procesamiento
- Inicialización de watchlist

---

#### `PATCH /watchlist/{symbol}`
**Descripción**: Actualiza la configuración de un símbolo existente en la watchlist. Permite modificar solo los campos especificados sin afectar los demás.

**Parámetros de URL**:
- `{symbol}`: Símbolo a actualizar (case-insensitive)

**Body** (application/json):
```json
{
  "enabled": false,
  "priority": 5
}
```

**Campos** (todos opcionales):
- `enabled`: Habilitar/deshabilitar para scheduler
- `priority`: Nueva prioridad (null para remover)

**Respuesta exitosa** (204): Sin contenido

**Errores**:
- `400`: Símbolo no encontrado

**Ejemplo de error**:
```json
{
  "error": "symbol not found"
}
```

**Comportamiento**:
- `enabled: false` → El símbolo se excluye del scheduler y prefetch
- `priority: null` → Remueve la prioridad específica
- Los campos no especificados mantienen su valor actual

**Casos de uso**:
- Pausar temporalmente un símbolo (enabled: false)
- Cambiar prioridades dinámicamente
- Reactivar símbolos pausados

---

#### `DELETE /watchlist/{symbol}`
**Descripción**: Elimina completamente un símbolo de la watchlist. El símbolo dejará de ser procesado por el scheduler inmediatamente.

**Parámetros de URL**:
- `{symbol}`: Símbolo a eliminar (case-insensitive)

**Respuesta exitosa** (204): Sin contenido

**Errores**:
- `400`: Símbolo no encontrado

**Ejemplo de error**:
```json
{
  "error": "symbol not found"
}
```

**Comportamiento**:
- Elimina el símbolo permanentemente de la watchlist
- Los datos en cache de quotes NO se eliminan
- El scheduler dejará de actualizar este símbolo inmediatamente

**Casos de uso**:
- Limpiar símbolos que ya no se monitoreán
- Reducir carga del scheduler
- Gestión de la watchlist

---

### ℹ️ Información y Metadata

#### `GET /info/endpoints`
**Descripción**: Devuelve documentación interactiva de todos los endpoints disponibles con sus parámetros y descripciones. Útil para discovery y documentación automática.

**Parámetros**: Ninguno

**Respuesta** (200):
```json
{
  "endpoints": [
    {
      "path": "/health",
      "method": "GET", 
      "description": "Application health and cache status"
    },
    {
      "path": "/quotes",
      "method": "GET",
      "description": "Get current quotes for symbols",
      "parameters": {
        "symbols": "Comma-separated list of symbols (e.g., AAPL,TSLA,MSFT.MX)"
      }
    },
    {
      "path": "/historical",
      "method": "GET",
      "description": "Get historical candle data",
      "parameters": {
        "symbol": "Single symbol (e.g., AAPL)",
        "from": "Start date in ISO-8601 format (e.g., 2024-01-01T00:00:00Z)",
        "to": "End date in ISO-8601 format (e.g., 2024-01-02T00:00:00Z)",
        "tf": "Timeframe (optional, default: T1D)",
        "adjusted": "Whether to use adjusted prices (optional, default: true)"
      }
    },
    {
      "path": "/prefetch",
      "method": "POST",
      "description": "Force fetch current quotes for all enabled watchlist symbols"
    },
    {
      "path": "/watchlist",
      "method": "GET",
      "description": "Get all symbols in watchlist"
    },
    {
      "path": "/watchlist",
      "method": "POST", 
      "description": "Add symbol to watchlist",
      "body": {
        "symbol": "Symbol to add (required)",
        "enabled": "Whether symbol is enabled (optional, default: true)",
        "priority": "Symbol priority (optional)"
      }
    },
    {
      "path": "/watchlist/{symbol}",
      "method": "PATCH",
      "description": "Update symbol in watchlist",
      "body": {
        "enabled": "Whether symbol is enabled (optional)",
        "priority": "Symbol priority (optional)"
      }
    },
    {
      "path": "/watchlist/{symbol}",
      "method": "DELETE",
      "description": "Remove symbol from watchlist"
    }
  ]
}
```

**Casos de uso**:
- Discovery automático de API
- Documentación interactiva
- Herramientas de testing automático

---

#### `GET /info/timeframes`
**Descripción**: Lista todos los timeframes soportados para datos históricos con sus descripciones legibles y recomendaciones de uso.

**Parámetros**: Ninguno

**Respuesta** (200):
```json
{
  "available_timeframes": [
    {
      "value": "T1M",
      "description": "1 minute"
    },
    {
      "value": "T5M", 
      "description": "5 minutes"
    },
    {
      "value": "T15M",
      "description": "15 minutes"
    },
    {
      "value": "T1H",
      "description": "1 hour"
    },
    {
      "value": "T1D",
      "description": "1 day"
    }
  ],
  "default": "T1D",
  "usage": "Use the 'value' field as the 'tf' parameter in /historical endpoint"
}
```

**Casos de uso**:
- Construir UI con selección de timeframes
- Validación de parámetros en cliente
- Documentación de opciones disponibles

---

## ⚙️ Configuración

### Variables de Entorno

```bash
# APIs de proveedores
FINNHUB_API_KEY=tu_finnhub_key
POLYGON_API_KEY=tu_polygon_key

# Configuración de aplicación (opcional)
PORT=8080
NEW_RELIC_APP_NAME=price-fetcher
```

### Archivo de Configuración

```properties
# application.properties

# Proveedor principal para quotes
pricefetcher.providers.primary=FINNHUB

# Configuración de APIs
pricefetcher.providers.finnhub.base-url=https://finnhub.io/api/v1
pricefetcher.providers.finnhub.api-key=${FINNHUB_API_KEY:}
pricefetcher.providers.polygon.base-url=https://api.polygon.io  
pricefetcher.providers.polygon.api-key=${POLYGON_API_KEY:}

# Configuración de mercado
pricefetcher.market.timezone=America/New_York
pricefetcher.market.schedule.premarket=06:00-09:30
pricefetcher.market.schedule.regular=09:30-16:00
pricefetcher.market.schedule.after=16:00-20:00

# Configuración de cotizaciones
pricefetcher.quotes.refresh-ms=2000  # Frecuencia del scheduler
pricefetcher.quotes.ttl-seconds=45   # TTL para marcar quotes como stale
```

## 🔄 Funcionamiento Automático

### Scheduler de Cotizaciones

- **Frecuencia**: Cada 2 segundos (configurable)
- **Comportamiento**: Obtiene cotizaciones para símbolos habilitados en watchlist
- **Batch size**: Varía según fase de mercado (PRE/REGULAR/AFTER/NIGHT)
- **Tolerancia a fallos**: Continúa con otros símbolos si alguno falla

### Cache y TTL

- **Quotes**: TTL de 45 segundos, después se marcan como `stale=true`
- **Historical**: Cache permanente hasta reinicio de aplicación
- **Thread-safe**: Implementado con `ConcurrentHashMap`

### Fases de Mercado

- **PRE**: 06:00-09:30 (Eastern Time)
- **REGULAR**: 09:30-16:00 (Eastern Time)  
- **AFTER**: 16:00-20:00 (Eastern Time)
- **NIGHT**: Resto del tiempo

## 🧪 Testing y Desarrollo

### Ejecutar Tests

```bash
# Todos los tests
./gradlew test

# Tests específicos
./gradlew test --tests "*QuotesControllerTest"

# Con coverage
./gradlew test jacocoTestReport
```

### Formato de Código

```bash
# Verificar formato
./gradlew ktlintCheck

# Corregir formato automáticamente
./gradlew ktlintFormat
```

### Testing con APIs Reales

Si tienes API keys reales, puedes probar:

```bash
# Agregar símbolos a watchlist
curl -X POST http://localhost:8080/watchlist \
  -H "Content-Type: application/json" \
  -d '{"symbol": "AAPL", "enabled": true}'

# Forzar prefetch
curl -X POST http://localhost:8080/prefetch

# Obtener cotizaciones
curl "http://localhost:8080/quotes?symbols=AAPL,TSLA"

# Obtener históricos
curl "http://localhost:8080/historical?symbol=AAPL&from=2024-01-01T00:00:00Z&to=2024-01-02T00:00:00Z&tf=T1D"

# Ver información de endpoints
curl http://localhost:8080/info/endpoints

# Ver timeframes disponibles  
curl http://localhost:8080/info/timeframes
```

## 🚨 Troubleshooting

### Problemas Comunes

1. **Error 404 en `/quotes`**: Símbolo no está en cache
   - **Solución**: Agregar a watchlist y esperar scheduler, o usar `/prefetch`

2. **APIs rate limited**: 
   - **Solución**: Logs mostrarán warnings, el servicio continuará con otros símbolos

3. **Cache vacío**:
   - **Solución**: Verificar API keys y conectividad en `/health`

4. **Datos stale**:
   - **Solución**: Normal si TTL expiró, scheduler actualizará automáticamente

### Logs Útiles

```bash
# Ver logs en tiempo real
docker logs -f <container-id>

# Buscar errores específicos
docker logs <container-id> 2>&1 | grep ERROR
```

## 🏗️ Arquitectura

- **Proveedores**: Finnhub (quotes) + Polygon (históricos)
- **Cache**: In-memory con ConcurrentHashMap
- **Persistencia**: Ninguna (MVP), se pierde al reiniciar
- **Observabilidad**: Logs estructurados + endpoint /health
- **Concurrencia**: Thread-safe para operaciones simultáneas

## 📄 Licencia

[Añadir licencia según corresponda]

---

Para más información técnica, revisar el archivo `CursorContext` en el repositorio.
