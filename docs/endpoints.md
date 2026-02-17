# API Endpoints

Este documento describe los endpoints expuestos por el servicio, con payloads, parametros y respuestas principales segun el codigo actual.

## Convenciones

- Formato de respuesta: JSON.
- Fechas: ISO-8601 (ej: 2024-01-01T00:00:00Z).
- Formato de simbolo (SymbolId):
  - Sin exchange: "AAPL"
  - Con exchange: "AAPL.NASDAQ"
  - Si hay varios puntos, el ultimo segmento es el exchange (ej: "BRK.B.NYSE").
- Timeframes validos: `T1D`, `T1H`, `T15M`, `T5M`, `T1M`.
- Errores: en 400/500 suele devolverse `{ "error": "..." }` y a veces un `message` extra.

## GET /health

**Que hace:** devuelve estado del servicio, cache y watchlist, y snapshot de mercado.

**Respuesta 200 (schema):**
```json
{
  "status": "UP",
  "timestamp": "2024-01-01T00:00:00Z",
  "market": {
    "phase": "REGULAR",
    "fetched_at": "2024-01-01T00:00:00Z",
    "open_symbols": ["AAPL"],
    "closed_symbols": ["TSLA"],
    "timezones": { "AAPL": "America/New_York" }
  },
  "cache": {
    "quotes": 10,
    "candles": 5,
    "symbols": ["AAPL", "TSLA"]
  },
  "watchlist": {
    "total": 2,
    "enabled": 1,
    "disabled": 1,
    "enabled_symbols": ["AAPL"]
  },
  "version": {
    "app": "price-fetcher",
    "version": "0.0.1-SNAPSHOT"
  }
}
```

## GET /quotes

**Que hace:** devuelve cotizaciones actuales para una lista de simbolos.

**Query params:**
- `symbols` (string, requerido): lista separada por comas. Max 50 simbolos.

**Respuesta 200:** array de `QuoteDto`.
```json
[
  {
    "symbol": "AAPL",
    "last": 150.25,
    "ts": "2024-01-01T00:00:00Z",
    "open": 149.8,
    "high": 151.0,
    "low": 149.5,
    "prevClose": 149.9,
    "currency": "USD",
    "source": "FINNHUB",
    "stale": false
  }
]
```

**Errores comunes:**
- 400 `{ "error": "symbols parameter is required and cannot be empty" }`
- 400 `{ "error": "No valid symbols provided" }`
- 400 `{ "error": "Too many symbols requested (max 50)" }`
- 400 `{ "error": "Invalid symbol format: ..." }`
- 404 sin body cuando no hay quote
- 500 `{ "error": "Internal server error" }`

## GET /historical

**Que hace:** devuelve velas historicas para un simbolo en un rango de fechas.

**Query params:**
- `symbol` (string, requerido)
- `from` (string ISO-8601, requerido)
- `to` (string ISO-8601, requerido)
- `tf` (string, opcional, default: `T1D`)
- `adjusted` (boolean, opcional, default: `true`)

**Respuesta 200:** `CandleSeries`.
```json
{
  "symbol": { "exchange": "NASDAQ", "ticker": "AAPL" },
  "timeframe": "T1D",
  "items": [
    {
      "ts": "2024-01-01T00:00:00Z",
      "o": 150.0,
      "h": 151.0,
      "l": 149.0,
      "c": 150.5,
      "v": 123456,
      "adjusted": true
    }
  ]
}
```

**Notas:**
- Si `from` > `to`, devuelve 400.
- `adjusted` se refleja en el campo `adjusted` de cada candle.

**Errores comunes:**
- 400 `{ "error": "symbol parameter is required and cannot be empty" }`
- 400 `{ "error": "from and to parameters are required" }`
- 400 `{ "error": "from date must be before to date" }`
- 400 `{ "error": "Invalid date format. Use ISO-8601 format (e.g., 2024-01-01T00:00:00Z)" }`
- 400 `{ "error": "Invalid argument: ..." }`
- 500 `{ "error": "Internal server error" }`

## POST /prefetch

**Que hace:** fuerza la precarga de quotes para los simbolos habilitados en la watchlist.

**Request body:** ninguno.

**Respuesta 200 (cuando hay simbolos habilitados):**
```json
{
  "prefetched": 3,
  "symbols": ["AAPL", "TSLA", "MSFT"]
}
```

**Respuesta 200 (sin simbolos habilitados):**
```json
{
  "prefetched": 0,
  "symbols": [],
  "message": "No enabled symbols in watchlist"
}
```

**Errores comunes:**
- 500 `{ "error": "Prefetch failed", "message": "..." }`

## GET /watchlist

**Que hace:** lista los simbolos en la watchlist.

**Respuesta 200:** array de `WatchListItem`.
```json
[
  { "symbol": "AAPL", "enabled": true, "priority": 1 },
  { "symbol": "TSLA", "enabled": false, "priority": null }
]
```

## POST /watchlist

**Que hace:** agrega un simbolo a la watchlist.

**Request body:** `WatchListItem`.
```json
{
  "symbol": "AAPL",
  "enabled": true,
  "priority": 1
}
```

**Respuesta 200:** sin body.

**Errores comunes:**
- 400 `{ "error": "symbol is required" }`
- 400 `{ "error": "symbol already exists" }`

## PATCH /watchlist/{symbol}

**Que hace:** actualiza campos opcionales de un simbolo en la watchlist.

**Path params:**
- `symbol` (string)

**Request body:** `PatchBody`.
```json
{
  "enabled": false,
  "priority": 5
}
```

**Respuesta 204:** sin body.

**Errores comunes:**
- 400 `{ "error": "symbol not found" }`

## DELETE /watchlist/{symbol}

**Que hace:** elimina un simbolo de la watchlist.

**Path params:**
- `symbol` (string)

**Respuesta 204:** sin body.

**Errores comunes:**
- 400 `{ "error": "symbol not found" }`
