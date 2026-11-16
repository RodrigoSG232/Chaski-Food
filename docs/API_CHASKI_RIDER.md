# Contrato Chaski Food ↔ Chaski Rider — `/integration/v1`

Chaski Food **provee** la API de integración `riderApi`; el **backend** de Chaski Rider la **consume**. La app Android Rider nunca llama a esta API ni accede a Firestore de Food.

**Referencia completa del contrato VIGENTE.**
- **Última actualización:** 16-11-2026.
- **Resumen de cambios y checklist de integración:** [CAMBIOS_CONTRATO_RIDER_E6](CAMBIOS_CONTRATO_RIDER_E6.md).
- **Decisiones de Food:** [DECISIONES § Integración con Chaski Rider](DECISIONES.md#integración-con-chaski-rider-hu21-bhu22-15-11-2026).

## Estado actual
| | Valor |
|---|---|
| Revisión desplegada de riderApi | **`riderapi-00004-moq`** (código `0db8d81`, publicado en `ramaDante`); runtime `nodejs22`; `southamerica-west1` |
| Revisión desplegada de foodApi | `foodapi-00013-hij` |
| Integración (`CHASKI_RIDER_ACTIVATION`) | **DISABLED** (variable sin definir): listado vacío; detalle y eventos → 404 para los pedidos que no iniciaron el flujo Rider |
| Comprobado | Pruebas automatizadas en LOCAL (emulador); en CLOUD, revisiones, IAM, variables y registros sin errores |
| **Pendiente (G6)** | Pruebas HTTP **autenticadas** con la cuenta real de Rider: 200 con listado vacío, 404 en el detalle y comprobación de la identidad |
| **Pendiente (G8)** | Activar `pilot:<storeId>` y las pruebas conjuntas de negocio |

**Marcas:**
- **[ACORDADO]:** diseño aprobado por ambos equipos;
- **[PRUEBA CONJUNTA]:** solo queda validado tras probarlo juntos.

Todo lo que no lleva marca describe el comportamiento **desplegado**.

## 1. Seguridad y autenticación
Dos capas, **ambas obligatorias**:

1. **Cloud Run IAM:**
   - `riderApi` declara en su código `invoker: "chaski-food-client@chaski-rider.iam.gserviceaccount.com"`, y cada despliegue deja **solo** esa cuenta como invocador directo (`roles/run.invoker`);
   - nunca es pública.
2. **Verificación del llamador en la aplicación:** antes de cualquier lógica, riderApi verifica el token:
   - **Cabecera:** `Authorization: Bearer <ID token de Google>`, una sola vez.
   - **Firma:** la de Google, comprobada con sus certificados públicos (`google-auth-library`); el token no puede haber expirado; emisor `accounts.google.com` o `https://accounts.google.com`.
   - **Audiencia (`aud`):** **exactamente** `https://riderapi-u65lcqarqa-tl.a.run.app`. El servicio también responde en otras URLs (una `…run.app` con número de proyecto y otra `cloudfunctions.net`), pero **no se aceptan como audiencia**: invoca y firma con la URL canónica.
   - **Identidad:** `email` = `chaski-food-client@chaski-rider.iam.gserviceaccount.com` y `email_verified: true`. El token debe pedirse en formato **full** / con `includeEmail` (los tokens «standard» no traen `email`).
   - **`X-Serverless-Authorization`:** se **rechaza siempre**. Cloud Run solo valida esa cabecera para IAM cuando está presente y le quita la firma antes de entregarla.
   - **Si algo falla:** `403 {"error":{"code":"CALLER_NOT_ALLOWED","retryable":false}}`.

**Qué capa te rechazó:**

| Respuesta | Capa | Causa habitual |
|---|---|---|
| 403 con cuerpo **HTML** | Cloud Run (IAM) | Sin token o identidad sin `run.invoker` |
| 401 con cuerpo **HTML** | Cloud Run | Token inválido o audiencia que no es una URL del servicio |
| 403 **JSON** `CALLER_NOT_ALLOWED` | riderApi (aplicación) | Otra cuenta, audiencia no canónica, sin `email`/`email_verified` o `X-Serverless-Authorization` presente |

- **Cabeceras de la petición:** `Authorization` y, en POST y PUT, `Content-Type: application/json`.
- **[ACORDADO] Responsabilidades de Rider:**
  - autenticar en su backend al repartidor;
  - mostrar el `pickupCode` **solo** al repartidor de la asignación vigente;
  - revocarlo al liberar, reasignar o cerrar.
- **Datos mínimos:** sin `customer.id`. Rider recibe solo lo necesario para entregar: nombre visible, teléfono, dirección, referencia, indicaciones y coordenadas.

Food no crea ni administra repartidores. Rider es la autoridad sobre ofertas, asignaciones y liberaciones; Food lo es sobre el pedido comercial, sus estados, el código de recojo y el registro del cobro.

## 2. Modelo de integración [ACORDADO]
- **Pull:** el pedido es descubrible al pasar a `READY_FOR_PICKUP`; Food no envía solicitudes de reparto.
- **Identificadores:** `orderId` (64 caracteres hexadecimales) identifica el pedido; `assignment.id` + `assignment.version` identifican la asignación. No se usan `deliveryRequestId` ni `deliveryId`.
- **Comunicación:** REST entre backends. Los proyectos Firebase son independientes.
- **Consulta:** cada **15 s** mientras haya repartidores disponibles (ajustable). En cada pasada se recorren **todas** las páginas desde el inicio y se deduplica por `orderId`. Que un pedido no aparezca, o que falle una consulta, no prueba que se haya asignado o cancelado: los pedidos conocidos se reconcilian con el detalle (§4).
- **Pedido sin repartidor:** sigue `READY_FOR_PICKUP`. No hay cancelación automática.
- **Una sola vía:** los pedidos gestionados por Rider avanzan solo con sus eventos; Food rechaza sus propias simulaciones sobre ellos (`409 RIDER_MANAGED` en foodApi, no en riderApi).

## 3. `GET /integration/v1/orders` — pedidos disponibles
- **Parámetros:** `limit` (1–50, por defecto 20) y `cursor` (opcional). Cualquier otro parámetro, un valor repetido o un cursor inválido → `400 INVALID_REQUEST`.
- **Elegibilidad:**
  - `status = READY_FOR_PICKUP`, con `readyAt`, **sin asignación** y **sin bloqueo de recojo**;
  - local dentro del ámbito de activación (§10);
  - pago de un tipo admitido: efectivo pendiente (`CASH_ON_DELIVERY` + `PENDING`) o **prepago simulado antiguo** (`payment.status:"PAID"`, `amountToCollectMinor: 0`).
  - Los pedidos bloqueados se siguen solo por el detalle.
- **Orden:** `readyAt` ascendente y desempate por id del documento. La elegibilidad se aplica **antes** de completar la página.
- **Paginación:**
  - se examinan como máximo 200 documentos por petición (bloques de 50);
  - `nextCursor` corresponde al **último documento examinado**: puede llegar con una página vacía o incompleta;
  - termina cuando vale `null`;
  - el cursor es **opaco**: no se interpreta ni se modifica.
- **Cada elemento** tiene la estructura del detalle (§4) **sin** `pickupCode`, `pickupVerification`, `pickupLock`, `collectionStatus` ni `collectionReview`.
- **Con la integración DISABLED:** `200 {"contractVersion":1,"orders":[],"nextCursor":null}`.

```json
{ "contractVersion": 1, "orders": [ { "...": "estructura del detalle (§4) sin pickupCode, pickupVerification, pickupLock ni collection*" } ],
  "nextCursor": "opaco-o-null" }
```

## 4. `GET /integration/v1/orders/{orderId}` — detalle y reconciliación
Se usa para:
- reconciliar después de un 409, un timeout o una desconexión;
- recuperar la verificación del código;
- consultar pedidos completados, con su resultado de entrega y de cobro.

| Caso | Respuesta |
|---|---|
| Pedido elegible en `READY_FOR_PICKUP`, `IN_DELIVERY` o `COMPLETED` y dentro del ámbito (o que ya inició el flujo Rider) | `200 {contractVersion:1, order}` |
| `orderId` mal formado o inexistente; o fuera del ámbito sin haber iniciado el flujo Rider (incluido DISABLED) | `404 ORDER_NOT_FOUND` |
| Otro estado u otro tipo de pago | `409 ORDER_NOT_READY` |

**Ejemplo** (pedido verificado y aún sin recoger; datos ficticios; formato `jsonc`):

```jsonc
{
  "contractVersion": 1,
  "order": {
    "schemaVersion": 1,
    "orderId": "<64 hex>",
    "updatedAt": "2026-11-15T15:41:58.004Z",
    "status": "READY_FOR_PICKUP",
    "readyAt": "2026-11-15T15:30:02.000Z",                     // ISO o null
    "customer": { "displayName": "Cliente", "phone": "+51900000000", "address": "Dirección de entrega",
                  "reference": null, "instructions": null, "latitude": -12.05, "longitude": -77.04 },
    "merchant": { "id": "store-id", "name": "Local", "phone": null, "address": "Dirección del local",
                  "instructions": null, "latitude": -12.06, "longitude": -77.03 },
    "items": [ { "id": "product-id", "name": "Producto", "quantity": 2 } ],
    "amounts": { "subtotalMinor": 2600, "deliveryFeeMinor": 450, "tipMinor": 100, "totalMinor": 3150, "currency": "PEN" },
    "payment": { "method": "CASH_ON_DELIVERY", "status": "PENDING", "amountToCollectMinor": 3150, "currency": "PEN" },
    "pickupCode": null,
    "pickupVerification": { "assignmentId": "ASG_1001_2", "assignmentVersion": 2, "verifiedAt": "2026-11-15T15:41:58.004Z" },
    "pickupLock": null,
    "collectionStatus": null,                                    // RECORDED | DISCREPANCY | null
    "collectionReview": null,                                    // §5 / §5b
    "dispatch": {
      "status": "AT_MERCHANT", "lastEventSequence": 4, "lastAssignmentVersion": 2,
      "assignment": { "id": "ASG_1001_2", "version": 2 },
      "rider": { "id": "RIDER_88", "displayName": "Repartidor", "phone": null, "vehicleType": "MOTORCYCLE", "vehiclePlate": "ABC-123" },
      "deliveryOutcome": null, "reason": null, "updatedAt": "2026-11-15T15:41:58.004Z"
    },
    "tracking": null   // o {assignmentId, assignmentVersion, sequence, latitude, longitude, accuracyMeters, recordedAt, estimatedArrivalAt}
  }
}
```

- **`payment.amountToCollectMinor`** = `totalMinor` (subtotal + reparto + propina) si el pago está `PENDING`, y `0` si ya está `PAID` (prepago simulado antiguo o cobro registrado). En un pedido `COMPLETED` el resultado del cobro se lee en `collectionStatus` y `collectionReview`, no en `amountToCollectMinor`.
- **Fechas:** todas en ISO 8601 UTC con milisegundos. `dispatch` y `tracking` no incluyen campos internos (`payloadHash`, `receivedAt` ni el `riderId` del tracking).

**Campos del recojo:**

| Situación | `pickupCode` | `pickupVerification` | `pickupLock` |
|---|---|---|---|
| Sin asignación vigente | `null` | `null` | `null` o el bloqueo vigente |
| Asignación vigente, sin verificar, sin bloqueo, en `READY_FOR_PICKUP` | Código de 4 dígitos (**solo** para el repartidor vigente) | `null` | `null` |
| Verificado para la asignación vigente | `null` | `{assignmentId, assignmentVersion, verifiedAt}` | `null` |
| Bloqueado por intentos | `null` | `null` | `{ "lockedAt": "…", "reason": "MAX_ATTEMPTS" }` |
| Tras una liberación | `null` | `null` (la anterior queda invalidada) | Se conserva si existía |

- Una verificación está **vigente** solo si su `assignmentId/assignmentVersion` coincide con `dispatch.assignment`.
- **Validar el código no equivale al recojo físico:** el recojo lo declara Rider con `PICKED_UP`.

## 5. Pago contra entrega y cobro
- Solo `CASH_ON_DELIVERY`. Importes en **céntimos de PEN** (enteros). **No hay pagos parciales.**
- **Medios que declara Rider:** `CASH`, `YAPE`, `PLIN` (cómo pagó el comprador al repartidor).
- **`collection` según el evento:**

| Evento | `collection` |
|---|---|
| `COMPLETED_SUCCESSFULLY` en efectivo pendiente | **Obligatorio** `{method, amountMinor (1–10 000 000), currency:"PEN"}` |
| `COMPLETED_SUCCESSFULLY` de un **prepago simulado antiguo** (`payment.status:"PAID"`) | `null` (clave presente); con dinero → 422 |
| `COMPLETED_UNSUCCESSFULLY` | `null` (clave presente); con dinero → **422** |
| Cualquier otro evento | Clave **ausente** |

- **Importe exacto** (igual al `totalMinor` de Food): en **una sola transacción** quedan registrados el pago `PAID` y el pedido `COMPLETED`; `collectionStatus:"RECORDED"`, `collectionReview:null`.
- **Importe distinto:**
  - el pedido queda `COMPLETED` con `deliveryOutcome=SUCCESS` y el pago **sigue `PENDING`**;
  - `collectionStatus:"DISCREPANCY"` y `collectionReview {status:"OPEN", expectedMinor, declaredMinor, method, currency}`;
  - Food **nunca** registra `PAID` con un importe incorrecto.
- **Respuesta perdida:** no volver a cobrar; reenviar **el mismo** evento; Food devuelve el resultado original.
- **Entrega fallida con dinero recibido:** el evento se envía con `collection:null` y el incidente se reporta fuera de banda (procedimiento manual de Food). Todavía no existe un registro integrado.
- La liquidación de lo recaudado por el repartidor pertenece a Rider.

```http
POST /integration/v1/rider-events
{ "schemaVersion": 1, "eventId": "EV_RIDER_1001_7", "eventType": "COMPLETED_SUCCESSFULLY", "eventSequence": 7,
  "occurredAt": "2026-11-15T16:10:00.000Z", "orderId": "<64 hex>", "assignment": { "id": "ASG_1001_2", "version": 2 },
  "status": "COMPLETED", "reason": null, "rider": null, "deliveryOutcome": "SUCCESS",
  "collection": { "method": "YAPE", "amountMinor": 3000, "currency": "PEN" } }

200
{ "contractVersion": 1, "eventId": "EV_RIDER_1001_7", "orderId": "<64 hex>", "applied": true, "lastEventSequence": 7,
  "collectionStatus": "DISCREPANCY",
  "collectionReview": { "status": "OPEN", "expectedMinor": 3150, "declaredMinor": 3000, "method": "YAPE", "currency": "PEN" } }
```

## 5b. Conciliación administrativa de un cobro discrepante
La hace un **administrador de Food** desde foodApi; Rider no la invoca. A Rider le llega como cambio **aditivo** en el detalle.
- **La declaración original de Rider nunca se modifica.** `collectionStatus` conserva su valor histórico (`DISCREPANCY`).
- **`collectionReview` una vez resuelta:**

```json
{ "status": "RESOLVED", "expectedMinor": 1300, "declaredMinor": 1600, "method": "CASH", "currency": "PEN",
  "outcome": "FULLY_COLLECTED", "confirmedMinor": 1600, "shortfallMinor": 0, "excessMinor": 300, "excessStatus": "PENDING_REFUND" }
```

  - `FULLY_COLLECTED`: se confirmó al menos el total; el pago queda `PAID`.
  - `NOT_FULLY_COLLECTED`: se confirmó menos; el pago sigue `PENDING` y se registra `shortfallMinor`.
  - `excessStatus`: `NONE`, `PENDING_REFUND` o `REFUNDED`. La devolución se hace **fuera** de la app y Food solo la registra.
  - No se exponen el actor ni la evidencia.
- **[ACORDADO, pendiente de confirmar por Rider]:**
  - tolerar `RESOLVED` y los campos nuevos;
  - usarlos en su liquidación;
  - acordar el procedimiento de devolución de excedentes.

## 6. `POST /integration/v1/rider-events` — eventos de reparto
Todas las claves son **obligatorias y exactas**: una clave de más, aunque valga `null`, → 422.
- `rider` va solo en `ASSIGNMENT_ACCEPTED` (`vehicleType`: `MOTORCYCLE` o `BICYCLE`).
- `collection` va solo en `COMPLETED_*` (§5).
- `occurredAt` en ISO 8601 UTC con milisegundos.

```json
{ "schemaVersion": 1, "eventId": "EV_RIDER_1001_1", "eventType": "ASSIGNMENT_ACCEPTED", "eventSequence": 1,
  "occurredAt": "2026-11-15T15:32:00.000Z", "orderId": "<64 hex>", "assignment": { "id": "ASG_1001_1", "version": 1 },
  "status": "ACCEPTED", "reason": null,
  "rider": { "id": "RIDER_88", "displayName": "Repartidor", "phone": null, "vehicleType": "MOTORCYCLE", "vehiclePlate": "ABC-123" },
  "deliveryOutcome": null }
```

**Respuesta:** `200 {contractVersion:1, eventId, orderId, applied:true, lastEventSequence}`. En los `COMPLETED_*` incluye además `collectionStatus` y `collectionReview`.

| `eventType` | `status` | Efecto |
|---|---|---|
| `ASSIGNMENT_ACCEPTED` | `ACCEPTED` | Requiere `READY_FOR_PICKUP`, sin asignación vigente, `assignment.version = lastAssignmentVersion + 1`, sin bloqueo y local dentro del ámbito. Si no: `409 ASSIGNMENT_CONFLICT`, `409 PICKUP_LOCKED` o `409 ORDER_NOT_READY` (fuera del ámbito) |
| `ARRIVED_AT_MERCHANT` | `AT_MERCHANT` | Requiere `READY_FOR_PICKUP` y la asignación vigente |
| `ASSIGNMENT_RELEASED` | `SEARCHING_RIDER` | Antes del recojo. Libera la asignación e **invalida la verificación**; no reinicia los intentos ni quita el bloqueo |
| `PICKED_UP` | `ON_THE_WAY` | Pasa a `IN_DELIVERY` **solo** con una verificación vigente de la misma asignación; si no, `409 PICKUP_NOT_VERIFIED` (no consume la secuencia) |
| `ARRIVED_AT_CUSTOMER` | `AT_CUSTOMER` | Requiere `IN_DELIVERY` |
| `COMPLETED_SUCCESSFULLY` | `COMPLETED` | Requiere `IN_DELIVERY`; `deliveryOutcome:"SUCCESS"`; registra el cobro o la discrepancia (§5) |
| `COMPLETED_UNSUCCESSFULLY` | `COMPLETED` | Requiere `IN_DELIVERY`; `deliveryOutcome:"UNSUCCESSFUL"`; `collection:null` |

- **Avisos al comprador:** la asignación, el recojo y el resultado de la entrega generan avisos en Food.
- **Fuera del ámbito:** los pedidos que ya iniciaron el flujo Rider siguen recibiendo sus eventos; una asignación **nueva** fuera del ámbito → `409 ORDER_NOT_READY`.

### Secuencia, idempotencia y recuperación
- **Secuencia:**
  - `eventSequence` es **una por pedido** (no por repartidor) y continúa tras liberaciones y reasignaciones;
  - debe ser exactamente `lastEventSequence + 1`; si no, `409 EVENT_SEQUENCE_CONFLICT`;
  - un evento rechazado **no consume** su secuencia.
- **Idempotencia:** el mismo `eventId` con el mismo cuerpo devuelve el resultado original (también después de cambios posteriores); con otro cuerpo, `409 IDEMPOTENCY_CONFLICT` (el hash cubre el cuerpo completo, `collection` incluida).
- **Asignación no vigente:** `409 ASSIGNMENT_STALE`. **Estado incompatible:** `409 ORDER_STATE_CONFLICT`.
- **[ACORDADO] Responsabilidades de Rider:**
  - el backend coordina los eventos y asigna la secuencia por pedido;
  - mantiene una **cola de salida duradera** (persistir antes de enviar; en orden por `orderId`);
  - ante timeout, 503 o resultado desconocido, reenvía el **mismo** `eventId`, cuerpo y secuencia con espera creciente;
  - ante 409, consulta el detalle y concilia;
  - ante 422, corrige el contenido (un evento no aplicado puede reemitirse con un `eventId` nuevo y la misma secuencia);
  - descarta sin dejar huecos los eventos pendientes de un repartidor liberado;
  - impide que dos repartidores ganen la misma asignación.
- **[PRUEBA CONJUNTA]:** desconexión real, pérdida del ACK, reintento del evento final, duplicados y doble aceptación.

## 7. `PUT /integration/v1/orders/{orderId}/tracking`
- Solo con el pedido `IN_DELIVERY`, la asignación vigente y el mismo `riderId`; si no, `409 ASSIGNMENT_STALE`.
- **Respuesta:** `200 {contractVersion:1, orderId, assignmentVersion, acceptedSequence, received}`.
- **Secuencias:**
  - una secuencia menor no se aplica (`received:false`);
  - la misma secuencia con otro cuerpo → `409 IDEMPOTENCY_CONFLICT`;
  - una muestra con un `recordedAt` más antiguo no reemplaza a la vigente.
- **Validaciones:** un `recordedAt` más de 60 s en el futuro → 422. `estimatedArrivalAt` es opcional (ISO, entre `recordedAt` y 24 h después). `accuracyMeters` entre 0 y 500. Coordenadas válidas.

```json
{ "assignmentId": "ASG_1001_2", "assignmentVersion": 2, "sequence": 19, "riderId": "RIDER_88",
  "latitude": -12.123, "longitude": -77.031, "accuracyMeters": 15, "recordedAt": "2026-11-15T16:00:00.000Z",
  "estimatedArrivalAt": "2026-11-15T16:12:00.000Z" }
```

- El tracking empieza después del recojo y termina al completar.
- El comprador lo ve por foodApi, sin PIN, teléfono, placa ni ID del repartidor ([HU23_SEGUIMIENTO](HU23_SEGUIMIENTO.md)).

## 8. Ciclo del código de recojo (foodApi; no lo usa Rider)
Se documenta para que Rider entienda el ciclo.
- **Generación:** `POST /api/v1/stores/{storeId}/orders/{orderId}/ready-for-pickup`, lo usa el responsable VERIFIED del local. `PREPARING → READY_FOR_PICKUP` y genera un código de 4 dígitos.
  - **El PIN no se envía al responsable** (ni en la bandeja ni en la respuesta); solo lo recibe el backend Rider en el detalle, con una asignación vigente.
- **Verificación:**
  - el responsable introduce el código que **muestra el repartidor**: `POST /api/v1/stores/{storeId}/orders/{orderId}/pickup-verifications` con `{operationId, code, assignmentId, assignmentVersion}`;
  - guarda `pickupVerification` ligada a la asignación;
  - **no** produce `PICKED_UP`;
  - el local ve la identidad del repartidor vigente (nombre y placa).
- **Intentos y bloqueo:**
  - 5 fallos por pedido y ciclo → `pickupLock`;
  - una reasignación no reinicia el contador;
  - Rider muestra «Recojo pendiente de revisión».
- **Desbloqueo:**
  - solo un **administrador de Food** (`POST /api/v1/admin/pickup-incidents/{orderId}/unlock`), con motivo;
  - genera un **código nuevo**, invalida el anterior e inicia un ciclo nuevo de 5 intentos;
  - Rider obtiene el código nuevo por el detalle si la asignación sigue vigente.
- **Auditoría:** los intentos y los desbloqueos son idempotentes y auditados; **nunca se guardan los códigos introducidos**.

## 9. Errores
**Cuerpo de error de Food:** `{"error":{"code":"…","retryable":<true solo en 5xx>}}`. Las respuestas de Cloud Run (HTML) no tienen ese formato (§1).

| HTTP | Código | Cuándo |
|---|---|---|
| 400 | `INVALID_REQUEST` | Parámetro no admitido o repetido, `limit` inválido, cursor inválido, parámetros de consulta en el detalle, los eventos o el tracking |
| 401 / 403 (HTML) | — (Cloud Run) | Token ausente o inválido, audiencia que no es del servicio, identidad sin `run.invoker` |
| 403 | `CALLER_NOT_ALLOWED` | El token no es de la cuenta Rider, la audiencia no es la canónica, falta `email`/`email_verified` o viene `X-Serverless-Authorization` |
| 404 | `NOT_FOUND` | Ruta inexistente |
| 404 | `ORDER_NOT_FOUND` | Pedido inexistente, mal formado o fuera del ámbito (incluido DISABLED) |
| 405 | `METHOD_NOT_ALLOWED` | Método distinto del documentado |
| 409 | `ORDER_NOT_READY`, `EVENT_SEQUENCE_CONFLICT`, `ASSIGNMENT_CONFLICT`, `ASSIGNMENT_STALE`, `ORDER_STATE_CONFLICT`, `IDEMPOTENCY_CONFLICT`, `PICKUP_NOT_VERIFIED`, `PICKUP_LOCKED` | Ver §3–§7 |
| 422 | `INVALID_REQUEST` | Cuerpo inválido, clave desconocida o ausente, `collection` incorrecta, fechas o coordenadas inválidas |
| 503 | `RIDER_API_UNAVAILABLE` | Fallo interno o configuración de activación inválida (falla en modo cerrado). Reintentable |
| 503 | `DATA_INCONSISTENT` | Datos del pedido incompatibles. Reintentable; si persiste, avisar a Food |

## 10. Activación, piloto y despliegue coordinado
- **`CHASKI_RIDER_ACTIVATION`** (configuración de Food):
  - sin definir → **DISABLED** (estado actual);
  - `pilot:<storeId>,<storeId>` → solo esos locales;
  - `general` → todos los locales;
  - cualquier otro valor → 503.
- **Fuera del ámbito:**
  - el pedido no se ofrece;
  - el detalle, los eventos y el tracking responden 404 **salvo** que el pedido ya haya iniciado el flujo Rider (las entregas en curso pueden terminarse y conciliarse aunque se reduzca el ámbito);
  - una asignación nueva → `409 ORDER_NOT_READY`.
- **Reducir el piloto ≠ apagado de emergencia.** No existe todavía un mecanismo independiente que corte las entregas en curso.
- **Versión:** se mantiene `/integration/v1`. Rider confirmó que retirar `customer.id` y `pickupCode` del listado es compatible con su adaptador.
- **Orden de activación:**
  1. ✔ Food desplegó el contrato nuevo con DISABLED;
  2. Rider despliega su versión compatible y prueba la autenticación con DISABLED (**G6**);
  3. Food activa `pilot:<storeId>` (**G8**) y se hacen las pruebas conjuntas;
  4. `general` tras validar el piloto.

## 11. Historial de versiones publicadas
| Fecha | Revisión | Contenido |
|---|---|---|
| 08-11-2026 | `riderapi-00001-xeg` | Versión inicial: solo prepago simulado (`PAID` + simulación), listado sin orden ni cursor y **con** `pickupCode` y `customer.id`, `payment {flow:"PREPAID_SIMULATION",…}`, `PICKED_UP` sin verificación, sin `collection` (422), `invoker:"private"` |
| 16-11-2026 | `riderapi-00002-win` | Contrato nuevo (E1–E5): efectivo, paginación por `readyAt`, PIN protegido, verificación y bloqueo, `collection`, conciliación, piloto. **El despliegue vació el IAM directo** |
| 16-11-2026 | `riderapi-00003-tew` | G7-A: invocador directo declarado en el código (solo la cuenta Rider) |
| 16-11-2026 | **`riderapi-00004-moq`** | G7-B: verificación criptográfica del llamador (§1). **Vigente** |

Los procedimientos internos de despliegue y de IAM de Food no forman parte de este contrato.

## 12. Pruebas conjuntas
- **G6 (con DISABLED):** checklist en [CAMBIOS_CONTRATO_RIDER_E6 §5](CAMBIOS_CONTRATO_RIDER_E6.md).
- **G8 (con piloto) [PRUEBA CONJUNTA]:**
  1. contra entrega completa con desconexión real y repetición del evento final;
  2. doble aceptación → `409 ASSIGNMENT_CONFLICT`;
  3. liberación antes del recojo: la asignación anterior ya no puede verificar, recoger ni enviar tracking;
  4. bloqueo y desbloqueo;
  5. discrepancia y conciliación.

  Detalle en [PRUEBAS_E6](PRUEBAS_E6.md).
- **Paginación con más de 50 pedidos:** demostrada en LOCAL (emulador).
