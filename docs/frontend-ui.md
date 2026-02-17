# Frontend: endpoints y uso en UI

Este documento resume los endpoints disponibles y una propuesta de como se usan desde la UI. Esta pensado para iniciar el frontend sin necesidad de leer todo el backend.

Notas generales
- Base URL: `http://localhost:8080` (ajustar por entorno).
- Auth opcional: con perfil `auth` se requiere `Authorization: Bearer <token>` y scopes.
- Respuestas de error tipicas: `{"error": "mensaje"}` con status 400/500; algunos endpoints pueden responder 404.

## Endpoints y uso sugerido en UI

### Health
`GET /health`
- Uso UI: panel de estado (admin/ops) y diagnostico rapido.
- Datos clave para UI: `market.phase`, `market.open_symbols`, `market.closed_symbols`, `cache.quotes`, `watchlist.enabled_symbols`.
- Observacion: es publico incluso con perfil `auth`.

`GET /actuator/health`
- Uso UI: ping tecnico (no necesita mostrarse en UI).

### Quotes (cotizaciones)
`GET /quotes?symbols=AAPL,TSLA`
- Scope: `read:prices` (solo en perfil `auth`).
- Parametros:
  - `symbols`: lista separada por coma, max 50. Se recomienda normalizar a uppercase en UI.
- Respuesta: lista de `QuoteDto` con campos como `symbol`, `last`, `ts`, `open`, `high`, `low`, `prevClose`, `currency`, `source`, `stale`.
- Uso UI:
  - Vista principal de cotizaciones y cards por simbolo.
  - Indicador `stale` para mostrar dato atrasado (badge o color).
  - Mostrar `ts` como "ultima actualizacion".

### Historical (velas)
`GET /historical?symbol=AAPL&from=2024-01-01T00:00:00Z&to=2024-02-01T00:00:00Z&tf=T1D&adjusted=true`
- Scope: `read:market-data` (solo en perfil `auth`).
- Parametros:
  - `symbol`: simbolo unico.
  - `from`, `to`: ISO-8601.
  - `tf`: `T1D`, `T1H`, `T15M`, `T5M`, `T1M`.
  - `adjusted`: bool, default `true`.
- Respuesta: `CandleSeries` con `items` de velas (`ts`, `o`, `h`, `l`, `c`, `v`, `adjusted`).
- Uso UI:
  - Grafico de velas y volumen en el detalle del simbolo.
  - Selector de rango y timeframe.

### Prefetch
`POST /prefetch`
- Scope: `write:prices` (solo en perfil `auth`).
- Respuesta: `{ "prefetched": number, "symbols": [..] }` o `{ "prefetched": 0, "symbols": [], "message": "No enabled symbols in watchlist" }`.
- Uso UI:
  - Boton de "Refrescar todo" en pantalla de watchlist o admin.
  - Mostrar resultado con toast o banner.

### Watchlist
`GET /watchlist`
- Scope: `read:prices` (solo en perfil `auth`).
- Respuesta: lista de `WatchListItem` ordenada por prioridad.
- Uso UI:
  - Pantalla de watchlist con ordenamiento y toggles.
  - Es la fuente de simbolos habilitados para otras vistas.

`POST /watchlist`
- Scope: `write:prices` (solo en perfil `auth`).
- Body:
  ```json
  { "symbol": "AAPL", "enabled": true, "priority": 1 }
  ```
- Uso UI:
  - Form para agregar simbolos.
  - Validar duplicados y formato antes de enviar.

`PATCH /watchlist/{symbol}`
- Scope: `write:prices` (solo en perfil `auth`).
- Body:
  ```json
  { "enabled": false, "priority": 5 }
  ```
- Uso UI:
  - Toggle de habilitado.
  - Drag and drop o input para prioridad.

`DELETE /watchlist/{symbol}`
- Scope: `write:prices` (solo en perfil `auth`).
- Uso UI:
  - Accion de eliminar simbolo de la lista.

## Flujos de UI recomendados
1) **Dashboard de cotizaciones**
   - Input de simbolos (o usa watchlist habilitada).
   - Llama `GET /quotes` y refresca con polling (cada 5-30s).
   - Muestra `stale` y timestamp.
2) **Detalle de simbolo**
   - Usa `GET /historical` para graficos.
   - Muestra datos actuales con `GET /quotes` (un solo simbolo).
3) **Watchlist**
   - CRUD con endpoints `/watchlist`.
   - Orden por `priority`, toggle `enabled`.
4) **Admin / Health**
   - Vista simple con `GET /health`.
   - Mostrar fase de mercado y estado de cache.

## Recomendaciones UI
- Respetar limite de 50 simbolos por request en `GET /quotes`. Si se supera, dividir en batches.
- Normalizar simbolos a uppercase y sin espacios antes de enviar.
- Mostrar un estado "stale" cuando `stale=true` en `QuoteDto`.
- Para historicos, validar que `from <= to` y usar ISO-8601.
- Manejar errores 400/404/500 con mensajes claros y acciones sugeridas.
- Si el perfil `auth` esta activo, centralizar el manejo del token y scopes (401/403).
- En watchlist, evitar enviar `priority` nula si no se usa, o mantenerla visible como opcional.
