# PLAN DE PROYECTO — CHASKI FOOD

## 1. Visión y alcance
Plataforma de delivery gastronómico: clientes descubren locales y productos de negocios, realizan pedidos,
los negocios gestionan su oferta y preparación, y la distribución se ejecuta mediante la integración con
**Chaski Rider** (sistema desarrollado por otro equipo).

**Responsabilidades del equipo:**
| Sistema | Rol | Propietario |
|---|---|---|
| **Chaski Food App** (Android) | Clientes, negocios, locales, pedidos, dashboard | Nosotros |
| **Chaski Food API** (backend REST) | Lógica de negocio y proceso de pedidos | Nosotros |
| **Chaski Rider** | Gestión y ejecución del reparto | Otro equipo — integración por contrato |

**Integraciones externas:** Firebase (Auth, Storage, Cloud Messaging, Firestore para chat), Google Maps/GPS,
cámara, micrófono y servicios externos de IA (recomendaciones, LLM, predicción de preparación).

---

## 2. Identidad y contexto
- **Nombre**: Chaski Food
- **Android**: aplicación móvil de delivery (descubrir locales/productos, pedidos, preparación, seguimiento).
- **Origen**: Caso de negocio UNMSM — Ingeniería de Sistemas (ver `contexto/`).
- **Planificación oficial**: 6 sprints (Semana 2 a 14), Épicas EP01–EP14, ~33 HUs + enablers (ver CSV de planificación).

---

## 3. Stack tecnológico

### Chaski Food App (Android)
- Kotlin 2.2 + Jetpack Compose + **Material 3** · `minSdk 24` / `target/compile 37`
- **MVVM + Clean Architecture**: capas `presentation / domain / data`
- **Hilt** (DI) · **Retrofit + OkHttp + kotlinx.serialization** · **Room** (SQLite)
- **Navigation Compose** · **Coroutines + Flow** · **WorkManager** · **DataStore**
- **Coil** (imágenes) · **FusedLocationProvider** (GPS) · **CameraX / GetContent** · **SpeechRecognizer**
- Firebase: **Auth, Storage, Cloud Messaging, Firestore**
- TESTS: JUnit + Kotlin Turbine + MockWebServer; Compose UI tests

### Chaski Food API (backend)
- Node.js + TypeScript + **Express** + **Prisma** + **PostgreSQL**
- Autenticación: verificación de ID token de Firebase (middleware)
- API REST documentada (OpenAPI) · manejo de errores tipificado · validación de esquemas
- Pruebas: **Jest + Supertest**

### Chaski Rider (externo)
- Consumimos su **API de entregas** y procesamos sus **eventos logísticos** por contrato acordado.
- No desarrollamos el sistema ni un simulador; hasta su entrega usamos fixtures del contrato.

---

## 4. Arquitectura Android
```
com.chaskifood.app
├── ChaskiApplication.kt            // @HiltAndroidApp
├── MainActivity.kt                 // NavHost raíz
├── core/
│   ├── ui/                         // ChaskiTheme, paleta, tipografía, componentes y estados Loading/Error/Content (EN02)
│   ├── navigation/                 // Rutas, guard de sesión
│   ├── network/                    // Retrofit, interceptores, autenticación
│   ├── database/                   // Room: entidades, DAOs, fecha de refresco
│   ├── datastore/                  // Sesión persistente
│   └── common/                     // Result/State, validadores, helpers de coroutines
└── feature/<feature>/
    ├── presentation/               // Screen + ViewModel + UiState/UiAction
    ├── domain/                     // modelos, casos de uso, interfaces de repositorio
    └── data/                       // implementaciones de repos (remoto + local)
```
Módulo único con packages limpios. Funcionalidad incremental por sprint (vertical por feature).

## 5. Arquitectura Backend (API)
```
chaski-food-api/ (repo propio)
├── src/
│   ├── core/                       // config, db, auth middleware, errores, eventos
│   ├── modules/
│   │   ├── usuarios/               // auth, perfil, direcciones
│   │   ├── negocios/               // onboarding, habilitación, locales, responsables
│   │   ├── catalogo/               // categorías, productos, config por local
│   │   ├── pedidos/                // carrito/checkout, pedidos, estados, cancelación/incidencias
│   │   ├── logistica/              // solicitud y eventos con Chaski Rider
│   │   ├── comunicacion/           // notificaciones (FCM) y chat contextual (Firestore)
│   │   └── analitica/              // dashboard comercial
│   └── shared/
├── tests/                          // Jest + Supertest por módulo
└── openapi.yaml                    // contrato de la API
```

---

## 6. Modelo de dominio

### Roles
`CLIENTE` · `ADMIN_NEGOCIO` · `RESPONSABLE_LOCAL` · `ADMIN_CHASKI` · `REPARTIDOR` (Rider, externo)

### Entidades principales
Usuario · Negocio · Local · Categoría · Producto · ProductoConfigLocal (precio/disponibilidad por local)
Dirección · Carrito · Pedido · OrderItem (snapshot histórico) · DeliveryRequest · DeliveryEvent
Incidencia · Valoración · Conversación (chat) · Notificación · Indicador

### Estados del pedido
```
PENDING_CONFIRMATION ──acepta──► PREPARING ──listo──► READY_FOR_PICKUP
        │                              │(actualiza est. tiempo)
        │ cancelación directa          ▼
        │ (o evaluación en PREPARING)  PICKED_UP (evento Rider) ─► IN_DELIVERY ─► COMPLETED
        ▼
     CANCELADO
```
- Tras `PICKED_UP`: una cancelación ordinaria se deriva al **flujo de incidencias**.
- El cliente puede consultar activos/históricos; cada estado queda registrado (historial).

### Estados de negocio / local
- Solicitud: `PENDING_REVIEW → APPROVED | OBSERVED | REJECTED` (cada decisión registra responsable y fecha/hora).
- Local administrativo: `ACTIVE | SUSPENDED` (Admin Chaski) — no elimina historial.
- Local operativo: `OPEN | CLOSED | PAUSED` + horarios por día; `PAUSED`/fuera de horario no recibe nuevos pedidos;
  los pedidos ya aceptados se conservan salvo incidencia.
- Producto por local: `OUT_OF_STOCK` no se agrega a una nueva compra.

---

## 7. Contrato de integración con Chaski Rider (por acordar)
> Dependencia externa crítica. Se fija el contrato con el equipo de Rider antes de HU22/HU23; se versiona
> y se desarrolla contra fixtures del mismo hasta su entrega.

| Item | Definición acordada |
|---|---|
| **API de solicitud** | Food → Rider: `orderId, localOrigen, destino, estimatedReadyAt, tarifaLogistica` → responde `deliveryId` |
| **Idempotencia** | Reintentar el mismo `deliveryRequestId` no crea entregas duplicadas |
| **Eventos** | Rider → Food: `ASSIGNED, ARRIVED_AT_LOCAL, PICKED_UP, IN_TRANSIT, LOCATION, ETA, DELIVERED` (+ incidencias) |
| **Mecanismo** | Webhooks / cola / Firestore escribe-&-lee (a definir con el equipo) |
| **Actualizaciones** | Cambios de `estimatedReadyAt` se comunican a Rider según contrato (HU20/HU33) |
| **Validaciones** | `PICKED_UP` válido → Food pasa a `IN_DELIVERY`; `DELIVERED` válido → `COMPLETED` |
| **Chat** | Conversación por pedido/entrega en Firestore compartido (cliente ↔ Rider). `Food` asegura acceso solo a autorizados |

---

## 8. Roadmap por Sprint

### Sprint 1 — Fundación, identidad y acceso (SEM 2–3)
**HUs/Enablers:** EN01, EN02, HU01, HU02, HU03, HU04
- Renombrado a `com.chaskifood.app`; Hilt; Retrofit + Room; Nav Compose; patrón Loading/Error/Content.
- ChaskiTheme (Material 3) + design system de componentes.
- Registro, login + recuperación, perfil y direcciones (con GPS).
- Backend: módulo de usuarios (auth Firebase, perfil, direcciones).
- **CE**: CE01, CE03, CE04, CE05, CE06, CE08, CE10

### Sprint 2 — Negocios, locales y catálogo (SEM 4–5)
**HUs/Enablers:** HU05–HU11
- Registro + solicitud de negocio; revisión/administración por Admin Chaski (aprobar/observar/rechazar/suspender).
- Gestión de locales, responsables y horarios/disponibilidad operativa.
- Catálogo: categorías, productos (cámara + Storage), precio/disponibilidad por local.
- Backend: módulos de negocios, locales y catálogo con control por rol.
- **CE**: CE02 (PN01/PN02), CE03, CE10

### Sprint 3 — Descubrimiento, carrito y compra (SEM 6–7)
**HUs/Enablers:** HU12–HU17
- Descubrir locales por dirección seleccionada (cobertura, horario, operativo); búsqueda/exploración.
- Carrito mono-local, checkout con revalidación, confirmación de pedido (recalcula backend, snapshots históricos).
- Consulta de pedidos activos e históricos.
- Backend: carrito/checkout, pedidos con OrderItem y dirección snapshot.
- **CE**: CE02 (PN03), CE06, CE10

### Sprint 4 — Atención, preparación y logística inicial (SEM 8–9)
**HUs/Enablers:** HU18–HU22
- Cancelación según estados (y reglas de evaluación).
- Recibir/aceptar/rechazar pedidos (motivos); tiempo de preparación y `estimatedReadyAt`; finalizar y validar recojo.
- Solicitud de reparto a Rider (idempotente). Contrato de integración en acción.
- Backend: máquina de estados del pedido + módulo logística.
- **CE**: CE02 (PN04/PN05, PN06)

### Sprint 5 — Seguimiento, comunicación y analítica (SEM 10–11)
**HUs/Enablers:** HU23–HU26, HU28
- Tracking: eventos Rider, ETA, ubicación; `DELIVERED` completa el pedido.
- Notificaciones FCM (cliente y local); chat contextual con Rider (Firestore).
- Incidencias (evidencias en Storage) y escalado al Admin Chaski.
- Dashboard comercial (pedidos, ventas, ticket promedio, top productos, tiempos, motivos de rechazo).
- Backend: comunicación, notificaciones, incidencias y analítica.
- **CE**: CE02 (PN05/PN06/PN07), CE03, CE09

### Sprint 6 — Postventa, IA, offline y publicación (SEM 12–14)
**HUs/Enablers:** HU27, HU29, HU30, HU31, HU32, HU33, EN04
- Valoraciones (establecimiento en Food; reparto a Rider).
- IA: búsqueda/recomendaciones + voz, asistente Chaski (LLM), predicción de tiempo de preparación
  (con **fallback** manual si el servicio no está disponible).
- Offline: carrito persistente en Room; sincronización con WorkManager (idempotente).
- Publicación: release firmado, AAB y track de pruebas en Google Play.
- **CE**: CE02 (PN06), CE06, CE07, CE08, CE10, CE11, CE12

---

## 9. Criterios de evaluación cubiertos (CE01–CE12)
CE01 Autenticación · CE02 PN01–PN07 · CE03 Firebase (Auth/Storage/FCM/Firestore) · CE04 Material 3/paleta ·
CE05 MVVM + Clean Code · CE06 Corrutinas + Retrofit · CE07 WorkManager · CE08 Room ·
CE09 Dashboard · CE10 GPS/cámara/mic · CE11 IA (3 servicios) · CE12 Google Play

---

## 10. Testing y calidad
- **App**: unit de ViewModel/casos de uso (coroutines-test + Turbine), repos con MockWebServer, DAOs Room in-memory;
  Compose UI tests en flujos críticos (login, carrito→checkout, aceptar pedido).
- **Backend**: Jest + Supertest por módulo; validación del contrato OpenAPI; pruebas de idempotencia (HU22, HU31).
- **Integración Rider**: suite de escenarios con fixtures del contrato (asignado, recojo, entrega, incidencia).
- **Puerta de calidad por sprint**: `./gradlew build test assembleDebug` y ejecutar test suite del backend.

---

## 11. Riesgos y supuestos
- **Rider (externo)**: se acordará contrato temprano; desarrollo contra fixtures del contrato; sin bloqueo de app/backend.
- **IA (CE11)**: requiere servicios externos con credenciales; si no hay keys → fallback al flujo manual (definido en las HUs).
- **Firebase**: requiere creación de proyecto y `google-services.json`; credenciales vía secretos (nunca en el repo).
- **Backend hosting**: deploy local/cloud gratuito; env con config.
- **Identidad**: `applicationId = com.chaskifood.app`, nombre "Chaski Food".