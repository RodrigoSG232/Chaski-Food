# Contrato Chaski Food ↔ Chaski Rider — `/integration/v1`

Chaski Food **provee** la API de integración `riderApi`; el **backend** de Chaski Rider la **consume**. La app Android Rider nunca llama a esta API ni accede a Firestore de Food.

**Última actualización: 15-11-2026 (D0).** Recoge los acuerdos técnicos de la primera fase de **contra entrega** (HU21-B y HU22), conformados por ambos equipos. Las decisiones de Food están en [DECISIONES § Integración con Chaski Rider](DECISIONES.md#integración-con-chaski-rider-hu21-bhu22-15-11-2026) y el plan por etapas, en `PLAN_HU15_HU22_RIDER.md` (local).

## Cómo leer este documento

| Marca | Significado |
|---|---|
| **[CLOUD]** | Comportamiento desplegado hoy en `riderapi-00001-xeg` (código del commit `ee32dbe`, publicado el 08-11-2026). |
| **[REPO]** | Comportamiento del repositorio actual (`ramaDante`, `1be7a39`), todavía no desplegado. |
| **[ACORDADO]** | Diseño contractual aprobado por Food y Rider. **No está implementado** salvo que se indique [REPO] o [CLOUD]. |
| **[PROPUESTA]** | Detalle técnico propuesto por Food que aún requiere decisión (nombres de endpoint, códigos de error, límites). Puede cambiar. |
| **[PRUEBA CONJUNTA]** | Comportamiento que solo se considera validado después de la prueba conjunta Food–Rider. |

**Regla para Rider:** no envíe campos nuevos ni dependa de comportamientos [ACORDADO] hasta que Food comunique la revisión publicada. `riderApi` valida las claves **exactas** del cuerpo: una clave nueva, incluso con valor `null`, devuelve **422** en la revisión publicada.

## 1. Seguridad y autenticación

- **[CLOUD]**
  - `riderApi` es una Cloud Function v2 privada (`invoker: "private"`) en `chaski-food-9adba`, región `southamerica-west1`.
  - Solo acepta llamadas autorizadas por IAM (`roles/run.invoker`).
  - **URL base y audiencia del ID token OIDC:** `https://riderapi-u65lcqarqa-tl.a.run.app`. Rutas bajo `/integration/v1/`.
  - **Cabeceras:** `Authorization: Bearer <ID token OIDC de la cuenta de servicio del backend Rider>` y `Content-Type: application/json`.
  - **Sin token, con otro tipo de token u otra audiencia:** Cloud Run responde 401/403 antes del código de Food, sin cuerpo JSON de Food.
  - La API no distingue identidades internamente: cualquier invocador autorizado ve todos los pedidos elegibles.
- **[ACORDADO]** Responsabilidades de identidad de Rider:
  - autenticar en su backend al repartidor que usa Android;
  - comprobar que su UID corresponde a la asignación vigente antes de mostrarle el `pickupCode`;
  - revocar ese acceso al liberar, reasignar o cerrar el pedido.

  Food exige, además, que la asignación siga vigente al verificar el código y al aplicar `PICKED_UP`.
- **[ACORDADO] Datos mínimos:**
  - se retira `customer.id`;
  - Rider recibe solo lo necesario para entregar: nombre visible, teléfono, dirección, referencia, indicaciones y coordenadas.

Food no crea ni administra repartidores. Rider es la autoridad sobre ofertas, asignaciones y liberaciones; Food lo es sobre el pedido comercial, sus estados, el código de recojo y el registro del cobro.

## 2. Modelo de integración [ACORDADO]

- **Modelo pull:** el pedido es descubrible al pasar a `READY_FOR_PICKUP`. Rider lo descubre consultando `riderApi`; Food no envía solicitudes de reparto.
- **Identificadores:** `orderId` (64 caracteres hexadecimales) identifica el pedido comercial; `assignment.id` + `assignment.version` identifican la asignación logística. **No se usan** `deliveryRequestId` ni `deliveryId` en esta fase.
- **Comunicación:** solo REST entre backends con OIDC. Los proyectos Firebase y sus Firestore son independientes.
- **Consulta:** el backend Rider consulta cada **15 segundos** mientras haya repartidores disponibles (valor inicial ajustable).
- **Paginación y deduplicación:** en cada pasada, Rider recorre **todas** las páginas desde el inicio y deduplica por `orderId`. La ausencia de un pedido en una página, o una consulta fallida, no prueba que se haya asignado o cancelado: los pedidos conocidos se reconcilian con el detalle.
- **Pedido sin repartidor:** sigue en `READY_FOR_PICKUP` y continúa la búsqueda. **No hay cancelación automática** mientras no se acuerde esa funcionalidad (quién cancela, con qué plazo y cómo lo detecta Rider).
- **Una sola vía:** cuando la integración esté activa, los pedidos gestionados por Rider avanzan solo con eventos de Rider. El backend de Food rechazará las transiciones simuladas sobre ellos.

## 3. `GET /integration/v1/orders` — pedidos disponibles

| Aspecto | [CLOUD] y [REPO] hoy | [ACORDADO] |
|---|---|---|
| Parámetros | `limit` 1–50 (por defecto 20); cualquier otro → `400 INVALID_REQUEST` | `limit` 1–50 y `cursor` opcional |
| Elegibilidad | `READY_FOR_PICKUP`, sin asignación, `paymentStatus=PAID` y `paymentSimulation=true`. **Los pedidos actuales en efectivo no aparecen** | `READY_FOR_PICKUP` sin asignación, con `CASH_ON_DELIVERY` + `PENDING`. Los pedidos antiguos de prepago simulado siguen siendo compatibles. **Pedidos con `pickupLock`: pendiente de decisión (ver abajo)** |
| Orden | Ninguno | `readyAt` ascendente, desempate por `orderId` |
| Límite | Se aplica **antes** de descartar los no elegibles (una página puede quedar vacía aunque existan pedidos válidos) | La elegibilidad se aplica antes de completar la página |
| Paginación | No | `nextCursor` opaco; `null` al final |
| `pickupCode` | Incluido | **Nunca** en el listado |
| `customer.id` | Incluido | Retirado |
| `payment` | `{flow:"PREPAID_SIMULATION", method:"SIMULATED", status:"PAID"}` | `{method, status, amountToCollectMinor, currency}` (§5) |

**[REPO, E1.3, no desplegado]** Cursor por valores exactos: base64url de `{"v":1,"s":<segundos>,"n":<nanosegundos>,"id":"<orderId>"}`, con el `readyAt` del último documento **examinado**, sin pérdida de precisión ni lectura de un documento ancla. Cualquier cursor mal formado, alterado o de otra versión → `400 INVALID_REQUEST`; Rider debe tratarlo como opaco.

**[PROPUESTA]**
- El cursor representa el **último pedido examinado**, no el último devuelto.
- Un pedido liberado conserva su `readyAt` original, por eso la pasada siempre empieza desde el inicio.
- Ordenar por `readyAt` requiere un índice compuesto nuevo en Firestore, que solo se publicará con autorización.
- Los pedidos antiguos sin `readyAt` no aparecerían en el listado ordenado.

**Pendiente de decisión (E1/E2): pedidos bloqueados en el listado.** Un pedido con `pickupLock` normalmente conserva su asignación, así que no figura como disponible. Falta acordar:
- si un pedido bloqueado y **sin** asignación debe ofrecerse a otros repartidores o excluirse hasta el desbloqueo;
- cómo sigue Rider un pedido que ya conoce.

Mientras tanto, Rider debe seguir los pedidos conocidos por el **detalle** (§4), que siempre muestra `pickupLock`, y no por su presencia en el listado.

**Respuesta [ACORDADO]** (ejemplo **parcial**: cada elemento de `orders` tiene la estructura del detalle de §4, sin `pickupCode`):

```json
{
  "contractVersion": 1,
  "orders": [ { "...": "misma estructura del detalle (§4), sin pickupCode" } ],
  "nextCursor": "opaco-o-null"
}
```

## 4. `GET /integration/v1/orders/{orderId}` — detalle y reconciliación

Rider lo usa para:
- reconciliar después de un 409, un timeout o una desconexión;
- recuperar la verificación del código al reanudar la pantalla de recojo;
- consultar pedidos ya **completados**, con su resultado de entrega y de cobro (`collectionStatus`, `collectionReview`), incluidas las incidencias abiertas.

| Caso | [CLOUD] y [REPO] hoy |
|---|---|
| Pedido elegible en `READY_FOR_PICKUP`, `IN_DELIVERY` o `COMPLETED` | `200 {contractVersion:1, order}` |
| `orderId` mal formado o inexistente | `404 ORDER_NOT_FOUND` |
| Otro estado u otra elegibilidad de pago (hoy, todo pedido en efectivo) | `409 ORDER_NOT_READY` |

**Hoy [REPO]:**
- el detalle **devuelve `pickupCode` a cualquier invocador** en cualquier estado elegible;
- `dispatch` y `tracking` se copian del documento interno, con fechas en formato Firestore (`_seconds`/`_nanoseconds`) y campos técnicos (`payloadHash`, `receivedAt`).

**[ACORDADO]:**
- se exponen `pickupCode` (solo cuando corresponde), `pickupVerification`, `pickupLock`, `payment.amountToCollectMinor`, `collectionStatus` y `collectionReview`;
- `pickupLock` no incluye datos internos de la auditoría administrativa.

**[PROPUESTA]:**
- `dispatch` y `tracking` se normalizan a fechas ISO 8601 UTC con milisegundos y sin campos técnicos;
- `readyAt` se incluye para la paginación.

**Ejemplo completo [ACORDADO]/[PROPUESTA]**, pedido verificado y todavía sin recoger (datos ficticios). Está en formato `jsonc`: quita los comentarios `//` para usarlo como JSON en las pruebas de contrato.

```jsonc
{
  "contractVersion": 1,
  "order": {
    "schemaVersion": 1,
    "orderId": "<64 hex>",
    "updatedAt": "2026-11-15T15:41:58.004Z",
    "status": "READY_FOR_PICKUP",
    "readyAt": "2026-11-15T15:30:02.000Z",                    // [PROPUESTA]
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
    "collectionStatus": null,
    "collectionReview": null,
    "dispatch": {
      "status": "AT_MERCHANT", "lastEventSequence": 4, "lastAssignmentVersion": 2,
      "assignment": { "id": "ASG_1001_2", "version": 2 },
      "rider": { "id": "RIDER_88", "displayName": "Repartidor", "phone": null, "vehicleType": "MOTORCYCLE", "vehiclePlate": "ABC-123" },
      "deliveryOutcome": null, "reason": null, "updatedAt": "2026-11-15T15:41:58.004Z"
    },
    "tracking": null
  }
}
```

**Reglas de los campos del recojo [ACORDADO]:**

| Situación | `pickupCode` | `pickupVerification` | `pickupLock` |
|---|---|---|---|
| Sin asignación vigente | `null` | `null` | `null` o bloqueo vigente |
| Asignación vigente, sin verificar, sin bloqueo | Código de 4 dígitos (para el repartidor vigente) | `null` | `null` |
| Verificado para la asignación vigente | [PROPUESTA] `null` | `{assignmentId, assignmentVersion, verifiedAt}` | `null` |
| Bloqueado por intentos | `null` | `null` | `{ "lockedAt": "…", "reason": "MAX_ATTEMPTS" }` |
| Tras una liberación | `null` (se conserva internamente) | `null`; la anterior queda invalidada | Se conserva si existía |

- Una verificación está **vigente** solo si `pickupVerification.assignmentId/assignmentVersion` coincide con `dispatch.assignment`.
- **Validar el código no equivale al recojo físico.** El recojo físico lo declara Rider con `PICKED_UP`.

## 5. Pago contra entrega y cobro [ACORDADO]

- **Fase actual:** solo `CASH_ON_DELIVERY` con `paymentStatus=PENDING` hasta el cobro.
  - Importes en **céntimos de PEN** (enteros).
  - `amountToCollectMinor` es el saldo exacto que debe cobrar el repartidor; hoy coincide con `totalMinor` = subtotal + reparto + propina.
  - **No hay pagos parciales.**
- **Medios declarados por Rider:** `CASH`, `YAPE`, `PLIN`. Son el medio con el que el comprador pagó al repartidor, no métodos de pago de Food.
- **`collection` según el evento:**

| Evento | `collection` |
|---|---|
| `COMPLETED_SUCCESSFULLY` contra entrega | Objeto obligatorio `{method, amountMinor, currency}` |
| `COMPLETED_UNSUCCESSFULLY` sin dinero recibido | `null` (clave presente) |
| Cualquier otro evento | Clave **ausente** |

- **Cobro correcto:** el importe coincide con `amountToCollectMinor`. Food registra el pago confirmado (`PAID`) y el pedido `COMPLETED` en **una sola transacción**.
- **Cobro discrepante:**
  - el pedido pasa a `COMPLETED` con `deliveryOutcome=SUCCESS`;
  - el pago **sigue `PENDING`** y se abre una incidencia `OPEN` con los importes esperado y declarado;
  - Food **nunca** registra como `PAID` un importe incorrecto;
  - la corrección posterior se registra como **ajuste trazable**, sin reescribir el evento original. El flujo de conciliación está pendiente de diseño.
- **Respuesta perdida:** Rider no vuelve a cobrar. Muestra «Cobro registrado · pendiente de sincronización» y reenvía **el mismo evento**; Food devuelve el resultado original.
- **Entrega fallida con movimiento de dinero:** es una incidencia que hay que tratar, no se declara falsamente `collection:null`. El tratamiento está pendiente de diseño.
- La liquidación de lo recaudado por el repartidor pertenece a Rider.
- **Fase futura opcional:** el pago anticipado gestionado por Food no está aprobado. Si se implementa, el saldo por cobrar sería 0 y el `deliveryCode` del comprador, distinto del `pickupCode` del local, tendría un ciclo propio.

**Ejemplo completo de cobro discrepante** (cuerpo y respuesta completos) [ACORDADO], valores [PROPUESTA]:

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

- Con el importe exacto: `collectionStatus` = `"RECORDED"` y `collectionReview` = `null` [PROPUESTA].
- Una moneda distinta de PEN o un método desconocido es un error de formato: `422` sin aplicar el evento.

## 6. `POST /integration/v1/rider-events` — eventos de reparto

Cuerpo [CLOUD]/[REPO]: todas las claves son obligatorias y exactas; `rider` solo va en `ASSIGNMENT_ACCEPTED`.

```json
{ "schemaVersion": 1, "eventId": "EV_RIDER_1001_1", "eventType": "ASSIGNMENT_ACCEPTED", "eventSequence": 1,
  "occurredAt": "2026-11-15T15:32:00.000Z", "orderId": "<64 hex>", "assignment": { "id": "ASG_1001_1", "version": 1 },
  "status": "ACCEPTED", "reason": null,
  "rider": { "id": "RIDER_88", "displayName": "Repartidor", "phone": null, "vehicleType": "MOTORCYCLE", "vehiclePlate": "ABC-123" },
  "deliveryOutcome": null }
```

Respuesta: `200 {contractVersion:1, eventId, orderId, applied:true, lastEventSequence}`.

| `eventType` | `status` | Efecto hoy [CLOUD]/[REPO] | Cambio [ACORDADO] |
|---|---|---|---|
| `ASSIGNMENT_ACCEPTED` | `ACCEPTED` | Registra la asignación y el repartidor; requiere `READY_FOR_PICKUP` | La vigencia de la asignación habilita el `pickupCode` en el detalle |
| `ARRIVED_AT_MERCHANT` | `AT_MERCHANT` | Requiere `READY_FOR_PICKUP` y la asignación vigente | — |
| `ASSIGNMENT_RELEASED` | `SEARCHING_RIDER` | Libera antes del recojo; conserva el pedido y el código | Invalida la verificación de esa asignación; **no** reinicia intentos ni quita un bloqueo |
| `PICKED_UP` | `ON_THE_WAY` | Pasa a `IN_DELIVERY` **sin verificar el código** | Solo con verificación **vigente** de la misma asignación; si no, 409 ([PROPUESTA] `PICKUP_NOT_VERIFIED`) |
| `ARRIVED_AT_CUSTOMER` | `AT_CUSTOMER` | Requiere `IN_DELIVERY` | — |
| `COMPLETED_SUCCESSFULLY` | `COMPLETED` | Pasa a `COMPLETED` con `SUCCESS` | Exige `collection` y registra el cobro o la discrepancia (§5) |
| `COMPLETED_UNSUCCESSFULLY` | `COMPLETED` | Pasa a `COMPLETED` con `UNSUCCESSFUL` | Exige `collection: null` |

- **Hoy, todos los eventos exigen `PAID` + `paymentSimulation`.** Los pedidos en efectivo responden `409 ORDER_NOT_READY` [CLOUD]/[REPO]. [ACORDADO]: se admiten pedidos `CASH_ON_DELIVERY` + `PENDING`.
- **Doble asignación:** `409 ASSIGNMENT_CONFLICT` si ya existe una asignación vigente. Es [REPO], **no está en [CLOUD]**: se publicará antes de la prueba con dos repartidores.
- **Avisos al comprador (HU24):** asignación, recojo y resultado de entrega. Son [REPO], no están en [CLOUD].

### Secuencia, idempotencia y recuperación

- **[CLOUD]/[REPO]:**
  - `eventSequence` es **una sola por pedido**, no por repartidor; continúa tras liberaciones y reasignaciones;
  - cada evento debe ser exactamente `lastEventSequence + 1`; si no, `409 EVENT_SEQUENCE_CONFLICT`;
  - un evento rechazado sin aplicarse **no consume** su secuencia;
  - repetir el mismo `eventId` con el mismo cuerpo devuelve el resultado original (también después de cambios de estado posteriores); el mismo `eventId` con otro cuerpo → `409 IDEMPOTENCY_CONFLICT`;
  - un evento de una asignación que ya no está vigente → `409 ASSIGNMENT_STALE`.
- **[ACORDADO] Responsabilidades de Rider:**
  - el **backend** coordina los eventos y asigna la secuencia por pedido;
  - mantiene una **cola de salida duradera**: persiste cada evento antes de enviarlo y lo procesa en orden por `orderId`;
  - las acciones de Android conservan identificadores estables hacia ese backend;
  - ante timeout, 503 o resultado desconocido, reenvía el **mismo** `eventId`, cuerpo y secuencia con espera creciente;
  - ante 409, consulta el detalle y concilia la asignación y `lastEventSequence`;
  - ante 422, corrige el contenido: un evento no aplicado puede reemitirse con un `eventId` nuevo y la misma secuencia;
  - los eventos pendientes de un repartidor liberado se descartan sin dejar huecos;
  - impide que dos repartidores ganen la misma asignación.
- **[PRUEBA CONJUNTA]:** desconexión real, pérdida del ACK, reintento del evento final, duplicados y doble aceptación.

## 7. `PUT /integration/v1/orders/{orderId}/tracking`

- **[CLOUD]:**
  - solo con el pedido `IN_DELIVERY`, la asignación vigente y el mismo `riderId`;
  - la respuesta es `200 {contractVersion:1, orderId, assignmentVersion, acceptedSequence, received}`;
  - una secuencia menor no se aplica (`received:false`); la misma secuencia con otro cuerpo → `409 IDEMPOTENCY_CONFLICT`.

```json
{ "assignmentId": "ASG_1001_2", "assignmentVersion": 2, "sequence": 19, "riderId": "RIDER_88",
  "latitude": -12.123, "longitude": -77.031, "accuracyMeters": 15, "recordedAt": "2026-11-15T16:00:00.000Z" }
```

- **[REPO]:**
  - rechaza un `recordedAt` más de 60 s en el futuro (422);
  - una muestra más antigua no reemplaza a la vigente;
  - acepta `estimatedArrivalAt` opcional (ISO, entre `recordedAt` y 24 h después). **[CLOUD] lo rechaza con 422.** Rider lo enviará solo cuando Food publique la revisión.
- **[ACORDADO]:** el tracking empieza después del recojo y termina al completar el pedido.
- **Comprador:** lo consulta con foodApi (`GET /api/v1/me/orders/{orderId}/tracking`), sin PIN, teléfono, placa ni ID del repartidor. Ver [HU23_SEGUIMIENTO](HU23_SEGUIMIENTO.md).

## 8. Operaciones de Food para el código de recojo (foodApi, no riderApi)

Las usa la app de Chaski Food, **no Rider**. Se documentan para que Rider entienda el ciclo del código.

- **[REPO]/[CLOUD foodApi]:** `POST /api/v1/stores/{storeId}/orders/{orderId}/ready-for-pickup`.
  - Solo el responsable VERIFIED asignado al local.
  - `PREPARING → READY_FOR_PICKUP`.
  - Genera el código de 4 dígitos (`0000`–`9999`); el reintento devuelve el mismo.
- **[ACORDADO] Verificación del código:**
  - **Quién:** el responsable autorizado del local introduce el código que muestra el repartidor.
  - **Qué valida Food:** permisos, pedido, estado `READY_FOR_PICKUP`, código y asignación vigente.
  - **Qué guarda:** `pickupVerification` ligada a `assignmentId/version`.
  - Food muestra al responsable la **identidad del repartidor vigente** (nombre y placa; la placa es `null` en bicicleta).
  - **Validar el código no produce `PICKED_UP`:** el pedido sigue `READY_FOR_PICKUP` hasta que Rider envía un `PICKED_UP` válido.
  - **Pendiente de decisión (E2/E3):** hoy la app de Food muestra el código al responsable («Código de recojo: XXXX»). Si el responsable puede verlo, la validación pierde su propósito. Se propone ocultarlo en la interfaz y en las respuestas de foodApi al local; se decide en E2/E3.
- **[ACORDADO] Intentos y bloqueo:**
  - **5 fallos acumulados por pedido y ciclo de verificación**;
  - una reasignación **no** reinicia el contador ni quita un bloqueo;
  - al alcanzar el límite, el pedido queda bloqueado (`pickupLock`) y Rider muestra «Recojo pendiente de revisión».
- **[ACORDADO] Desbloqueo:**
  - solo un **administrador autorizado de Food**;
  - registra el motivo;
  - genera un **código nuevo**, invalida el anterior e inicia un ciclo nuevo de 5 intentos.
- **[ACORDADO] Auditoría e idempotencia:**
  - los intentos y los desbloqueos son idempotentes y auditables;
  - **nunca se guardan los códigos introducidos.**
- **[REPO, E2, no desplegado] Endpoints de foodApi:**

  | Ruta | Quién | Respuestas |
  |---|---|---|
  | `POST /api/v1/stores/{storeId}/orders/{orderId}/pickup-verifications` con `{operationId (UUID v4), code (4 dígitos), assignmentId, assignmentVersion}` | Responsable VERIFIED asignado al local | `200 {outcome: VERIFIED o ALREADY_VERIFIED, attemptsRemaining, replayed}`; `409 PICKUP_CODE_INVALID {attemptsRemaining}`; `409 ASSIGNMENT_CHANGED`; `409 ORDER_STATE_CHANGED`; `423 PICKUP_LOCKED`; `409 IDEMPOTENCY_CONFLICT` (mismo `operationId` con otro actor o asignación); 400/401/403/404 |
  | `GET /api/v1/admin/pickup-incidents` | Claim `admin` con correo verificado | `200 {incidents: [{orderId, shortId, storeId, storeName, status, lockedAt, cycle, failures, hasAssignment}], truncated}` (máximo 50, sin PIN ni datos del comprador) |
  | `POST /api/v1/admin/pickup-incidents/{orderId}/unlock` con `{operationId, reason (5–200)}` | Claim `admin` | `200 {outcome: UNLOCKED, cycle, replayed}` (sin PIN); `409 PICKUP_NOT_LOCKED`, `409 ORDER_STATE_CHANGED`, `409 IDEMPOTENCY_CONFLICT` |

  - **Persistencia:** en el pedido, `pickupSecurity {cycle, failures, lockedAt, verification}` (aditivo; si no existe, ciclo 1). Cada intento queda en `pickup_attempts/{operationId}` (actor, asignación, ciclo, resultado; **sin el código**) y cada desbloqueo en `pickup_unlocks/{operationId}`.
  - **Atomicidad:** un intento fallido se confirma antes de responder 409/423. El quinto fallo bloquea en la misma escritura. Los intentos concurrentes se serializan.
  - **Desbloqueo:** PIN nuevo distinto del anterior, ciclo + 1, contadores a cero; se conservan los intentos anteriores. Rider obtiene el código nuevo por el detalle si la asignación sigue vigente.
  - **[REPO, E5.2] El PIN ya no se envía al responsable** (bandeja ni `markReady`), en ningún local. Solo lo recibe el backend Rider en el detalle, con una asignación vigente. La bandeja incluye `pickup {assignment (sin id ni teléfono del repartidor), verified, locked, attemptsRemaining}`. Requisito: todos los participantes deben usar el APK actual (E3 o posterior); no hay compatibilidad con APKs anteriores.
- **Pendiente:** la interfaz del responsable y del administrador (E3, Pencil primero).

## 9. Errores

Cuerpo de error de Food: `{"error":{"code":"…","retryable":<true solo en 5xx>}}`.

| HTTP | Código | Estado |
|---|---|---|
| 400 | `INVALID_REQUEST` (parámetro no admitido) | [CLOUD] |
| 401/403 | Sin cuerpo de Food (IAM o Cloud Run) | [CLOUD] |
| 404 | `NOT_FOUND` (ruta inexistente), `ORDER_NOT_FOUND` | [CLOUD] |
| 405 | `METHOD_NOT_ALLOWED` | [CLOUD] |
| 409 | `ORDER_NOT_READY`, `EVENT_SEQUENCE_CONFLICT`, `ASSIGNMENT_STALE`, `ORDER_STATE_CONFLICT`, `IDEMPOTENCY_CONFLICT` | [CLOUD] |
| 409 | `ASSIGNMENT_CONFLICT` | [REPO] |
| 409 | `PICKUP_NOT_VERIFIED` (recojo sin verificación vigente), `PICKUP_LOCKED` (aceptar un pedido bloqueado) | [REPO, E2] |
| 422 | `INVALID_REQUEST` (cuerpo inválido o clave desconocida) | [CLOUD] |
| 503 | `RIDER_API_UNAVAILABLE`, `DATA_INCONSISTENT` (reintentable) | [CLOUD] |

## 10. Compatibilidad y activación coordinada [ACORDADO]

- **Versión:** se conserva `/integration/v1`. Rider confirmó que retirar `customer.id` y `pickupCode` del listado es compatible con su adaptador; Food no tiene otros consumidores de `riderApi`.
- **Cambios de escritura:** exigir la verificación para `PICKED_UP` y los datos de cobro al finalizar cambian el comportamiento. Primero se documentan ejemplos definitivos y se prueban en LOCAL, y luego se activan para **pedidos de prueba identificados por ambos equipos**.
- **Orden de despliegue:**
  1. Food publica `riderApi` y comunica la revisión.
  2. Rider activa los campos nuevos.
  3. Se hace la prueba conjunta.
  4. Se activa en general.
- **Si apareciera un consumidor del formato anterior,** se mantendría la compatibilidad para él o se habilitaría v2.

## 10b. Activación y piloto [REPO, E5.3, no desplegado]
- **Parámetro de despliegue `CHASKI_RIDER_ACTIVATION`:**
  - sin definir o vacío → **desactivada** (por defecto): el listado viene vacío y los pedidos que no iniciaron el flujo de Rider son invisibles;
  - `pilot:<storeId>,<storeId>` → solo esos locales (ids de Firestore; nunca nombres ni ids fijados en el código);
  - `general` → todos los locales.
  - Cualquier otro valor → `503` (falla en modo cerrado).
- **Fuera del ámbito:** el pedido no se ofrece; el detalle, los eventos y el tracking responden `404 ORDER_NOT_FOUND`, salvo que el pedido **ya haya iniciado** el flujo de Rider. Así, una entrega en curso puede terminarse y conciliarse aunque el piloto se reduzca. Una **asignación nueva** (`ASSIGNMENT_ACCEPTED`) fuera del ámbito → `409 ORDER_NOT_READY`.
- **Simulaciones de Food** (E5.1): `409 RIDER_MANAGED` si el pedido tiene eventos de Rider, verificación o bloqueo de PIN, cobro o revisión, o si su local está dentro del ámbito, aunque Rider aún no haya enviado eventos. No afecta al contrato de Rider.

## 11. Estado de despliegue y diferencias publicadas

| Elemento | [CLOUD] `riderapi-00001-xeg` | [REPO] `8faa72c` (E1–E4) + E5 local sin commit (15-11) | [ACORDADO] |
|---|---|---|---|
| Elegibilidad del efectivo | No | **Sí (E1.2):** `CASH_ON_DELIVERY` + `PENDING` con código, en listado, detalle y eventos `ASSIGNMENT_ACCEPTED`, `ARRIVED_AT_MERCHANT` y `ASSIGNMENT_RELEASED`. `PICKED_UP`, `ARRIVED_AT_CUSTOMER` y `COMPLETED_*` → `409 ORDER_NOT_READY` **temporal** hasta E2/E4 (no apto para la integración real). Prepago antiguo sin cambios | Sí |
| `pickupCode` en el listado | Sí | **No** (E1.1) | No |
| `pickupCode` en el detalle | Siempre | **Solo `READY_FOR_PICKUP` con asignación vigente; si no, `null`** (E1.1) | Además, ni verificado ni bloqueado (E2) |
| `customer.id` | Sí | **Retirado** (E1.1) | Retirado |
| `payment` | `PREPAID_SIMULATION/SIMULATED/PAID` | **`{method, status, amountToCollectMinor, currency}`**; prepago antiguo: `CASH_ON_DELIVERY`, `PAID`, `0` (E1.1) | Sí |
| `readyAt` | No | **ISO o `null`** (E1.1) | Sí |
| `dispatch` y `tracking` normalizados (ISO, sin `payloadHash`, `receivedAt` ni `riderId` en tracking) | No | **Sí** (E1.1) | Sí |
| `PICKED_UP` con verificación | No | **Sí (E2):** exige `pickupVerification` vigente de la misma asignación (también en el prepago antiguo); si no, `409 PICKUP_NOT_VERIFIED` sin consumir la secuencia | Sí |
| Pedidos bloqueados | No | **Sí (E2):** fuera del listado; el detalle devuelve `pickupLock` y `pickupCode: null`; `ASSIGNMENT_ACCEPTED` → `409 PICKUP_LOCKED`; `ASSIGNMENT_RELEASED` borra la verificación y conserva intentos y bloqueo | Sí |
| `pickupVerification` / `pickupLock` en el detalle | No | **Sí (E2)**; `pickupCode` es `null` si está verificado o bloqueado | Sí |
| Eventos de efectivo | No | Recorrido completo habilitado (E4); el bloqueo temporal de `COMPLETED_*` quedó retirado | Sí |
| `collection` y cobro | 422 | **Sí (E4):** obligatoria en `COMPLETED_*` y prohibida en el resto (422); `COMPLETED_SUCCESSFULLY` en efectivo exige un objeto (`CASH`, `YAPE` o `PLIN`, entero de 1 a 10 000 000, `PEN`); `COMPLETED_UNSUCCESSFULLY` y el prepago antiguo exigen `null`. Importe exacto respecto del total de Food → `PAID` más `paymentCollection` más `payment_history/rider_collection` (`RECORDED`); distinto → `PENDING` más `collectionReview` `OPEN` (`DISCREPANCY`). La respuesta y el detalle incluyen `collectionStatus` y `collectionReview`; repetir el evento devuelve el resultado original | Sí |
| Paginación y orden | No | **Sí (E1.3):** `status == READY_FOR_PICKUP` + `readyAt >= 0` ordenados por `readyAt` y luego `__name__`; `limit` 1–50 (por defecto 20) y `cursor` opaco; la elegibilidad se aplica antes de llenar la página; como máximo 200 documentos examinados por petición, en bloques de 50; `nextCursor` = último documento **examinado** (puede venir con una página vacía) y `null` al terminar. Índice compuesto `status`, `readyAt`, `__name__` añadido **solo** a `firestore.indexes.json` (no desplegado). Pedidos sin `readyAt`: fuera del listado, accesibles por el detalle | Sí |
| `ASSIGNMENT_CONFLICT` | No | Sí | Sí |
| Avisos al comprador por eventos Rider | No | Sí | Sí |
| Validaciones de tracking y `estimatedArrivalAt` | No | Sí | Sí |

Food comunicará a Rider la revisión publicada antes de que se activen los campos nuevos. Los procedimientos internos de despliegue y de IAM de Food no forman parte de este contrato: ver [GUIA_DESARROLLO §2.4](GUIA_DESARROLLO.md).

## 12. Pruebas conjuntas previstas [PRUEBA CONJUNTA]

1. **Contra entrega con desconexión real:**
   - Food marca listo, Rider asigna y el local valida el código;
   - Rider pierde la conexión y recupera la verificación;
   - se recoge, se envía tracking y se completa con cobro;
   - se repite el evento final.

   **Se comprueba:** una sola asignación vigente, un recojo y un cobro.
2. **Doble aceptación:** dos repartidores intentan aceptar el mismo pedido → `409 ASSIGNMENT_CONFLICT` en la revisión publicada.
3. **Liberación antes del recojo:** la asignación anterior ya no puede verificar, recoger ni enviar tracking.
4. **Paginación:** la cobertura con más de 50 pedidos se demuestra en LOCAL antes de la activación general.

Historial: el análisis del 404 de `/integration/v1/_probe_` (09-11) quedó cerrado por ambos equipos; no es un defecto de la API.
