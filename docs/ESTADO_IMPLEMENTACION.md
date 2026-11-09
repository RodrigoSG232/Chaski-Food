# Estado de implementación de Chasqui Food

**Actualización Cloud — 08-11-2026:** se publicó exclusivamente `foodApi` del commit `f989166` en `chaski-food-9adba`, región `southamerica-west1`, con autorización del usuario. Firebase informó `Successful update operation` y `Deploy complete`. La ruta `GET /api/v1/me/orders/{orderId}/tracking` pasó de `404 NOT_FOUND` a `401 AUTHENTICATION_REQUIRED` en una consulta anónima de comprobación: existe y exige sesión. Falta la prueba autenticada del pedido en el celular. `riderApi`, reglas e índices no se desplegaron en esta operación. Antes de publicar se aprobaron nuevamente 150 unitarias y 31 pruebas de integración LOCAL con Node 22.23.4; queda resuelta la limitación de runtime indicada en la evidencia anterior. Registro local: `functions/.artifacts/hu23-foodapi-deploy.log`.

Última revisión de HU24: **08-11-2026**. Rama de trabajo local: `feat/hu24-notificaciones`, basada en HU23. HU24 no tiene commit, push ni despliegue. Cómo funciona: [ARCHITECTURE](ARCHITECTURE.md). Cómo trabajar: [GUIA_DESARROLLO](GUIA_DESARROLLO.md).

Vocabulario: **implementado** = código integrado; **verificado** = hay evidencia de ejecución; **validado en Cloud** = probado con el APK CONECTADO contra `chaski-food-9adba`. Una pantalla o un commit no cierran una historia por sí solos.

## 1. Estado por historia

| Historias | Estado | Notas |
|---|---|---|
| EN01–EN02 base y Design System | Implementado | Feature-first, MVVM, Hilt, Compose/Material 3, `ChaskiTheme`. |
| HU01–HU03 identidad | Implementado; verificado en Cloud en los recorridos de HU34/HU16 | Correo, Google, recuperación, vinculación de teléfono a sesión existente, perfil `users/{uid}`, cierre de sesión central con marca durable. |
| HU04 direcciones | Implementado y verificado | Copia por valor en el pedido ya implementada por HU16. |
| HU05–HU11 negocios, locales, responsables, horarios, catálogo | Implementado | Desde HU34 registro, aprobación y asignación pasan por foodApi. HU07 limita la suspensión general a 8 locales activos. |
| HU12–HU13 descubrimiento y búsqueda | Implementado | Conviven componentes de demostración (`FakeDiscoverRepository`). |
| HU14 carrito | Implementado, probado y aceptado | Room por UID; sin reserva de precio/stock. Ajuste aprobado en HU17: «Agregar al carrito» vuelve al catálogo del local con aviso «Ver carrito»; botón «Continuar». |
| **HU15** revisión de compra | **Validado en Cloud; cambio local pendiente de publicar** | Elegibilidad y cotización vial HeiGIT. En el c?digo integrado: tarifa `lima-driving-car-v2` a S/0.90 por km vial, pedido mínimo S/25 y propina opcional separada. |
| **HU16** confirmar pedido | **Validado en Cloud (31-10)** | Pedido idempotente, outbox, `newOrderNotifier` con FCM real y bandeja del responsable. Pedido de prueba `PENDING_CONFIRMATION`, evento `SENT`. |
| **HU34** acceso según permisos | **Validado en Cloud** | Coordinador comercial en foodApi, índice privado, reglas publicadas, vinculación VERIFIED, cliente implícito, lease renovable y paneles con cierre de sesión. Comprador, propietario, responsable y administrador comprobados. |
| **HU17** pedidos propios | **Completada y validada en Cloud (31-10)** | Lista En curso/Historial, detalle histórico, «Mis compras» en Cuenta (propietario/responsable) y «Ver mi pedido» en Checkout. Índice y `foodApi` (`foodapi-00003-vab`) publicados. |
| **HU18** cancelar pedido | **Implementada; interfaz validada en dispositivo (01-11)** | Botón, confirmación y bloqueo desde `PREPARING` comprobados manualmente. Backend transaccional pendiente de emulador/publicación Cloud. |
| **HU19** recibir y responder | **C?digo integrado en main** | Backend y Android implementan aceptar con estimación y rechazar con motivo; suspensión permite consultar/rechazar y bloquea aceptar. JVM `orders` aprobadas. Faltan notificación al cliente, emulador y validación manual/Cloud. |
| **HU20** gestionar tiempo de preparación | **C?digo integrado en main; Cloud pendiente** | Bandeja `PREPARING`, actualización transaccional de 5–180 min, historial, detalle del cliente y outbox para una entrega existente. JVM `orders` aprobadas y pruebas instrumentadas compilan; faltan ejecutar emulador, validación manual y Cloud. |
| **HU21** finalizar preparación y validar recojo | **C?digo integrado en main; Cloud pendiente** | Cliente simula el pago en `PREPARING`; foodApi genera una sola vez el código de 4 dígitos. El responsable puede pasar a `READY_FOR_PICKUP` únicamente después del pago. Falta validación manual y volver a publicar foodApi con estos cambios. |
| **HU22** solicitar servicio de reparto | `riderApi` desplegada; conexión real pendiente | `riderApi` privada expone pedidos listos, código estable, asignación/liberación, fases y tracking con idempotencia. Servicio ACTIVE; cuenta Rider con `run.invoker` únicamente sobre `riderapi` y acceso anónimo denegado (08-11). Falta invocación autenticada desde Rider y validar el intercambio con un pedido listo. Contrato: [API_CHASKI_RIDER.md](API_CHASKI_RIDER.md). |
| **HU23** seguimiento de la entrega | Implementada en rama; publicación parcial | Seguimiento del comprador con mapa tras recojo y ETA opcional. `foodApi` se publicó con autorización; falta el contrato final de ETA de Rider, prueba con Rider real y validación visual en dispositivo. |
| **HU24** notificaciones del pedido | Implementada localmente; validación pendiente | Conserva aviso `NEW_ORDER` al responsable. Outbox transaccional para aceptación, rechazo, listo, Rider asignado, recojo y entrega exitosa/no exitosa; FCM mínimo dirigido al comprador. Android abre el detalle y solicita el estado vigente a foodApi. Falta despliegue y prueba real de FCM en dispositivo. |
| HU25–HU33, EN04 | No iniciadas | Release sin configurar. |

HU34 no figura en la planificación original del curso: es una historia acordada por el equipo (sus decisiones vigentes están en [DECISIONES](DECISIONES.md)).

## 2. Estado de Firebase Cloud (31-10-2026)
- **Functions:** `foodApi` (revisión `foodapi-00003-vab`, 31-10, con las rutas HU17; punto de retorno `foodapi-00002-naj`) y `newOrderNotifier` ACTIVE en `southamerica-west1`, trigger Eventarc sobre `order_events/{eventId}`, secreto `HEIGIT_API_KEY` habilitado. **`checkoutApi` eliminada.**
- **Reglas:** las de `firestore.rules` (publicadas el 31-10, idénticas al repositorio). **Índices:** los 3 de `firestore.indexes.json` en READY; el de HU17 (`food_orders` `buyerUid ASC, createdAt DESC, __name__ DESC`) se publicó el 31-10 sin alterar los 2 anteriores.
- **Datos comerciales:** limpieza L1/L2 hecha; solo queda **CevicheVictory** como negocio de prueba (aprobado, 2 locales, 1 categoría, 2 productos, 4 precios), con un responsable VERIFIED y un dispositivo registrado. Hay **tres pedidos de prueba** `PENDING_CONFIRMATION` (útiles para HU19): el de HU16 y **dos creados a propósito** al validar Checkout de HU17 el 31-10, cada uno con su evento. `users` y Authentication no se tocaron.
- Publicación (30–31-10): limpieza de datos ficticios (L1/L2), despliegue de funciones (D), registro y aprobación de negocio (E1), asignación y vinculación de responsable (E2), reglas (G) y pedido completo con aviso (F), cada fase con autorización. El detalle por fases queda en el registro histórico local de Dante.

## 3. Evidencia vigente
Verificación HU24 en esta rama (08-11-2026): Functions compila; **150/150** pruebas unitarias y verificación del paquete Cloud aprobadas. Auth/Firestore/Functions Emulator con datos sintéticos: **49/49** pruebas de notificaciones, bandeja, decisiones, listo y Rider aprobadas en dos lotes (22 y 27). Android CONECTADO: APK Debug generado y **618/618** pruebas JVM aprobadas. `git diff --check` sin errores. El transporte real FCM, la pulsación del aviso en un dispositivo y los flujos Rider reales requieren validación posterior al despliegue autorizado. No se desplegó código HU24 ni se hizo commit o push; la concesión IAM de Rider fue una operación Cloud posterior y separada.

Verificación HU23 sobre el código de esta rama (08-11-2026): Functions compila y pasa **150/150 unitarias**; **31/31 pruebas de integración** en Auth/Firestore/Functions Emulator (seguimiento HU23, consultas del comprador y bandeja del local), incluido el CLI de simulación. Paquete Cloud verificado: excluye tests y laboratorio. Android genera APK y APK de pruebas; pruebas JVM aprobadas. La ejecución instrumentada y revisión visual del mapa quedan pendientes: ADB no reportó dispositivo ni AVD conectado. Host de backend Node **24.13.1**; runtime declarado **22**, pendiente de repetir en esa versión antes de publicar. No se consultó ni modificó Cloud.

Se corrigió también el doble de prueba de `StoreOrdersScreenTest`, que carecía de `markReady` de HU21 e impedía compilar androidTest. El detalle Android ahora admite la estimación de preparación durante `READY_FOR_PICKUP`, como ya devuelve foodApi. Los resultados de abajo conservan su alcance y fecha originales, no prueban por sí solos el código HU23.

| Alcance | Resultado |
|---|---|
| JVM Android (código `0685d67`) | 574/574 |
| Functions + reglas en Firebase Emulator | 153/153 (incluye 11 de reglas comerciales) |
| Reglas publicadas (API de prueba de reglas) | 17/17 permitidos/denegados esperados |
| HU17 (código `66e2d6c`, LOCAL) | Functions unitarias 126/126; emulador HU16/HU17 58/58; JVM 590/590+; instrumentadas de Pedidos, Checkout, Carrito y Oferta 31/31; E2E LOCAL HU16+HU17 (respuesta perdida, recuperación, «Ver mi pedido», lista y detalle con datos reales); revisión manual de Dante en AVD aprobada |
| HU17 en Cloud (APK CONECTADO, 31-10) | **Validación manual de Dante:** regresión de la bandeja HU16 y navegación comercial; cliente (En curso, Historial, detalle, productos, importes, actualización); «Mis compras» de propietario y responsable; cambio de sesión, estados vacíos y errores; dos compras reales con «Pedido enviado → Ver mi pedido → Pedidos». **Registros `foodapi-00003-vab`:** 68 peticiones, sin 5xx, `DATA_INCONSISTENT` ni `UNKNOWN_ORDER_STATUS` (los 401 son pruebas sin token). El aislamiento por UID con ID ajeno solo se probó automáticamente en LOCAL; en Cloud se comprobó visualmente al cambiar de cuenta. |
| HU20 local (`ramaMax`, 01-11) | JVM de `feature.orders` aprobadas; `compileDebugAndroidTestKotlin` aprobado. Functions compila con Node 22, 136 pruebas unitarias aprobadas y paquete verificado. Se agregaron casos de emulador para actualización, idempotencia, historial, permisos y outbox; la batería completa aún debe ejecutarse. |
| Laboratorio manual HU20 (`ramaMax`, 01-11) | Sembrador LOCAL seguro probado dos veces contra Auth/Firestore Emulator: 4 identidades, negocio, 2 locales, catálogo, responsable VERIFIED y 3 pedidos; 1/1 prueba de idempotencia aprobada. Nunca apunta al proyecto real y se excluye del paquete Cloud. |
| Instrumentadas Android (anteriores a HU17) | Compilan; las últimas correcciones de acceso/avisos **no** se ejecutaron en dispositivo como suite |
| Cloud manual con APK CONECTADO | E1 (registro/aprobación), E2 (asignación, VERIFIED, dispositivo), G (4 roles con reglas nuevas), F (pedido → evento SENT → push → bandeja) |

## 4. Pendientes y mejoras futuras
Fuente única de pendientes. Cada elemento indica por qué se postergó, dónde abordarlo, si requiere decisión humana y qué precaución tener. Que algo figure aquí **no** lo convierte en requisito: solo lo marcado como aprobado lo es.

### 4.1 Próximas historias (planificación original)
| HU | Título | Notas ya decididas |
|---|---|---|
| HU18 | Cancelar pedido | **Aprobado:** cancelación directa únicamente en `PENDING_CONFIRMATION`; desde `PREPARING` no se permite. Si ya existe `deliveryId`, no se cancela silenciosamente y se reserva la coordinación para HU22. |
| HU19 | Recibir y responder pedidos | **Aprobado:** `PENDING_CONFIRMATION → PREPARING`; estimación 5–180 min. Rechazo con motivo. En suspensión: consulta y rechazo permitidos, aceptación bloqueada. Notificación al cliente aún pendiente. |
| HU20 | Gestionar tiempo de preparación | **Integrado en main:** modificación solo en `PREPARING`, 5–180 min, historial versionado, lectura del cliente al refrescar y evento durable si ya existe reparto. Pendiente de validación LOCAL/Cloud. |
| HU21 | Finalizar preparación y validar recojo | **Integrado en main:** el cliente realiza un pago simulado sin cobro real; foodApi genera un único código de recojo y el responsable autorizado puede marcar listo solamente después del pago. La entrega no cambia a `IN_DELIVERY` desde Food. |
| HU22 | Solicitar servicio de reparto | **Integrado en main:** API privada para lectura del pedido completo, código único, asignación/liberación, estados y última ubicación. Food nunca asigna repartidores. Falta despliegue y prueba contractual con la identidad de servicio Rider. |

### 4.2 Mejoras aprobadas, no implementadas
| Mejora | Por qué se postergó | Dónde | ¿Decisión humana? | Precauciones |
|---|---|---|---|---|
| Retirar responsable desde la app | Detectado en E2; fuera de alcance de la publicación | `feature/business` (gestión de locales) | No (REVOKE ya existe en foodApi) | Usar `POST access/operations` REVOKE con revisión; no borrar documentos; la fila queda `REVOKED`. |
| Reenviar correo de verificación | Detectado en E2: solo se envía al registrarse | `feature/auth` / panel de reconciliación | No | Sin verificar, el BIND se rechaza (403); tras verificar, «Volver a comprobar». |
| Limpiar menú Cuenta del cliente (quitar «Productos por local», «Operación de locales», «Menú y catálogo») | Decidido por Dante «a futuro» | `feature/profile` (AccountScreen) | No | Respetar DEC-HU34-29 (no rediseñar el resto del cliente). |
| Mensaje claro en checkout si la cuenta no tiene nombre | Detectado en F: error genérico | `CheckoutBuyerSnapshotSource` / `FirebaseCheckoutRepository` | No | foodApi también compara el perfil; no inventar un nombre. |
| Avisos más insistentes al responsable | FCM `ttl` 5 min; `NO_RECIPIENTS` es terminal | HU19 o HU24 | **Sí** (sonido, recordatorio, reintento) | La bandeja es la fuente de verdad; no duplicar pedidos. |
| Apelación de suspensión (HU35 propuesta) | Aprobada funcionalmente en DEC-HU34-05; numeración y contratos sin definir | Nueva HU35 | **Sí** (estados, colecciones, endpoints) | No reutilizar el legado borrado de `4ce5618` sin rediseño. |

### 4.3 Decisiones pendientes
| Tema | Contexto | Dónde |
|---|---|---|
| Bloque `store_notices` en `firestore.rules` | Sin datos ni código desde L2; retirarlo exige republicar reglas, o reutilizarlo en HU35 | Próximo cambio de reglas / HU35 |
| Replay de eventos `EXHAUSTED`/`NO_RECIPIENTS` | No hay procedimiento aprobado para reenviar avisos fuera de la ventana de reintentos | HU24 / operación |
| Pagos digitales | Solo `CASH_ON_DELIVERY` | Fuera de HU15–HU16 |
| Protección contra borrado de Firestore y privilegios de la cuenta de servicio de Compute (rol Editor, excesivo) | Detectado en la inspección Cloud; no se cambió | Decisión de infraestructura |
| Publicación (EN04) | Release usa `unconfigured.invalid`; APK actual es Debug firmado por cada PC | EN04 |

### 4.4 Deuda técnica y problemas conocidos
- **Reglas y tokens anteriores:** las reglas no consultan Auth en vivo; un token emitido antes de un cambio de correo/verificación puede coincidir con una vinculación no reconciliada. foodApi sí verifica identidad vigente (HU34 §16–17).
- **APK antiguo:** con las reglas publicadas pierde registro de negocio, gestión de responsables y operación de responsables sin VERIFIED. Todo el equipo debe usar el APK nuevo.
- **Datos LOCAL de revisión:** existe un sembrador ficticio de HU17 fuera de Git (`.artifacts/b2-lab/functions/scripts/hu17-review-seed.cjs`: cuentas, local, productos y 29 pedidos). No cubre todo el resto de la app.
- **Firebase en Debug:** el `app/google-services.json` personal es CONECTADO; para LOCAL se copia temporalmente la plantilla a `app/src/debug/google-services.json` (prioritaria) y se elimina después.
- **Instrumentadas** de las correcciones de acceso/avisos solo compiladas.
- **`docs/HU04_PLAN.md` (local):** una prueba del laboratorio HU04 lee su bloque de reglas; no retirarlo ni alterarlo hasta desacoplar esa prueba.
- **Imágenes huérfanas** de Storage no se limpian automáticamente; las imágenes Base64 antiguas siguen soportadas.
- **Pantallas de demostración** (seguimiento, pago con tarjeta, valoración, `FakeDiscoverRepository`) no representan funcionalidad real.
- El parámetro Gradle `chaskiCheckoutApiBaseUrl` conserva un nombre histórico; apunta a `foodApi`.
- HU07: suspensión general limitada a 8 locales activos por transacción.

### 4.5 Ideas propuestas que no se aprobaron
- Optimizar las consultas de hechos de acceso / métricas (P2 de HU34): solo si se mide un problema.
- Caché global de permisos: **rechazada** (seguridad).
- Frame de cliente «lakue» de Pencil: **no aprobado para implementación** (DEC-HU34-29).
- Etiquetas de navegación de DEC-HU34-26 para el cliente: sustituidas por DEC-HU34-29.

## 5. Documentos y fuentes
- Versionados: `README.md`, `docs/ARCHITECTURE.md`, `docs/GUIA_DESARROLLO.md`, `docs/DECISIONES.md` y este documento.
- Compartidos internamente (no versionados): `AGENTS.md` y `CLAUDE.md`, instrucciones para asistentes de IA.
- Locales, fuera de Git: `contexto/` (caso y planificación originales del curso, fuente de los criterios de aceptación), `docs/HU04_PLAN.md` y los registros históricos completos de HU16 y HU34.
