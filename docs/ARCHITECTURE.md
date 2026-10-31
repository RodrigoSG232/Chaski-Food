# Arquitectura y entornos de Chasqui Food

Referencia: rama `ramaDante` / `main` al 31-10-2026 (HU15, HU16 y HU34 integradas). Describe cómo funciona el sistema **según el código**; el estado de cada historia y los pendientes están en [ESTADO_IMPLEMENTACION](ESTADO_IMPLEMENTACION.md) y la forma de trabajar en [GUIA_DESARROLLO](GUIA_DESARROLLO.md). Si este documento y el código difieren, prevalece el código: corrige el documento.

## 1. Visión general

Chasqui Food es una app Android de delivery: los clientes descubren locales y productos, arman un carrito y confirman pedidos con pago contra entrega; los propietarios gestionan negocios, locales y catálogo; los responsables de local reciben los pedidos; el administrador de Chasqui aprueba y supervisa negocios.

```
Android (Kotlin/Compose)  ──Firebase Auth──►  identidad (ID token)
        │  lecturas/escrituras permitidas por reglas
        ├──────────────►  Cloud Firestore  ◄── Admin SDK ── foodApi (Cloud Functions v2, TypeScript)
        │                                              │        └── HeiGIT (rutas viales)
        └── HTTPS + ID token ─────────────────────────►│
                                                       └─ order_events ──► newOrderNotifier ──► FCM ──► responsable
```

- Proyecto Firebase real: **`chaski-food-9adba`**, región **`southamerica-west1`** (Functions y Firestore).
- Proyecto de laboratorio: **`demo-chaski-hu15`** (solo Firebase Emulator; nunca datos reales).

## 2. Android

### Tecnología
Kotlin, Jetpack Compose + Material 3, un único módulo Gradle `app` (paquete `com.chaskifood.app`), `minSdk 24`, `targetSdk 37`, Java 11. Hilt (DI), corrutinas + `Flow`/`StateFlow`, Navigation Compose, Retrofit + OkHttp + kotlinx.serialization, Room 2.7 (base `chaski_food.db`), DataStore, Firebase (Auth, Firestore, Storage, Messaging), Coil (imágenes), MapLibre (mapa de direcciones). **No se usa WorkManager.**

### Organización
Feature-first con capas por feature en `app/src/main/java/com/chaskifood/app/`:

| Capa | Responsabilidad |
|---|---|
| `presentation` | Compose (estado + eventos) y ViewModel (coordina operaciones, carga/errores, mensajes). Sin acceso directo a Firebase/Room/HTTP. |
| `domain` | Modelos, contratos de repositorio y reglas puras. Sin SDKs. |
| `data` | Implementaciones: Firebase, Retrofit, Room, DataStore, mappers y módulo Hilt de la feature. |

Dirección: Presentation → Domain ← Data. `core/` contiene infraestructura compartida; tema y componentes en `ui/theme` y `ui/components`.

| Paquete | Contenido |
|---|---|
| `core/access` | **HU34**: identidad compartida (`SharedAccessIdentitySource`, `FirebaseAccessIdentityDriver`), hechos de acceso desde foodApi (`RemoteAccessFacts`), contexto con lease de 60 s (`SharedAccessContext`), resolución pura de permisos (`domain/ResolveAccess`) y puertos `PendingAssignmentBinder`/`SessionSignOut`. |
| `core/navigation` | `ChaskiNavHost`, rutas (`ChaskiTab.kt`), guardas por permiso (`AccessNavigationGuard`, `AccessNavigationPolicy`), eventos de ciclo de vida/red (`AccessNavigationEvents`) y paneles restrictivos (`AuthorizationPanel`). |
| `core/notifications` | **HU16** FCM: `ChaskiMessagingService`, `PushTokenSource`, `DeviceRegistrar` (registro en foodApi), `NotificationCoordinator`, `NoticePolicy`. |
| `core/database`, `core/datastore`, `core/network`, `core/firebase` | Room, sesión DataStore, Retrofit/OkHttp, instancias Firebase y `FirebaseAccessControl` (claim admin y comprobaciones cliente). |
| `feature/auth`, `profile`, `splash`, `onboarding` | Acceso (correo, Google, recuperación, vinculación de teléfono), perfil `users/{uid}`, cierre de sesión central con marca durable (`SignOutMarkerStore`). |
| `feature/address` | Direcciones en Firestore, selección local por UID (DataStore), mapa y geocodificación. |
| `feature/business`, `catalog` | Solicitudes de negocio, aprobación/supervisión admin, locales, horarios, responsables, catálogo y precios por local. Operaciones comerciales vía `CommercialOperations` → foodApi. |
| `feature/discover`, `search`, `home`, `restaurants` | Descubrimiento de locales por dirección/horario/cobertura, búsqueda y detalle de ofertas. |
| `feature/cart` | **HU14**: carrito por UID en Room con validación remota de oferta. |
| `feature/checkout` | **HU15/HU16**: revisión de compra (`FirebaseCheckoutRepository`), cotización y elegibilidad (foodApi), confirmación idempotente y recuperación (`CheckoutOrderOperations`, intenciones en Room). |
| `feature/orders` | Bandeja del responsable (`StoreOrdersScreen`, `HttpStoreOrdersRepository`) y compras propias HU17 (`BuyerOrdersScreen` compartida por la pestaña Pedidos y «Mis compras», `BuyerOrderDetailScreen`, `HttpBuyerOrdersRepository`). Las pantallas de seguimiento/valoración siguen siendo **demostrativas**. |
| `feature/menu`, `main` | Contenedor principal y piezas de ejemplo. |

Módulos Hilt (todos en `SingletonComponent`): `AccessDataModule`, `DatabaseModule`, `FirebaseModule`, `NetworkModule`, `NotificationsModule`, `AddressDataModule`, `AuthDataModule`, `BusinessDataModule`, `CartDataModule`, `CatalogDataModule`, `CheckoutDataModule`, `DiscoverDataModule`, `StoreOrdersDataModule`, `SearchDataModule`.

### Room (versión 3, `exportSchema = true`)
Tablas `app_config`, `carts`, `cart_items` (carrito por UID) y `checkout_intents` (intenciones de compra recuperables por `ownerUid` + `intentId`). Migraciones `MIGRATION_1_2` y `MIGRATION_2_3`, esquemas en `app/schemas/`. **Nunca** migraciones destructivas ni borrado de bases como solución.

### Sesión, autorización y navegación (HU34)
- FirebaseAuth es la autoridad de sesión. La identidad compartida publica `AccessIdentityState` con *generación* (cambia en cada login/cuenta) y *revisión* (cambia con token, correo o verificación).
- `SharedAccessContext` pide a foodApi `GET /access/facts` y resuelve la experiencia (`CLIENT`, `OWNER`, `MANAGER`) + capacidades con `ResolveAccess`. El resultado vale **60 s** (lease) y se **renueva en segundo plano a los 50 s**; un fallo de red deja caducar la lease (bloqueo seguro), sin reintentos en bucle.
- Al salir de una pantalla comercial se **revalida en segundo plano** si la lease sigue vigente; abrir la app, volver de segundo plano, reconectar, caducar o pulsar «Volver a comprobar» hacen un refresco bloqueante («Verificando permisos»).
- Las guardas de navegación (`protectedComposable`) muestran paneles restrictivos (reconciliación, conflicto, error, sin conexión) con **«Volver a comprobar» y «Cerrar sesión»**.
- Una invitación de responsable PENDING se vincula automáticamente (BIND) una vez por identidad si el correo está verificado; el backend decide la elegibilidad.
- El administrador no es una experiencia: es el claim `admin` que añade la sección **Cuenta → Administración**.
- Toda autorización cliente es UX; **la autoridad son foodApi y las reglas de Firestore**.

## 3. Backend `foodApi` (Cloud Functions v2)

Código en `functions/` (TypeScript, Node 22, pnpm 11.25.0, `firebase-functions` 7.4, `firebase-admin` 14.5). Codebase **`food-local`** en `firebase.json` (es un nombre de despliegue: el mismo código corre en el emulador y en Cloud).

| Función | Tipo | Configuración |
|---|---|---|
| `foodApi` | HTTPS (`functions/src/index.ts`) | `southamerica-west1`, timeout 15 s, `maxInstances 1`, concurrencia 10, `invoker: public` (cada ruta exige Firebase ID token), secreto `HEIGIT_API_KEY` |
| `newOrderNotifier` | Firestore `document.created` en `order_events/{eventId}` | `retry: true`, timeout 120 s, `maxInstances 1` |

**`checkoutApi` fue eliminada el 31-10-2026** (era un backend antiguo con tarifa fija, código fuera del repo). No debe volver a usarse ni recrearse; todo pasa por `foodApi`.

Estructura: `foodApiHandler.ts` (enrutado HTTP y traducción de errores) → servicios por dominio: `access/` (coordinador HU34), `eligibility/`, `delivery/` (HU15), `orders/` (HU16), `notifications/` (dispositivos, procesador de eventos, FCM), `auth/verifyBuyerToken.ts` (verificación de ID token con revocación) y `runtimeEnvironment.ts` (decide LOCAL/CLOUD y rechaza configuraciones mezcladas).

### Endpoints (prefijo `/api/v1/`, todos con `Authorization: Bearer <Firebase ID token>`)

| Método y ruta | Historia | Función |
|---|---|---|
| `GET stores/{storeId}/purchase-eligibility` | HU15 | Negocio `APPROVED` + local `ACTIVE` → `ELIGIBLE` o motivo. |
| `POST delivery-quotes` `{storeId, addressId}` | HU15 | Cotización vial persistida en `delivery_quotes` (vence a los 5 min). |
| `POST orders` | HU16 | Confirmación autoritativa e idempotente del pedido. |
| `GET order-attempts/{intentId}` | HU16 | Recuperación del resultado de una confirmación incierta. |
| `GET stores/{storeId}/orders` | HU16 | Bandeja paginada del responsable VERIFIED del local. |
| `GET me/orders?phase=ACTIVE\|HISTORICAL` y `GET me/orders/{orderId}` | HU17 | Compras propias por `buyerUid` (cualquier experiencia); cursor ligado a UID y fase; ajeno = 404. Ver [DECISIONES](DECISIONES.md#consultas-propias-hu17). |
| `PUT` / `DELETE devices/{installationId}` | HU16 | Registro/retirada del dispositivo FCM (PUT solo responsables con local asignado). |
| `GET access/facts` | HU34 | Hechos de acceso de la cuenta (propiedad, asignaciones, locales). |
| `GET access/revision`, `POST access/assignment-revision` | HU34 | Revisiones del índice para control de concurrencia. |
| `POST access/operations` | HU34 | Operaciones comerciales: `REGISTER`, `RESUBMIT`, `APPROVE` (admin), `ASSIGN`, `REVOKE`, `BIND`, `PREPARE` (admin, sin UI). Idempotentes por `operationId` y auditadas. |

### HeiGIT y tarifa (HU15)
`delivery/openRouteServiceReader.ts` consulta HeiGIT openrouteservice (`driving-car`) con la clave `HEIGIT_API_KEY` (Secret Manager; nunca en Android). Cobertura: distancia en línea recta ≤ `stores.deliveryRadiusKm`. Tarifa vigente (`deliveryQuotePolicy.ts`, versión `lima-driving-car-v1`): **150 céntimos + redondeo(120 céntimos × km viales)**, aritmética entera. En LOCAL, `CHASKI_ROUTE_FIXTURE=enabled` simula 1 250,5 m sin llamar a HeiGIT.

### Pedidos (HU16)
`orders/orderService.ts` revalida en una transacción: identidad y compra solo como cliente, negocio/local habilitados y `OPEN`, horario, cobertura, cotización vigente del mismo UID, productos/precios/categorías, responsable receptor y consentimiento (huella de lo que el cliente aceptó). Crea a la vez `food_orders/{id}` (estado `PENDING_CONFIRMATION`, `CASH_ON_DELIVERY`, PEN, copias por valor de dirección y líneas), `food_orders/{id}/status_history/initial`, `order_attempts/{uid}/intents/{intentId}` (idempotencia duradera) y `order_events/{id}` (outbox). Android guarda la intención en Room **antes** de llamar y recupera con `order-attempts` si la respuesta se pierde.

### Avisos (HU16)
`newOrderNotifier` procesa cada `order_events` con lease (3 min), hasta 5 intentos con backoff (1, 5, 15, 15, 15 min) y progreso por dispositivo (`order_events/{id}/deliveries`). Reautoriza a cada responsable antes de enviar. FCM: prioridad alta, `ttl` 5 min. Estados finales: `SENT`, `NO_RECIPIENTS` (sin dispositivos; **no se reintenta**), `FAILED_PERMANENT`, `EXHAUSTED`. El aviso no es la fuente de verdad: **la bandeja sí**. Cerrar sesión retira el dispositivo (`DELETE devices`).

## 4. Firestore

### Colecciones y propietario de escritura

| Colección | Contenido | Escribe |
|---|---|---|
| `users/{uid}` (+ `addresses`, `preferences/delivery`, `address_operations`) | Perfil, direcciones y consistencia de la predeterminada | El propio usuario (reglas) |
| `business_requests/{ownerUid}` | Solicitud de negocio (estado `PENDING_REVIEW`/`APPROVED`/`OBSERVED`/`REJECTED`/`SUSPENDED`) | **foodApi** (REGISTER/RESUBMIT/APPROVE); admin cliente solo observar/rechazar/suspender/reactivar auditado |
| `store_managers/{businessId}_{email}` | Asignación de responsable (`PENDING`→`VERIFIED`/`REVOKED`, `boundUid`, locales) | **Solo foodApi** |
| `commercial_access_index/{uid_*|email_*}` | Índice privado UID↔correo con revisión | **Solo foodApi**; ningún cliente lo lee |
| `stores`, `categories`, `products`, `store_product_configs` | Locales, catálogo y precio/disponibilidad por local | Propietario aprobado / responsable VERIFIED según reglas; admin para suspensión |
| `audit_logs` | Auditoría administrativa y comercial | Admin (reglas) y foodApi |
| `delivery_quotes`, `order_attempts`, `food_orders` (+`status_history`), `order_events` (+`deliveries`), `notification_devices` | HU15/HU16 | **Solo foodApi**; cerradas a clientes |
| `store_notices` | Bloque de reglas heredado sin datos ni código que lo use (ver pendientes) | — |

Las imágenes de locales/productos se guardan en Storage bajo `businesses/{businessId}/stores|products/...`; Firestore guarda la URL.

### Reglas e índices
- `firestore.rules` (versionado) = reglas **publicadas** desde el 31-10-2026. Cierran a clientes toda escritura comercial que debe pasar por foodApi, exigen vinculación VERIFIED coherente con el índice para operar como responsable y terminan con una denegación general.
- `firestore.indexes.json`: 3 índices compuestos (`delivery_quotes`; `food_orders` para la bandeja y para compras propias `buyerUid, createdAt desc, __name__ desc`).
- Las pruebas de reglas están en `functions/test/commercialRules.emulator.ts` (y casos Android en `FirestoreRulesClientEmulatorTest`).

## 5. Entornos LOCAL y CONECTADO

El backend local y el desplegado **comparten el mismo código fuente**, pero no necesariamente la misma versión: lo desplegado es lo último que alguien publicó con autorización. **Que el código local cambie no actualiza Cloud.**

| Aspecto | LOCAL | CONECTADO |
|---|---|---|
| Firebase | Emulator Suite, proyecto `demo-chaski-hu15` (Auth `127.0.0.1:9099`, Firestore `:8089`, Functions `:5001`) | Proyecto real `chaski-food-9adba` |
| foodApi | Emulador; Android la llama por `10.0.2.2` (AVD) o `127.0.0.1` con `adb reverse` | `https://foodapi-u65lcqarqa-tl.a.run.app/api/v1/` |
| Rutas/tarifa | `CHASKI_ROUTE_FIXTURE=enabled` (simulada) | HeiGIT real |
| FCM | Fixture (`CHASKI_NOTIFICATIONS_FIXTURE`), tokens ficticios | FCM real |
| Storage | No se inicia en el laboratorio | Real |
| `google-services.json` de Debug | Copia de `app/src/debug/google-services.demo.json` | JSON legítimo del proyecto real (canal interno, nunca en Git) |

Parámetros Gradle (Debug):

| Propiedad | Uso |
|---|---|
| `-PchaskiFirebaseMode=LOCAL\|CONECTADO` | Modo (por defecto `LOCAL`). |
| `-PchaskiEmulatorHost=10.0.2.2\|127.0.0.1` | Host del emulador en LOCAL. |
| `-PchaskiConnectedProjectId=chaski-food-9adba` | Proyecto exigido en CONECTADO. |
| `-PchaskiCheckoutApiBaseUrl=https://foodapi-u65lcqarqa-tl.a.run.app/api/v1/` | URL de **foodApi** en CONECTADO (el nombre de la propiedad es histórico; solo acepta esa URL). |

La tarea `validateDebugFirebaseConfiguration` detiene la compilación si el JSON activo no corresponde al modo (LOCAL ↔ `demo-chaski-hu15`, CONECTADO ↔ `chaski-food-9adba`). Release sigue con URLs `unconfigured.invalid`: **no está listo para distribuir** (EN04).

Comandos completos de cada entorno: [GUIA_DESARROLLO §2](GUIA_DESARROLLO.md#2-entornos-de-trabajo-y-comandos).

## 6. Secretos y configuración privada (sin valores)
- `HEIGIT_API_KEY`: Secret Manager (Cloud) y `functions/.secret.local` con valor **ficticio** en LOCAL.
- `google-services.json` real, `local.properties`, keystores: fuera de Git.
- La cuenta de servicio de Compute ejecuta las funciones; Eventarc/Pub/Sub tienen los roles necesarios para el trigger.
- No publicar tokens, UID, correos ni datos de usuarios en commits, documentos o capturas.
