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
  - **Quién decide:** el propietario no acepta ni rechaza pedidos (403), aunque sea dueño del negocio; tampoco un responsable de otro local. La decisión es una transacción de foodApi que compite con la cancelación HU18: solo una de las dos gana.
  - **Repetición:** reenviar la misma decisión del mismo responsable devuelve el resultado original sin escribir de nuevo; cualquier otra decisión sobre un pedido ya respondido o cancelado → `409 ORDER_STATE_CHANGED`.
  - **Suspensión:** la aceptación se bloquea (`423`) si el negocio no está `APPROVED` o el local no está `ACTIVE`.
  - **Motivos:** `STORE_BUSY` («Alta demanda», preseleccionado en el diálogo), `PRODUCT_UNAVAILABLE`, `CLOSING_SOON` y `OTHER`, este último con explicación de 3 a 300 caracteres. El diálogo del responsable se conserva sin cambios (14-11-2026).
  - **Motivo visible para el comprador (14-11-2026):** el detalle del pedido rechazado muestra el motivo y, en `OTHER`, la explicación registrada. Aplica también a pedidos antiguos que ya tengan esos datos, sin migrar ni modificar documentos; si el motivo falta o no se reconoce, se muestra «Motivo del rechazo no disponible.». Nunca se exponen `rejectedByUid` ni otros identificadores internos. **Los responsables deben redactar la explicación de «Otro» pensando que la leerá el comprador.**
- **Ampliación HU20 (rediseño aprobado 14-11-2026; sustituye al reemplazo de estimación anterior):** HU19 sigue fijando la estimación inicial de 5–180 minutos. HU20 solo **amplía** el plazo mientras el pedido esté `PREPARING`: cada ampliación suma de 5 a 60 minutos, sin tope acumulado por ahora, y la nueva hora es `max(hora estimada vigente, hora del servidor) + minutos` (una hora vencida se recalcula desde ahora).
  - Dos ampliaciones intencionales iguales son operaciones distintas y se suman; reintentar la misma operación (mismo `operationId`) nunca vuelve a sumar. Una pantalla con una revisión desactualizada no amplía: recarga y pide confirmar de nuevo.
  - Cada ampliación queda en el historial; los registros del comportamiento anterior se conservan sin migrar.
  - El comprador ve la hora vigente al abrir, volver o refrescar su pedido; una hora vencida no se presenta como pedido listo. No hay push nuevo ni actualización permanente en tiempo real (el seguimiento avanzado es HU23).
  - **Suspensión:** se permite ampliar pedidos ya aceptados aunque el negocio o local esté suspendido, coherente con «la suspensión solo bloquea aceptar»; está cubierto por pruebas.
  - El contrato de Chaski Rider no cambia. El evento antiguo `PREPARATION_ESTIMATE_UPDATED` (solo si existe `deliveryId`) se conserva, pero hoy ningún flujo escribe `deliveryId` ni lo consume; su destino se decidirá en HU21/HU22, donde existe la preferencia preliminar de coordinar el recojo al pasar a `READY_FOR_PICKUP`.
- **Efectivo contra entrega simulado:** aceptar conserva `PREPARING` y el pago pendiente. El responsable puede marcar `READY_FOR_PICKUP` sin pago previo; foodApi genera allí un código de cuatro dígitos. El endpoint antiguo de pago anticipado rechaza nuevos intentos, aunque los pedidos ya pagados siguen siendo compatibles.
- **Flujo temporal Food:** el responsable registra «Pedido recogido» (`IN_DELIVERY`) y simula la llegada (`AT_CUSTOMER`) sin crear asignación ni evento de Rider. Solo el comprador dueño del pedido confirma recepción y efectivo en una transacción que registra `PAID`, `COMPLETED` y resultado `SUCCESS`; no se efectúa un cobro real. Las acciones son idempotentes y requieren permisos vigentes.
- **Dependencia Rider:** `riderApi` conserva por ahora su contrato anterior de pago anticipado, por lo que no ofrece los nuevos pedidos con pago pendiente. Antes de usar Rider real deberá acordarse el contrato de efectivo contra entrega y su confirmación de pago.
- **Límite entre apps:** Food no crea usuarios repartidores. `riderApi` es una función separada, privada mediante IAM y destinada exclusivamente al backend Rider. Expone el pedido y código; recibe asignación, liberación, fases y tracking. Rider sigue siendo la única autoridad que selecciona al repartidor.
- **Reasignación HU22:** `ASSIGNMENT_RELEASED` solo libera antes del recojo; no cancela la compra ni modifica el código. Una nueva asignación incrementa su versión. Después de `PICKED_UP` no se reasigna automáticamente y Food pasa a `IN_DELIVERY` únicamente por un evento Rider válido.
- **Para recibir pedidos** basta al menos un responsable asignado válido; no se exige que esté conectado. Sin responsables se bloquean confirmaciones nuevas (HU16-05, HU16-12).

> **Actualización 15-11-2026:** los puntos «Dependencia Rider», el destino de `PREPARATION_ESTIMATE_UPDATED` en HU20 y el «Flujo temporal Food» quedan condicionados por la sección siguiente. Su texto se conserva como historial.

## Integración con Chaski Rider (HU21-B/HU22, 15-11-2026)

**Origen:** conformidad técnica de Food (Dante, responsable) y de Chaski Rider (Ángel, responsable del proyecto Rider), tras dos rondas de coordinación (14 y 15-11-2026). El contrato técnico está en [API_CHASKI_RIDER](API_CHASKI_RIDER.md). **No modifica por sí solo la planificación académica:** los criterios revisados de HU21/HU22 requieren aprobación formal (ver `PLAN_HU15_HU22_RIDER.md`, local). Nada de esta sección está implementado salvo que se indique.

### Integración logística
- **Modelo pull desde `READY_FOR_PICKUP`.** Rider descubre los pedidos consultando `riderApi` y administra las ofertas, asignaciones y liberaciones. *Motivo:* reutiliza la API existente y respeta que la asignación es responsabilidad de Rider.
- **Identificadores:** `orderId` para el pedido comercial y `assignment.id/version` para la asignación. Sin `deliveryRequestId` ni `deliveryId` en el contrato inicial. *Motivo:* el modelo pull no necesita una solicitud previa, y ambos proyectos ya identifican cada recorrido sin ellos.
- **Proyectos independientes:** Firebase y Firestore de Food y Rider separados; comunicación solo de backend a backend por REST con OIDC; versión `/integration/v1` con activación coordinada para pedidos de prueba.
- **Descubrimiento:**
  - el backend Rider consulta cada 15 s (valor inicial ajustable);
  - paginación por `readyAt` y `orderId` con `nextCursor`;
  - cada pasada recorre todas las páginas desde el inicio;
  - deduplicación por `orderId`.
- **Pedido sin repartidor:** sigue `READY_FOR_PICKUP`. Sin cancelación automática mientras no se acuerde quién cancela, con qué plazo y cómo lo detecta Rider.

### Validación del recojo (HU21-B)
- **Ciclo del código:**
  - Food genera `pickupCode` al marcar `READY_FOR_PICKUP`;
  - Rider lo muestra **solo** al repartidor de la asignación vigente;
  - el responsable autorizado lo introduce en Food;
  - Food valida permisos, pedido, código y asignación vigente, y guarda `pickupVerification` ligada a `assignmentId/version`.
- **Recojo físico:** validar el código **no** equivale a recoger. Rider confirma el recojo físico con `PICKED_UP`, que Food acepta solo con una verificación y una asignación vigentes.
- **Reasignación normal:** conserva el código e invalida la verificación anterior.
- **Intentos y bloqueo:** 5 fallos acumulados por pedido y ciclo de verificación. Una reasignación no reinicia el contador ni quita un bloqueo.
- **Desbloqueo:**
  - solo un **administrador autorizado de Food**;
  - genera un código nuevo, invalida el anterior e inicia un ciclo nuevo de 5 intentos.
  - **Excepción acotada a HU34-28 (aprobada como interpretación de diseño, 15-11-2026):** el administrador sigue **sin acceso general a pedidos**. La excepción permite **solo** la recuperación de un recojo bloqueado:
    1. identificar la incidencia con datos mínimos;
    2. comprobar que es recuperable;
    3. ejecutar el desbloqueo;
    4. registrar el motivo, el actor y la operación;
    5. regenerar el PIN.

    **No** habilita aceptar o rechazar pedidos, cambiar estimaciones, administrar locales, gestionar entregas ni consultar la bandeja general.
  - Es compatible con el resto de HU34: el administrador sigue siendo un claim y no una experiencia (HU34-28), la autorización la decide el servidor (HU34-06/14/21) y el claim tampoco concede el dashboard (HU28).
  - **Pendiente para E2:** el canal (Android o herramienta administrativa segura). No hay endpoint, permiso ni pantalla aprobados.
- **Auditoría y exposición:**
  - intentos y desbloqueos idempotentes y auditables;
  - nunca se guardan los códigos introducidos;
  - Rider ve `pickupLock`, pero no la auditoría administrativa;
  - Food muestra al responsable la identidad del repartidor vigente.

- **Implementación E2 aprobada (15-11-2026, LOCAL):**
  - rutas y códigos de `API_CHASKI_RIDER` §8;
  - la verificación también se exige al prepago antiguo;
  - desbloqueo por un endpoint de foodApi con el claim `admin` (pantalla en E3);
  - pedidos bloqueados fuera del listado, con `ASSIGNMENT_ACCEPTED` → `409 PICKUP_LOCKED`;
  - `pickupCode` es `null` en el detalle una vez verificado.

  El PIN sigue visible para el responsable de forma **transitoria** hasta E3/E5.

### Pago contra entrega (HU22)
- **Medio y saldo:**
  - `CASH_ON_DELIVERY` con pago inicial `PENDING`;
  - `amountToCollectMinor` es el saldo exacto, en céntimos de PEN;
  - Rider declara el medio con el que se cobró: `CASH`, `YAPE` o `PLIN`;
  - sin pagos parciales.
- **`collection`:** objeto en `COMPLETED_SUCCESSFULLY` contra entrega; `null` en `COMPLETED_UNSUCCESSFULLY` sin dinero recibido; ausente en los demás eventos.
- **Cobro correcto:** pago confirmado y pedido completado en la misma transacción.
- **Cobro discrepante:** pedido `COMPLETED` con `deliveryOutcome=SUCCESS`, pago `PENDING` e incidencia `OPEN`. Nunca se registra como `PAID` un importe incorrecto. *Motivo:* refleja la entrega física sin dar por bueno un cobro erróneo.
- **Reintentos y consulta:** Rider no vuelve a cobrar tras una respuesta perdida; repetir el evento recupera el resultado original. El detalle permite consultar `collectionStatus` y `collectionReview`.
- **Pendiente de diseño:**
  - la conciliación posterior, que debe conservar trazabilidad como ajuste;
  - una entrega fallida con movimiento de dinero, que es una incidencia y no `collection:null`.

- **E4 implementado en LOCAL (15-11-2026):**
  - `paymentStatus` sigue siendo `PENDING` o `PAID`, sin estados nuevos;
  - el cobro exacto se registra con `paymentCollection` (fuente `RIDER`) y la discrepancia con `collectionReview` `OPEN`;
  - una entrega fallida que declare dinero se **rechaza** (422) y se trata como incidencia con Rider;
  - el panel HU28 excluye de las ventas un pedido entregado con la revisión abierta, sin fallar;
  - la declaración de Rider es evidencia logística, no una confirmación bancaria de Yape o Plin.
- **Conciliación pendiente (sin implementar):** E4 solo abre la revisión, y no existe un procedimiento autorizado para cerrarla. **No se permite corregir Firestore a mano.** Antes de operar con cobros reales hay que decidir:
  - **quién la resuelve:** se propone un administrador de Food, como excepción acotada similar a la del recojo;
  - **con qué evidencia:** declaración del repartidor, comprobante de Yape o Plin, confirmación del cliente o del local;
  - **qué auditoría se conserva:** actor, motivo, resultado (cobro confirmado por un importe o incobrable) y fecha, sin reescribir el evento ni el historial;
  - **qué hace falta implementar:** el endpoint y la pantalla correspondientes (con aprobación visual en Pencil).

  Hasta entonces, el ciclo de conciliación **no está completo**.

### Secuencias y resiliencia
- **Secuencia:** `eventSequence` es por pedido, no por repartidor; continúa tras liberaciones y reasignaciones; un evento rechazado sin aplicarse no consume su secuencia.
- **Responsabilidades de Rider:**
  - el backend coordina los eventos con una cola de salida **duradera**;
  - ante un resultado desconocido conserva `eventId`, contenido y secuencia;
  - Android usa identificadores estables hacia su backend;
  - impide asignaciones simultáneas incompatibles.
- **Responsabilidad de Food:** el tracking y los eventos se aceptan solo para la asignación vigente.
- **Pruebas obligatorias:** desconexiones, pérdida del ACK, reintentos y duplicados.

### Simulación y pago anticipado
- **Sustitución de la simulación:** la simulación temporal de reparto se sustituirá por el flujo real con Rider. Una vez activada la integración, el **backend** impedirá las transiciones simuladas de los pedidos gestionados por Rider. Durante el desarrollo se conservan mecanismos de prueba aislados en LOCAL. La retirada del código de simulación se evaluará después de aprobar las pruebas reales, sin afectar otros componentes.
- **Pago anticipado:** gestionado por Food, queda como **fase futura opcional**, condicionada al tiempo disponible. El `deliveryCode` del comprador es distinto del `pickupCode` del establecimiento.

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
- **Privacidad:** pedido ajeno o inexistente → mismo 404; el detalle usa los snapshots de HU16 sin coordenadas, datos del comprador, hashes, cotización ni `actorUid`; el historial solo muestra eventos reales de `status_history`. En pedidos `REJECTED` el detalle añade solo el motivo y la explicación de HU19.
- **Lista:** ID visible de 8 caracteres (consultas y navegación usan los 64); hasta dos productos con cantidad y «+N más» desde el snapshot; pago descrito de forma neutral («Efectivo contra entrega»).
- **Checkout:** el carrito muestra «Continuar»; la pantalla siguiente se titula «Confirmar pedido» y, tras el éxito (directo o recuperado), «Pedido enviado» con «Recibimos tu pedido» y «Ver mi pedido» (D4). Tras la compra, Atrás lleva a Pedidos y no vuelve a la revisión. Desde Cuenta → «Recuperar compra pendiente» el título es «Recuperar compra».

## Dashboard comercial (HU28, 08-11-2026)
- **Aprobado por el usuario:** tres indicadores/gráficos; MPAndroidChart 4.0.1. Ventas significa subtotal de productos entregados con éxito, nunca tarifa de reparto ni propina.
- **Período:** 7, 30 o 90 días calendario de Lima, incluido el actual; pedidos y tasa de aceptación por fecha de creación, ventas por fecha de entrega exitosa. Pendientes y cancelados no entran al denominador de aceptación.
- **Permisos:** propietario APPROVED o SUSPENDED consulta únicamente su negocio; responsable VERIFIED consulta únicamente locales asignados. El claim `admin` no concede dashboard. El filtro por local se valida en `foodApi`.
- **Precisión:** se rechazan datos inconsistentes y ventanas que superen 3000 pedidos en cualquiera de las dos consultas, en vez de mostrar agregados parciales. Una venta exige entrega exitosa y `paymentStatus=PAID`, incluso en el flujo Food simulado. El gráfico describe ventas registradas de productos, no cobros bancarios.

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
