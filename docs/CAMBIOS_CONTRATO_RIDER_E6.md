# Cambios del contrato `/integration/v1` para Chaski Rider (E6)

> **Resumen para el equipo Rider (Ángel):** cambios recientes, requisitos clave y checklist de integración.
>
> - **Contrato completo vigente:** [API_CHASKI_RIDER.md](API_CHASKI_RIDER.md). Ante cualquier duda, manda ese documento.
> - **Versión anterior:** `riderapi-00001-xeg` (08-11-2026), la que conoce el adaptador actual de Rider.
> - **Versión vigente en Cloud:** **`riderapi-00004-moq`** (16-11-2026; código `0db8d81`), con `foodapi-00013-hij`.
> - **Integración DISABLED:** el listado viene vacío y el detalle responde 404. No hay pedidos ofrecidos hasta el piloto (G8).
>
> Este documento no afirma que Rider haya implementado nada: cada fila indica qué hay que **implementar** y qué hay que **confirmar**. Las pruebas autenticadas con la cuenta real de Rider están **pendientes (G6)**.

## 1. Autenticación (CAMBIA: nueva verificación de identidad)
**Dos capas obligatorias:**

1. **Cloud Run IAM:** el único invocador directo de riderApi es `chaski-food-client@chaski-rider.iam.gserviceaccount.com` (declarado en el código de Food; se conserva en cada despliegue).
2. **Verificación en riderApi (nuevo):** cada petición debe llevar:
   - `Authorization: Bearer <ID token de Google>` **completo y firmado**, emitido para esa cuenta (sin manipular; vigente);
   - la **audiencia exacta `https://riderapi-u65lcqarqa-tl.a.run.app`**, la URL canónica. **Invoca esa misma URL**: las otras URLs del servicio (`…run.app` con número de proyecto, `cloudfunctions.net`) **no se aceptan como audiencia**;
   - los claims `email` = la cuenta anterior y `email_verified: true`. Pide el token en formato **full** / con `includeEmail` (desde el metadata server o la librería de Google en su backend; **nunca** claves JSON compartidas).
   - **No envíes `X-Serverless-Authorization`:** se rechaza siempre.
   - Si algo falla: `403 {"error":{"code":"CALLER_NOT_ALLOWED","retryable":false}}`.

**Cómo saber qué capa rechazó:**

| Respuesta | Capa | Revisa |
|---|---|---|
| 403 con cuerpo **HTML** | Cloud Run (IAM) | Falta el token o la cuenta no es la autorizada |
| 401 con cuerpo **HTML** | Cloud Run | Token inválido o audiencia que no es del servicio |
| 403 **JSON** `CALLER_NOT_ALLOWED` | riderApi | Cuenta, audiencia canónica, `email`/`email_verified` o cabecera `X-Serverless-Authorization` |

**Impacto inmediato:** el adaptador actual de Rider recibirá `CALLER_NOT_ALLOWED` si su token no cumple estos requisitos. No tiene efecto operativo mientras la integración siga DISABLED.

## 2. Diferencias con la versión anterior
| Tema | Antes (`00001-xeg`) | Ahora (`00004-moq`) | Rider debe |
|---|---|---|---|
| **Autenticación** | IAM; la aplicación no distinguía identidades | IAM más verificación criptográfica del llamador (§1) | Token full con la audiencia canónica |
| **Descubrimiento** | Sin orden ni cursor; incluía `pickupCode` y `customer.id` | `GET /orders?limit=1..50&cursor=`, ordenado por `readyAt` e id; `nextCursor` opaco (puede venir con una página vacía; `null` al terminar); **sin** `pickupCode` ni `customer.id`; excluye los pedidos asignados o bloqueados | Paginar hasta `null`; no interpretar el cursor; tolerar páginas vacías |
| **Pago** | `PREPAID_SIMULATION/SIMULATED/PAID` | `payment {method:"CASH_ON_DELIVERY", status, amountToCollectMinor, currency:"PEN"}` en céntimos. Efectivo pendiente: `amountToCollectMinor = totalMinor`. **Prepago simulado antiguo:** `status:"PAID"`, `amountToCollectMinor:0` | Cobrar exactamente `amountToCollectMinor`; si vale 0, no cobrar |
| **Detalle** | El PIN siempre | `pickupCode` solo en `READY_FOR_PICKUP` con **asignación vigente**, sin verificar y sin bloqueo; si no, `null`. Además `pickupVerification`, `pickupLock`, `collectionStatus` y `collectionReview`; fechas ISO | Mostrar el PIN **solo al repartidor asignado** |
| **Asignación y liberación** | Versión | Además `409 ASSIGNMENT_CONFLICT`, `ASSIGNMENT_STALE` y `PICKUP_LOCKED`. `ASSIGNMENT_RELEASED` invalida la verificación (los intentos y el bloqueo se conservan) | `assignment.version` +1 al reasignar |
| **Verificación del PIN** | No existía | El **local** verifica el código en Food. `PICKED_UP` sin verificación vigente → `409 PICKUP_NOT_VERIFIED` (no consume la secuencia) | Esperar la verificación y reintentar `PICKED_UP` |
| **Bloqueo** | No existía | 5 fallos → `pickupLock`; fuera del listado. Un administrador de Food desbloquea y **regenera** el PIN | Volver a consultar el detalle tras el desbloqueo |
| **Eventos** | Sin validaciones nuevas | `eventSequence` por pedido (+1 estricto); `eventId` idempotente con hash del cuerpo completo; mismo `eventId` con otro cuerpo → `409 IDEMPOTENCY_CONFLICT` | Cola duradera; reenviar **el mismo** evento ante un resultado desconocido |
| **Cobro (`collection`)** | 422 | En `COMPLETED_*` la clave es obligatoria: éxito en efectivo pendiente → `{method:CASH\|YAPE\|PLIN, amountMinor 1..10 000 000, currency:"PEN"}`; éxito de **prepago antiguo** y entrega fallida → `null`; en otros eventos, ausente | Enviarla siempre en el evento final |
| **Discrepancia** | — | Importe ≠ total de Food → `COMPLETED`, pago `PENDING`, `collectionStatus:"DISCREPANCY"`, `collectionReview {status:"OPEN",…}`. Exacto → `RECORDED` y `PAID` | Mostrar «cobro en revisión»; no recobrar |
| **Conciliación (§5b)** | — | Un administrador de Food resuelve: `collectionReview.status:"RESOLVED"` más `outcome`, `confirmedMinor`, `shortfallMinor`, `excessMinor`, `excessStatus`. `collectionStatus` sigue en `DISCREPANCY` | Tolerar los campos; usarlos en su liquidación |
| **Excedentes** | — | La devolución se hace **fuera** de la app; Food la registra (`REFUNDED`) | Acordar quién devuelve y cómo se informa |
| **Entrega fallida con dinero** | — | `COMPLETED_UNSUCCESSFULLY` con `collection` distinto de `null` → **422**. Procedimiento manual | Reportar fuera de banda; enviar `collection:null` |
| **Errores** | Parciales | `{error:{code,retryable}}`: 400 parámetros o cursor; **403 `CALLER_NOT_ALLOWED`**; 404 `ORDER_NOT_FOUND`; 409 `ORDER_NOT_READY`, `ORDER_STATE_CONFLICT`, `EVENT_SEQUENCE_CONFLICT`, `ASSIGNMENT_*`, `PICKUP_*`, `IDEMPOTENCY_CONFLICT`; 422 cuerpo; **503 `RIDER_API_UNAVAILABLE`** (también con una configuración de activación inválida) y **503 `DATA_INCONSISTENT`**, ambos retryable. Las respuestas de Cloud Run en HTML no tienen ese formato | Reintentar solo si `retryable` |
| **Piloto** | — | `CHASKI_RIDER_ACTIVATION`: DISABLED (actual) / `pilot:<storeIds>` / `general`. Fuera del ámbito: sin ofertas ni asignaciones nuevas (`409 ORDER_NOT_READY`); las entregas ya iniciadas continúan | Tolerar el 404 y el listado vacío |

## 3. Pendiente de confirmar por Rider
1. La cuenta de servicio, el token full y la audiencia canónica (§1).
2. Que paginan, toleran páginas vacías y no muestran el PIN fuera de la asignación.
3. `PICKUP_NOT_VERIFIED` y `PICKUP_LOCKED`, y el flujo de espera.
4. `collection` en todos los `COMPLETED_*`, incluido el caso del prepago antiguo (`null`).
5. Que toleran la §5b y el procedimiento de devolución.
6. Que la cola duradera resiste horas de 503 o 403.
7. El canal para incidentes de entrega fallida con dinero.
8. La fecha de su despliegue compatible.

## 4. Estado y dependencias
- **Food [CLOUD, 16-11]:**
  - contrato nuevo **desplegado** (`riderapi-00004-moq`, `foodapi-00013-hij`), índice de descubrimiento READY, IAM directo solo para la cuenta Rider, verificación del llamador activa e integración **DISABLED**;
  - los registros no muestran errores.
- **Comprobado:**
  - las pruebas automatizadas de Food en LOCAL (incluida la verificación criptográfica de tokens con firmas reales de prueba);
  - en Cloud, las revisiones, el IAM y la configuración.
- **No probado todavía:** las llamadas autenticadas con la cuenta real de Rider (G6).
- **Siguiente orden:**
  1. Rider adapta y despliega su versión compatible;
  2. checklist de G6 (§5) con DISABLED;
  3. Food activa `pilot:<storeId>` (G8) y se hacen las pruebas conjuntas;
  4. `general` tras el piloto.
- **Riesgo por versiones:** el adaptador antiguo de Rider contra el Food actual obtiene `CALLER_NOT_ALLOWED` o 422 (`collection`) y 409 (`PICKED_UP` sin verificación). Por eso el piloto no se activa hasta confirmar la versión compatible.
- **Pruebas conjuntas:** casos 1–14 de [PRUEBAS_E6](PRUEBAS_E6.md); Rider aporta un repartidor de prueba y su app.
- **Aceptación de la integración:** la §3 confirmada por escrito más los casos 1–14 en OK.

## 5. Checklist de G6 (no ejecutado)
**Con la integración DISABLED (sin datos de negocio):**
1. Ángel confirma por escrito el contrato (§2, §3).
2. Confirma que su backend usa `chaski-food-client@chaski-rider.iam.gserviceaccount.com`.
3. Obtiene el ID token **en su backend** (formato **full**, audiencia `https://riderapi-u65lcqarqa-tl.a.run.app`). Nunca claves JSON compartidas.
4. Envía solo `Authorization: Bearer …` (sin `X-Serverless-Authorization`) a la URL canónica.
5. `GET /integration/v1/orders?limit=5` → **200** `{"contractVersion":1,"orders":[],"nextCursor":null}`.
6. `GET /integration/v1/orders/<64 hex inexistente>` → **404** `ORDER_NOT_FOUND`.
7. Un 200 en el punto 5 demuestra que la identidad se verificó con un token firmado. Si sale 403 JSON `CALLER_NOT_ALLOWED` o 401/403 HTML, aplicar la tabla de §1.
8. **Evidencia:** solo el código HTTP, el cuerpo sin datos personales y la hora. **Nunca el token.** Food contrasta con sus registros.

**Requieren `pilot:<storeId>` (G8, autorización aparte):** ofertas reales, asignación, PIN, recojo, tracking, entrega, cobro, conciliación, liberación y bloqueo (PRUEBAS_E6, casos 1–14).

**Criterios para autorizar G8:**
- los puntos 1–8 en OK;
- el local elegido sin pedidos que interfieran (consulta de nuevo antes de activar);
- el APK `1.1.0-e6` en los dispositivos participantes;
- Rider desplegado en su versión compatible.
