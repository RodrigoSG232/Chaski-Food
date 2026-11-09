# Decisiones vigentes de Chasqui Food

Decisiones **aprobadas** por el equipo que siguen aplicando y condicionan las próximas historias (HU17 en adelante). Cambiarlas requiere una decisión humana explícita. Entre paréntesis, el identificador original (DEC-HU16-xx, DEC-HU34-xx). Cómo están implementadas: [ARCHITECTURE](ARCHITECTURE.md); pendientes: [ESTADO_IMPLEMENTACION §4](ESTADO_IMPLEMENTACION.md#4-pendientes-y-mejoras-futuras).

## Backend y datos
- **Un solo backend: `foodApi`.** Las operaciones sensibles (registro y aprobación de negocios, asignaciones de responsables, pedidos, avisos) pasan por foodApi; no se crean backends paralelos ni se recupera `checkoutApi` (HU16-07, HU34-08, HU34-13).
- **Esquema propio y versionado** de pedidos: `food_orders` (+ `status_history`), `order_attempts/{uid}/intents`, `delivery_quotes`, `order_events`. Pedido, intento, historial inicial y evento se crean en la misma transacción (HU16-08, HU16-16).
- **El índice `commercial_access_index` es derivado y privado**: coordina UID y correo; Auth, `business_requests` y `store_managers` siguen siendo las fuentes. No es una colección de roles (HU34-20).
- **Sin eliminaciones automáticas** al preparar o reconciliar datos; los conflictos se detectan y se resuelven explícitamente (HU34-24).

## Pedidos (HU15–HU22)
- **Cotización**: foodApi la calcula con HeiGIT, la guarda y entrega `quoteId`; vale 5 minutos, pertenece al UID (no a la sesión) y se reutiliza mientras sea válida. Android nunca fija importes (HU16-01, HU16-06, HU16-20).
- **Tarifa y mínimo:** el reparto cuesta S/0.90 por kilómetro de ruta, sin base fija (`lima-driving-car-v2`). El subtotal de productos debe ser al menos S/25.00; foodApi lo revalida con precios vigentes.
- **Desglose:** el pedido conserva subtotal de productos, costo de reparto, propina opcional y total. La propina no se mezcla con el costo de reparto.
- **Confirmación autoritativa**: la transacción final revalida precios, permisos, responsables, dirección, cotización y horario; sin llamadas a HeiGIT/FCM dentro de la transacción (HU16-17).
- **Idempotencia**: cada intención tiene un `intentId` duradero por UID; repetirla devuelve el resultado original; el mismo ID con otro contenido se rechaza; una compra nueva usa otro ID (HU16-03, HU16-09).
- **Recuperación**: se distinguen éxito, rechazo final e incertidumbre; un timeout no significa fracaso; la intención se guarda en Room **antes** de llamar y una nueva revisión implica nueva intención, nunca un reenvío automático (HU16-02, HU16-18, HU16-22).
- **Consentimiento**: cualquier cambio relevante (incluidas subidas y bajadas de precio) exige nueva revisión (HU16-10).
- **Pedido histórico por valor**: se guardan comprador, negocio/local, líneas con nombre y precio, importes, pago (`CASH_ON_DELIVERY`, PEN), direcciones de recojo y entrega, instrucciones, contacto, cotización, estado y fechas. Editar los datos originales no cambia pedidos pasados (HU16-15).
- **Estado inicial `PENDING_CONFIRMATION`.** HU19 acepta pasando **directo a `PREPARING`**, sin estado `CONFIRMED` (aunque el PDF del curso lo mencione).
- **Cancelación HU18:** el comprador cancela directamente solo mientras el estado siga en `PENDING_CONFIRMATION`. Desde `PREPARING` no se permite cancelar; la transición compite atómicamente con la futura aceptación de HU19. Repetir una cancelación propia ya completada es idempotente. Un pedido con `deliveryId` requiere coordinación logística de HU22 y no se cancela silenciosamente.
- **Respuesta HU19:** solo un responsable VERIFIED asignado al local responde pedidos `PENDING_CONFIRMATION`. Aceptar pasa directamente a `PREPARING` e incluye una estimación de 5–180 minutos; rechazar exige un motivo y «Otro» exige explicación. Durante la suspensión se conserva consulta y rechazo, pero se bloquea la aceptación.
- **Estimación HU20:** solo se modifica mientras el pedido permanezca `PREPARING`, dentro de 5–180 minutos y mediante una transacción de foodApi. Cada cambio crea historial versionado; repetir el mismo valor es idempotente. El cliente recibe el valor vigente al refrescar su detalle. Si ya existe `deliveryId`, se genera un evento durable `PREPARATION_ESTIMATE_UPDATED`; su consumo por Rider pertenece a HU22.
- **Pago educativo:** mientras no exista una pasarela real, el comprador puede simular el pago únicamente cuando el pedido está `PREPARING`. foodApi registra `paymentStatus=PAID`, `paymentSimulation=true` y `paidAt`; la interfaz advierte que no se efectúa ningún cobro real.
- **Código único:** foodApi genera un único `pickupCode` aleatorio de cuatro dígitos al confirmar el pago simulado. Marcar el pedido listo no crea ni reemplaza el código. Una reasignación o cancelación del repartidor conserva el mismo código del pedido; no se implementa un segundo PIN de entrega.
- **Recojo HU21:** solo un responsable autorizado cambia un pedido pagado de `PREPARING → READY_FOR_PICKUP`. Si el pago sigue pendiente, foodApi rechaza la transición. Si existe `deliveryId`, crea `ORDER_READY_FOR_PICKUP` para Rider incluyendo el código ya generado.
- **Límite entre apps:** Food no crea usuarios repartidores. `riderApi` es una función separada, privada mediante IAM y destinada exclusivamente al backend Rider. Expone el pedido y código; recibe asignación, liberación, fases y tracking. Rider sigue siendo la única autoridad que selecciona al repartidor.
- **Reasignación HU22:** `ASSIGNMENT_RELEASED` solo libera antes del recojo; no cancela la compra ni modifica el código. Una nueva asignación incrementa su versión. Después de `PICKED_UP` no se reasigna automáticamente y Food pasa a `IN_DELIVERY` únicamente por un evento Rider válido.
- **Para recibir pedidos** basta al menos un responsable asignado válido; no se exige que esté conectado. Sin responsables se bloquean confirmaciones nuevas (HU16-05, HU16-12).

## Consultas propias (HU17)
### Seguimiento HU23 (08-11-2026)
- **Aprobado por el usuario:** ETA proporcionado por Rider. Avanzar con simulación LOCAL mientras se recibe el contrato definitivo; actualizar después. En ausencia de ETA real, mostrar «ETA no disponible».
- **Implementación:** consulta propia en foodApi, fases sin mapa antes del recojo, última posición persistida y cierre solo por transición válida. Se usa el evento existente `COMPLETED_SUCCESSFULLY`; no se crea un evento `DELIVERED` incompatible.
- **Resultado de entrega:** `UNSUCCESSFUL` se muestra «Entrega no exitosa»; pedidos antiguos sin resultado, «Finalizado». Solo `SUCCESS` se etiqueta «Entregado».
- **Valores provisionales de implementación:** polling de 10 segundos (20/30 ante fallos), muestra desactualizada después de 120 segundos, extensión opcional `estimatedArrivalAt`. La fuente Rider está aprobada; estos detalles de intercambio aún deben confirmarse con su equipo.
- Sin cambios Cloud, IAM, PIN ni chat en esta historia. El simulador está bajo `functions/test/lab`, fuera del paquete de despliegue.

### Consulta e historial
- **Acceso:** el cliente usa la pestaña **Pedidos**; propietario y responsable, **Cuenta → «Mis compras»**, con la misma lista y detalle. Las rutas usan `verifyBuyerToken` y filtran por `buyerUid`: no exigen experiencia comercial ni conceden `START_PURCHASE` (D1, HU34-01/07).
- **Estados:** una sola tabla en foodApi. Activos: `PENDING_CONFIRMATION`, `PREPARING`, `READY_FOR_PICKUP`, `IN_DELIVERY`; históricos: `COMPLETED`, `CANCELLED`, `REJECTED`. Un estado válido desconocido se muestra como activo («Estado en actualización») y se registra; `status` ausente/nulo o documento corrupto → `503 DATA_INCONSISTENT` (D2, N1).
- **Refresco:** HTTP al abrir, al volver a la app y al deslizar; sin tiempo real ni caché local (D3).
- **Paginación:** un flujo cronológico por `buyerUid` (`createdAt desc, __name__ desc`), sin filtrar por estado en Firestore; lotes de 50 hasta 200 documentos por petición; el cursor apunta al **último documento examinado**, va ligado al UID y a la fase (SHA-256 sin clave: vincula, no autentica) y el servidor revalida el ancla. Una página vacía con cursor nunca se presenta como «sin pedidos».
- **Privacidad:** pedido ajeno o inexistente → mismo 404; el detalle usa los snapshots de HU16 sin coordenadas, datos del comprador, hashes, cotización ni `actorUid`; el historial solo muestra eventos reales de `status_history`.
- **Lista:** ID visible de 8 caracteres (consultas y navegación usan los 64); hasta dos productos con cantidad y «+N más» desde el snapshot; pago descrito de forma neutral («Efectivo contra entrega»).
- **Checkout:** el carrito muestra «Continuar»; la pantalla siguiente se titula «Confirmar pedido» y, tras el éxito (directo o recuperado), «Pedido enviado» con «Recibimos tu pedido» y «Ver mi pedido» (D4). Tras la compra, Atrás lleva a Pedidos y no vuelve a la revisión. Desde Cuenta → «Recuperar compra pendiente» el título es «Recuperar compra».

## Bandeja y avisos
- **La bandeja es la fuente de verdad**; FCM es solo aviso, puede duplicarse o perderse (HU16-04, HU16-13).
- Los pedidos se asocian al **local (`storeId`)**, no a una persona; un nuevo responsable autorizado ve los existentes (HU34-03).
- Avisos con evento persistente `NEW_ORDER`, procesador independiente, reintentos acotados, dispositivos por UID, destinatarios reautorizados en cada envío y payload mínimo; el **propietario no recibe** avisos de pedidos (HU16-04, HU16-21).
- Lecturas por HTTP autorizadas por foodApi; los documentos privados no se abren al cliente (HU16-19).
- **HU24:** la aceptación, el rechazo y el pedido listo crean un evento de aviso al comprador en la misma transacción que cambia el pedido. Los eventos válidos de Rider crean los avisos de asignación, recojo y resultado de entrega. Cada evento conserva un ID estable para reintentos e idempotencia.
- El dispositivo se registra para cualquier cuenta autenticada con acceso a sus compras; al enviar, el backend revalida la titularidad del pedido o la asignación del responsable. El push lleva solo tipo de evento, ID de evento, ID de pedido y UID destinatario. Un aviso antiguo puede llegar después de un cambio posterior: abrirlo siempre pide el pedido vigente a foodApi.
- Los avisos son de mejor esfuerzo: FCM puede retrasarlos, duplicarlos o perderlos. `NO_RECIPIENTS` es terminal y no existe replay operativo aprobado; el detalle y la bandeja siguen disponibles por HTTP.

## Roles y permisos
- **Una experiencia comercial por cuenta**: cliente, propietario o responsable. Sin autorizaciones comerciales se es cliente; la aprobación del negocio convierte en propietario (HU34-02, HU34-04).
- **Exclusividad**: un propietario aprobado no puede ser responsable (ni de otro negocio ni de sus locales). Un responsable puede tener varios locales. Combinaciones incompatibles → estado de **conflicto** explícito, sin precedencia automática (HU34-04, HU34-11).
- **Solo el cliente compra**. Propietario y responsable conservan la consulta de sus compras personales anteriores (HU34-01, HU34-07).
- **Un responsable no puede registrar un negocio**: debe usar otra cuenta con otro correo (HU34-10, alternativa B).
- **Propietario suspendido**: conserva su experiencia y ve estado y motivo, pero no gestiona locales; podrá apelar cuando exista HU35 (HU34-05).
- **Administrador**: es un claim (`admin`), no una experiencia; añade la sección Administración en Cuenta (solicitudes, supervisión, auditoría), sin dashboard ni acceso a pedidos (HU34-28).
- **Autorización en el servidor**: perder conexión no convierte en cliente; las operaciones protegidas requieren autorización vigente del servidor; no se muestran datos privados desde caché sin autorización verificable (HU34-06, HU34-14, HU34-21).

## Responsables
- Se asignan **por correo**, incluso antes de que exista la cuenta, sin exigir activación (HU34-09).
- El acceso operativo exige **autenticación y correo verificado** (vinculación `VERIFIED`) (HU34-09).
- Si el responsable cambia el correo de su cuenta, **no se transfieren** sus asignaciones; el acceso se suspende hasta una nueva vinculación (HU34-12).
- Revocar una asignación retira consultas y datos de esos locales; sin otras autorizaciones vuelve a cliente (HU34-03).

## Interfaz
- **Se conserva la interfaz actual del cliente** (Inicio, Buscar, Pedidos, Cuenta, catálogo, carrito, direcciones y Checkout) (HU34-29).
- Inicio del propietario: estado del negocio, Locales y responsables, Catálogo general, Horarios y Oferta/precios por local. Responsable: sus locales con selector, estado y accesos autorizados (HU34-27).
- Se conserva la ruta principal `MAIN` y sus ámbitos compartidos (HU34-26).
- **Pencil primero:** las interfaces nuevas se diseñan como frames nuevos con prefijo de historia (p. ej. «HU17 /»), sin sobrescribir los aprobados, y se aprueban antes de programarlas en Compose. Cada `*Screen.kt` nuevo incluye `@Preview` con datos ficticios, sin Hilt, Firebase ni red.
- **Agregar al carrito** vuelve al catálogo del local con el aviso «Producto agregado al carrito · Ver carrito» (HU14, aprobado durante HU17). Sin foto, el producto muestra «Sin imagen».
