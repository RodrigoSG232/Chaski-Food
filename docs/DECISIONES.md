# Decisiones vigentes de Chasqui Food

Decisiones **aprobadas** por el equipo que siguen aplicando y condicionan las próximas historias (HU17 en adelante). Cambiarlas requiere una decisión humana explícita. Entre paréntesis, el identificador original (DEC-HU16-xx, DEC-HU34-xx). Cómo están implementadas: [ARCHITECTURE](ARCHITECTURE.md); pendientes: [ESTADO_IMPLEMENTACION §4](ESTADO_IMPLEMENTACION.md#4-pendientes-y-mejoras-futuras).

## Backend y datos
- **Un solo backend: `foodApi`.** Las operaciones sensibles (registro y aprobación de negocios, asignaciones de responsables, pedidos, avisos) pasan por foodApi; no se crean backends paralelos ni se recupera `checkoutApi` (HU16-07, HU34-08, HU34-13).
- **Esquema propio y versionado** de pedidos: `food_orders` (+ `status_history`), `order_attempts/{uid}/intents`, `delivery_quotes`, `order_events`. Pedido, intento, historial inicial y evento se crean en la misma transacción (HU16-08, HU16-16).
- **El índice `commercial_access_index` es derivado y privado**: coordina UID y correo; Auth, `business_requests` y `store_managers` siguen siendo las fuentes. No es una colección de roles (HU34-20).
- **Sin eliminaciones automáticas** al preparar o reconciliar datos; los conflictos se detectan y se resuelven explícitamente (HU34-24).

## Pedidos (HU15–HU19)
- **Cotización**: foodApi la calcula con HeiGIT, la guarda y entrega `quoteId`; vale 5 minutos, pertenece al UID (no a la sesión) y se reutiliza mientras sea válida. Android nunca fija importes (HU16-01, HU16-06, HU16-20).
- **Confirmación autoritativa**: la transacción final revalida precios, permisos, responsables, dirección, cotización y horario; sin llamadas a HeiGIT/FCM dentro de la transacción (HU16-17).
- **Idempotencia**: cada intención tiene un `intentId` duradero por UID; repetirla devuelve el resultado original; el mismo ID con otro contenido se rechaza; una compra nueva usa otro ID (HU16-03, HU16-09).
- **Recuperación**: se distinguen éxito, rechazo final e incertidumbre; un timeout no significa fracaso; la intención se guarda en Room **antes** de llamar y una nueva revisión implica nueva intención, nunca un reenvío automático (HU16-02, HU16-18, HU16-22).
- **Consentimiento**: cualquier cambio relevante (incluidas subidas y bajadas de precio) exige nueva revisión (HU16-10).
- **Pedido histórico por valor**: se guardan comprador, negocio/local, líneas con nombre y precio, importes, pago (`CASH_ON_DELIVERY`, PEN), direcciones de recojo y entrega, instrucciones, contacto, cotización, estado y fechas. Editar los datos originales no cambia pedidos pasados (HU16-15).
- **Estado inicial `PENDING_CONFIRMATION`.** HU19 acepta pasando **directo a `PREPARING`**, sin estado `CONFIRMED` (aunque el PDF del curso lo mencione).
- **Para recibir pedidos** basta al menos un responsable asignado válido; no se exige que esté conectado. Sin responsables se bloquean confirmaciones nuevas (HU16-05, HU16-12).

## Bandeja y avisos
- **La bandeja es la fuente de verdad**; FCM es solo aviso, puede duplicarse o perderse (HU16-04, HU16-13).
- Los pedidos se asocian al **local (`storeId`)**, no a una persona; un nuevo responsable autorizado ve los existentes (HU34-03).
- Avisos con evento persistente `NEW_ORDER`, procesador independiente, reintentos acotados, dispositivos por UID, destinatarios reautorizados en cada envío y payload mínimo; el **propietario no recibe** avisos de pedidos (HU16-04, HU16-21).
- Lecturas por HTTP autorizadas por foodApi; los documentos privados no se abren al cliente (HU16-19).

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
