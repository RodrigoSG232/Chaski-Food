# Cambios del contrato `/integration/v1` para Chaski Rider (preparación de E6)

> Resumen para el equipo Rider (Ángel). La fuente completa es [API_CHASKI_RIDER.md](API_CHASKI_RIDER.md).
>
> **Comparación:**
> - **PUBLICADO:** `riderapi-00001-xeg`, el que Rider usa hoy.
> - **NUEVO:** repositorio de Food en `5edaf91`, **todavía no desplegado**.
>
> Este documento no afirma que Rider haya implementado nada. Cada fila indica qué hay que **implementar** y qué hay que **confirmar**.

## 1. Autenticación (sin cambios de mecanismo)
- Cloud Run privado: Rider llama con un **ID token de Google** de **su cuenta de servicio**, con la audiencia = URL de riderApi.
- Food concede `run.invoker` (directo) solo a `chaski-food-client@chaski-rider.iam.gserviceaccount.com` (G7-A).
- **[NUEVO, G7-B] Verificación del llamador en riderApi:** además de IAM, cada petición debe llevar:
  - `Authorization: Bearer <ID token de Google>` **firmado** para esa cuenta;
  - **audiencia exacta `https://riderapi-u65lcqarqa-tl.a.run.app`**, la URL canónica (otras URLs del servicio **no** se aceptan como audiencia);
  - el claim `email` con `email_verified: true` (token en formato **full** / `includeEmail`).

  Si no se cumple → `403 {"error":{"code":"CALLER_NOT_ALLOWED","retryable":false}}`.
- **No usar `X-Serverless-Authorization`:** se rechaza siempre.
- **Confirmar en G6:** que su token cumple los tres requisitos (la prueba real con su cuenta está pendiente).
- **Piloto:** con la integración desactivada, el listado viene vacío y el detalle responde 404. Food activará un único local.

## 2. Diferencias

| Tema | PUBLICADO | NUEVO | Rider debe |
|---|---|---|---|
| **Descubrimiento** | Listado sin orden ni cursor; incluye `pickupCode` y `customer.id` | `GET /orders?limit=1..50&cursor=` ordenado por `readyAt` e id; `nextCursor` opaco (puede venir con una página vacía; `null` al terminar); **sin** `pickupCode` ni `customer.id`; `readyAt` en ISO | Paginar hasta `nextCursor:null`; no interpretar el cursor; tolerar páginas vacías |
| **Pago** | `PREPAID_SIMULATION/SIMULATED/PAID` | `payment {method:"CASH_ON_DELIVERY", status, amountToCollectMinor, currency:"PEN"}` (céntimos) | Cobrar `amountToCollectMinor` |
| **Detalle** | El PIN siempre | `pickupCode` solo en `READY_FOR_PICKUP` con **asignación vigente**, sin verificar y sin bloqueo; si no, `null`. Además `pickupVerification`, `pickupLock`, `collectionStatus` y `collectionReview` | Mostrar el PIN **solo al repartidor asignado** |
| **Asignación y liberación** | Versión de la asignación | Igual, y además `ASSIGNMENT_CONFLICT` y `ASSIGNMENT_STALE`. `ASSIGNMENT_RELEASED` borra la verificación del local (los intentos y el bloqueo se conservan) | Incrementar `assignment.version` al reasignar |
| **Verificación del PIN** | No existe | El **local** verifica el código en Food. `PICKED_UP` sin verificación vigente de la misma asignación → `409 PICKUP_NOT_VERIFIED` (no consume la secuencia) | Esperar la verificación y reintentar `PICKED_UP` con el mismo evento |
| **Bloqueo** | No existe | 5 intentos fallidos → bloqueo. `pickupLock` en el detalle; fuera del listado; `ASSIGNMENT_ACCEPTED` → `409 PICKUP_LOCKED`. Un administrador de Food desbloquea y **regenera** el PIN | Volver a consultar el detalle tras el desbloqueo |
| **Eventos logísticos** | Sin validaciones nuevas | `eventSequence` por pedido (+1 estricto), `eventId` idempotente con hash del contenido completo; mismo `eventId` con otro contenido → `409 IDEMPOTENCY_CONFLICT` | Cola duradera; ante un resultado desconocido, reenviar **el mismo** evento |
| **Cobro (`collection`)** | `422` | **Obligatorio** en `COMPLETED_*`: éxito en efectivo → `{method:CASH\|YAPE\|PLIN, amountMinor 1..10 000 000, currency:"PEN"}`; fallo y prepago antiguo → `null`; en otros eventos, ausente | Enviarlo siempre en el evento final |
| **Discrepancia** | — | Importe ≠ total de Food → pedido `COMPLETED`, pago `PENDING`, `collectionStatus:"DISCREPANCY"`, `collectionReview {status:"OPEN",expectedMinor,declaredMinor,method,currency}`. Exacto → `RECORDED` y `PAID` | Mostrar «cobro en revisión»; no recobrar |
| **Conciliación (§5b)** | — | Un administrador de Food resuelve. `collectionReview.status:"RESOLVED"` más `outcome FULLY_COLLECTED\|NOT_FULLY_COLLECTED`, `confirmedMinor`, `shortfallMinor`, `excessMinor`, `excessStatus NONE\|PENDING_REFUND\|REFUNDED`. `collectionStatus` sigue en `DISCREPANCY` (histórico) | Tolerar los campos nuevos; usarlos en su liquidación |
| **Excedentes** | — | Food registra la devolución hecha **fuera** de la app (`REFUNDED`) | Acordar quién devuelve y cómo se informa |
| **Entrega fallida con dinero** | — | `COMPLETED_UNSUCCESSFULLY` con `collection ≠ null` → **422**. Procedimiento manual ([PREPARACION_E6 §8](PREPARACION_E6.md)) | Reportar fuera de banda; enviar `collection:null` |
| **Errores** | Parciales | `{error:{code,retryable}}`: 400/422 formato; 404 `ORDER_NOT_FOUND`; 409 `ORDER_NOT_READY`, `ORDER_STATE_CONFLICT`, `EVENT_SEQUENCE_CONFLICT`, `ASSIGNMENT_*`, `PICKUP_*`, `IDEMPOTENCY_CONFLICT`; 503 retryable | Reintentar solo si `retryable` |
| **Piloto** | — | `CHASKI_RIDER_ACTIVATION`: desactivado / `pilot:<storeIds>` / `general`. Fuera del ámbito: no hay ofertas ni asignaciones nuevas; las entregas ya iniciadas continúan | Tolerar el 404 y el listado vacío |

## 3. Pendiente de confirmar por Rider
1. La cuenta de servicio y la audiencia del token.
2. Que paginan, toleran páginas vacías y no muestran el PIN fuera de la asignación.
3. `PICKUP_NOT_VERIFIED` y `PICKUP_LOCKED`, y el flujo de espera.
4. `collection` en todos los `COMPLETED_*`.
5. Que toleran la §5b y el procedimiento de devolución.
6. Que la cola duradera resiste horas de 503 o 403 (suspensión).
7. El canal para incidentes de entrega fallida con dinero.
8. Su fecha de despliegue compatible.

## 4. Resumen operativo para la coordinación (G6, preparado; no enviado)

**Para compartir con Ángel y el equipo Rider. Nada de esto está confirmado por Rider todavía.**

- **Estado de Food [CLOUD, 15-11]:**
  - índice de descubrimiento desplegado (verificar READY en ESTADO);
  - `riderApi` publicado sigue siendo la versión anterior (`riderapi-00001-xeg`), y el contrato nuevo entrará en G5 con la integración **desactivada**;
  - la URL base de riderApi es `https://riderapi-u65lcqarqa-tl.a.run.app` (también responde por la URL `cloudfunctions.net`): la **audiencia del ID token debe coincidir con la URL que Rider invoque**.
- **Qué debe implementar Rider:** las filas de la §2 (paginación, PIN solo para el asignado, espera de verificación, `collection`, §5b, cola duradera).
- **Dependencias de despliegue:**
  1. Food despliega con la integración desactivada (G5);
  2. Rider despliega su versión compatible (G6);
  3. Food retira las cuentas sobrantes de IAM (G7);
  4. Food activa un local (G8).

  **Rider no debe apuntar su versión nueva a Food antes del paso 1:** el contrato publicado hoy es incompatible (422 en `collection`, PIN en el listado).
- **Pruebas conjuntas:** los casos 1–14 de [PRUEBAS_E6](PRUEBAS_E6.md); Rider aporta un repartidor de prueba y su app.
- **Riesgos por versiones:**
  - Rider nuevo contra Food antiguo → `collection` rechazada (422) y PIN expuesto en el listado;
  - Rider antiguo contra Food nuevo → `COMPLETED_*` sin `collection` (422) y `PICKED_UP` sin verificación (409).

  Por eso los dos despliegues deben coordinarse y el piloto no se activa hasta que ambos estén confirmados.
- **Criterios de aceptación de la integración:** la §3 confirmada por escrito más los casos 1–14 de PRUEBAS_E6 en OK.
