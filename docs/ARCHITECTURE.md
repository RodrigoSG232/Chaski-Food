# Arquitectura de Chasqui Food

Referencia de esta revisión: código de `da114d7`, 2026-10-11. Este documento describe la aplicación Android y distingue implementación observada, infraestructura preparada y convenciones para trabajo futuro. La existencia de código o pruebas unitarias no acredita por sí sola la aceptación de una historia ni el funcionamiento de los servicios en producción.

## 1. Visión general

Chasqui Food conecta clientes con negocios gastronómicos: permite gestionar identidad y direcciones, administrar negocios/locales y explorar su oferta. El producto previsto incluye compra y pedidos; esos procesos todavía tienen pantallas de demostración. Chaski Rider es un sistema externo encargado del reparto, cuya integración no está implementada aquí.

La aplicación tiene un único módulo Android, `app`, y se organiza **feature-first**: cada funcionalidad agrupa su presentación, dominio y acceso a datos. Las features con flujos de datos desarrollados utilizan MVVM: Compose representa el estado, el ViewModel coordina acciones y los repositorios exponen operaciones mediante contratos. Corrutinas y Flow conectan las operaciones asíncronas con la UI.

Esta separación está implementada con distintos grados de detalle. Address tiene subpaquetes por responsabilidad; otras features mantienen archivos directamente en cada capa y algunas solo tienen presentación. La estructura orienta el desarrollo futuro, pero no implica que todas las funcionalidades estén terminadas o uniformemente refactorizadas. Las capas son paquetes del mismo módulo, no módulos Gradle que impongan sus fronteras.

## 2. Estructura real

```text
app/src/main/
├── AndroidManifest.xml
├── java/com/chaskifood/app/
│   ├── ChaskiApplication.kt
│   ├── MainActivity.kt
│   ├── core/
│   │   ├── common/
│   │   ├── database/
│   │   ├── datastore/
│   │   ├── firebase/
│   │   ├── navigation/
│   │   └── network/
│   ├── feature/
│   └── ui/
│       ├── components/
│       └── theme/
├── keepRules/
└── res/
```

En adelante, las rutas de código son relativas a `app/src/main/java/com/chaskifood/app/`.

`ChaskiApplication.kt` declara `@HiltAndroidApp`. `MainActivity.kt`, con `@AndroidEntryPoint`, monta `ChaskiTheme` y `ChaskiNavHost`. Los recursos Android están en `res/`; los componentes visuales compartidos y el tema están en `ui/`, no en un paquete `core/ui`.

| Carpetas existentes bajo `feature/` | Capas presentes y responsabilidad actual |
|---|---|
| `auth/` | Presentation, Domain y Data: autenticación y operaciones de cuenta mediante Firebase Auth. |
| `address/` | Las tres capas: direcciones, selección local, mapa, ubicación y geocodificación. |
| `business/` | Las tres capas: solicitudes, evaluación/supervisión, locales, responsables, horarios y auditoría mediante Firestore. |
| `catalog/` | Las tres capas: categorías, productos, imágenes y configuración de precio/disponibilidad por local. |
| `discover/` | Las tres capas: locales disponibles según dirección, cobertura y horario; también conserva un repositorio de demostración. |
| `search/` | Las tres capas: búsqueda, oferta por local y detalle de producto conectados a datos; conserva pantallas de muestra. |
| `cart/` | Domain y Presentation: validación aislada de productos y pantalla de carrito de muestra; no tiene capa Data. |
| `checkout/`, `orders/`, `menu/` | Presentation: pantallas de compra, pedidos y opciones con datos o estado de demostración. |
| `home/`, `restaurants/` | Presentation: consumen la presentación de locales disponibles de Discover; existen también piezas anteriores de exploración. |
| `profile/` | Presentation: usa Auth para cuenta/perfil; otras opciones son pantallas pendientes o de muestra. |
| `splash/`, `onboarding/`, `main/` | Presentation: arranque, introducción y contenedor de pestañas. |

## 3. Responsabilidades de las capas

| Capa | Responsabilidad y ejemplo |
|---|---|
| **Presentation** | Screens y componentes específicos muestran datos y emiten acciones. ViewModels coordinan operaciones y exponen UiState, incluyendo carga, error y contenido. `AddressScreen.kt`, `AddressViewModel.kt` y `AddressUiState.kt` son un ejemplo. La pantalla no decide cómo persistir. |
| **Domain** | Modelos, interfaces Repository, proveedores abstractos y reglas de negocio independientes de la fuente de datos. En Address contiene validación de direcciones y política de selección. Los casos de uso se introducen cuando encapsulan una operación o regla real; no son obligatorios para cada llamada al repositorio. |
| **Data** | Implementaciones Repository, fuentes remotas/locales, mappers, adaptadores Android y módulos DI propios. Traduce datos externos al dominio y coordina persistencia, sesión y errores. |

La dirección esperada de las dependencias es `Presentation → Domain ← Data`; Hilt conecta contratos con implementaciones. Domain puede utilizar Kotlin, Flow y tipos comunes apropiados, pero debe evitar Firebase, Retrofit, Room y Compose. La dependencia de `AuthRepository` respecto de `android.app.Activity` es una excepción actual, no el patrón a reproducir.

Las subcarpetas se crean cuando hay responsabilidades que separar. No es necesario generar `usecase`, `mapper`, `remote` o `di` vacíos para cada feature. `feature/cart/domain/ValidateProductForCart.kt` ya contiene una regla concreta de validación, pero todavía no está invocada desde el flujo productivo del carrito.

## 4. Address como ejemplo

| Ruta dentro de `feature/address/` | Clases y responsabilidad |
|---|---|
| `domain/model/` | `AddressModels.kt`: dirección, borrador, coordenadas, libro de direcciones y estados de lectura. |
| `domain/repository/` | `AddressRepository.kt`: observar, crear, editar, borrar, predeterminar y seleccionar direcciones de la cuenta activa. |
| `domain/provider/` | `DeviceLocationProvider.kt` y `ReverseGeocodingProvider.kt`: contratos de ubicación y sugerencia de dirección. |
| `domain/validation/` | `AddressValidator.kt` y `AddressSelectionPolicy.kt`: validación y resolución de selección válida. |
| `data/remote/` | `AddressRemoteDataSource.kt`, `FirestoreAddressRemoteDataSource.kt`, `AddressDocumentMapper.kt` y `DeliveryMetadata.kt`: contrato técnico, Firestore, adaptación y metadatos de operaciones. |
| `data/local/` | `AddressSelectionStore.kt` y `DataStoreAddressSelectionStore.kt`: ID seleccionado por usuario en almacenamiento local. |
| `data/location/` | `AndroidDeviceLocationProvider.kt`, `AndroidReverseGeocodingProvider.kt` y `CurrentLocationSearch.kt`: adaptación de los servicios Android. |
| `data/repository/` | `FirebaseAddressRepository.kt`: implementa el contrato de Domain coordinando fuente remota, selección local y sesión. |
| `data/di/` | `AddressDataModule.kt`: bindings de Address y provider de sus preferencias locales. |

`data/AddressSessionSource.kt` permanece directamente en Data: contiene el contrato de sesión y su implementación `FirebaseAddressSessionSource`. `presentation/` contiene el formulario/listado, su estado y ViewModel, restauración del editor y los componentes del mapa.

El flujo de alta está escrito así en el código:

1. El formulario de `presentation/AddressScreen.kt` emite `AddressAction.Save`.
2. `AddressViewModel.onAction()` valida el borrador y llama a `AddressRepository.createAddress()` con un identificador estable para reintentos.
3. Hilt entrega `FirebaseAddressRepository`, que valida la sesión y delega en `AddressRemoteDataSource.create()`.
4. `FirestoreAddressRemoteDataSource` ejecuta la transacción; `AddressDocumentMapper` adapta los campos. Firestore conserva direcciones, predeterminada y metadatos de operación.
5. El resultado de la operación y la observación mediante `observeAddressBook()` actualizan el estado; Compose vuelve a representarlo. La lista no se sustituye por un éxito remoto inventado.

**Firestore y DataStore cumplen responsabilidades distintas.** Firestore guarda las direcciones en `users/{uid}/addresses/{addressId}`, la predeterminada en `users/{uid}/preferences/delivery` y registros técnicos en `users/{uid}/address_operations/{operationId}`. DataStore guarda solamente el ID seleccionado por UID en `chaski_address_selection`, identificado en Hilt con `@AddressPreferences`.

Seleccionar una dirección localmente no equivale a cambiar la predeterminada remota. La selección se revalida frente a las direcciones de la cuenta. Address no usa Room para persistir direcciones ni la selección. El mapa y GPS ayudan a obtener un punto que el usuario debe confirmar antes de guardar; el geocodificador ofrece texto editable, no una dirección infalible.

## 5. Core e infraestructura compartida

| Paquete | Estado y responsabilidad |
|---|---|
| `core/firebase/` | En uso. `FirebaseModule.kt` proporciona Auth y Firestore compartidos. `FirebaseAccessControl.kt` realiza comprobaciones de sesión, administración, propiedad del negocio y operación de locales en el cliente. |
| `core/network/` | Preparado. `NetworkModule.kt` construye Json, OkHttp, Retrofit y `ChaskiApi`; `ChaskiApi.kt` solo declara `health()`. No se encontraron consumidores funcionales de esa API ni endpoints de pedidos. |
| `core/database/` | Preparado. `DatabaseModule.kt` construye `ChaskiDatabase`, con `AppConfigEntity`, tabla `app_config` y `AppConfigDao`. No se encontraron consumidores funcionales del DAO; no persiste el carrito. |
| `core/datastore/` | En uso. `SessionDataStore.kt` guarda sesión y marca de onboarding en `chaski_session`; lo consumen Auth, Splash y Onboarding. Es independiente del DataStore de selección de Address. |
| `core/navigation/` | En uso. `ChaskiNavHost.kt`, `ChaskiTab.kt` y `MainTabNavigation.kt` coordinan destinos y pestañas. El grafo compone las features y comparte el ViewModel de Address entre Main y su pantalla de gestión. |
| `core/common/` | En uso. `ApiResult.kt`, `UiState.kt` y `RetryableQuery.kt` ofrecen resultados, estados y reintentos compartidos. |

Core agrupa infraestructura realmente transversal. La reutilización de un modelo por dos features no exige trasladarlo allí: puede seguir perteneciendo al dominio que lo define. Tampoco corresponde unificar los DataStore de sesión y direcciones, cuyas identidades y responsabilidades son diferentes.

## 6. Hilt e inyección de dependencias

`@Inject constructor` permite a Hilt construir una clase a partir de sus dependencias. `@Binds` vincula una interfaz con una implementación, como `AddressRepository` con `FirebaseAddressRepository`. `@Provides` describe cómo obtener un objeto, por ejemplo mediante `FirebaseAuth.getInstance()`. `@Singleton` conserva una instancia por componente de aplicación para ese binding; no significa persistencia en disco ni autoriza cambiar otros scopes.

Los nueve módulos actuales están instalados en `SingletonComponent`:

| Archivo | Responsabilidad |
|---|---|
| `core/firebase/FirebaseModule.kt` | `provideFirebaseAuth()` y `provideFirestore()`, ambos `@Singleton`, usando los `getInstance()` existentes de la aplicación Firebase predeterminada. |
| `feature/auth/data/AuthDataModule.kt` | Binding de `AuthRepository` a `FirebaseAuthRepository`. |
| `feature/address/data/di/AddressDataModule.kt` | Seis bindings: repositorio, fuente remota, sesión, selección local, ubicación y geocodificación; además `provideAddressPreferences()` con `@AddressPreferences`. |
| `feature/business/data/BusinessDataModule.kt` | Bindings de repositorios de negocios/locales y normalizador de teléfonos. |
| `feature/catalog/data/CatalogDataModule.kt` | Bindings de catálogo/configuración por local y `provideStorage()` para `FirebaseStorage`. |
| `feature/discover/data/DiscoverDataModule.kt` | `AvailableStoresRepository` a `FirebaseAvailableStoresRepository`; también conserva `DiscoverRepository` a `FakeDiscoverRepository`. |
| `feature/search/data/SearchDataModule.kt` | `StoreOfferRepository` a `FirebaseStoreOfferRepository`. |
| `core/network/NetworkModule.kt` | Providers de la infraestructura HTTP/serialización. |
| `core/database/DatabaseModule.kt` | Provider de la base Room. |

Auth y Firestore tienen un único provider compartido cada uno. Los bindings de repositorios indicados son singleton; el binding de `BusinessPhoneNormalizer` no declara scope. No deben homogeneizarse scopes por motivos de organización. FirebaseStorage permanece en Catalog; `SessionDataStore` y `FirebaseAccessControl` se construyen mediante sus constructores inyectables, sin módulos adicionales.

`FirebaseAccessControl` recibe FirebaseAuth y FirebaseFirestore y es utilizado por los repositorios de Business y Catalog. Comprueba el claim `admin` y consulta información de propiedad/asignación. `FirebaseAuthRepository` también obtiene `AuthUser.isAdmin` del claim del token. Estas comprobaciones en Android deben acompañarse de **reglas de seguridad del servicio** que impongan los permisos efectivos sobre Firestore; documentar el cliente no demuestra qué reglas están desplegadas.

## 7. Comunicación entre features

Las siguientes dependencias se observan en los imports y consumidores actuales; la flecha indica «utiliza elementos de»:

| Dependencia | Motivo |
|---|---|
| Profile y Business → Auth Domain | Cuenta/sesión mediante `AuthRepository` y modelos asociados. |
| Catalog → Auth y Business Domain | Identidad, negocio habilitado y locales para administrar catálogo/configuraciones. |
| Discover → Address y Business Domain | Dirección seleccionada mediante `AddressRepository`, coordenadas, modelos de locales y reglas de disponibilidad/horario. |
| Search → Discover y Catalog Domain | Locales disponibles mediante `AvailableStoresRepository`, productos, categorías y configuración de oferta. |
| Cart Domain → Catalog Domain | `ValidateProductForCart` valida producto, precio y disponibilidad por local. |
| Home y Restaurants → Discover Presentation | Reutilizan `AvailableStoresContent` y `AvailableStoresViewModel`; `HomeViewModel` conserva además el contrato anterior `DiscoverRepository`. |
| Main → Home, Search, Orders y Profile Presentation | Composición de las pestañas principales. |
| Business → Address Presentation/Domain | La gestión de locales reutiliza `AddressMapRoute`, `AddressMapRequest` y coordenadas. |
| Business y Catalog → Auth Presentation | Reutilización actual de componentes visuales como `AuthAppBar`. |

Hay una excepción relevante: `feature/search/data/FirebaseStoreOfferRepository.kt` importa `StoreProductConfigurationDocumentMapper` desde Data de Catalog. Es acoplamiento a un detalle interno y queda como deuda técnica. La reutilización de presentación entre features también requiere revisar propiedad y estabilidad; no justifica mover automáticamente todos esos componentes a Core.

Para nuevas funcionalidades, preferir contratos de Domain cuando la colaboración esté justificada, evitar acceso innecesario a Data ajeno y comprobar que no aparezcan ciclos. Los contratos deben aportar una frontera útil; no se añadirá una interfaz o un caso de uso solo para envolver otra llamada sin responsabilidad propia.

## 8. Tecnologías y estado de implementación

«En uso» significa que existen consumidores en los flujos del código; no certifica configuración remota, disponibilidad ni aceptación en dispositivo.

| Tecnología | Estado observado |
|---|---|
| Kotlin, corrutinas/Flow | En uso en modelos, reglas, ViewModels y repositorios. |
| Jetpack Compose, Material 3 y Navigation Compose | En uso en UI, tema, estados y navegación. |
| Hilt | En uso para construcción de ViewModels, repositorios e infraestructura. |
| Firebase Authentication | En uso en acceso, registro, recuperación, teléfono y perfil. |
| Cloud Firestore | En uso en Address, Business, Catalog, Discover y Search, con listeners, consultas y escrituras según cada feature. |
| Firebase Cloud Storage y Coil | Catalog tiene subida de imágenes mediante `putBytes()` y almacenamiento de su URL; Coil muestra imágenes en catálogo y detalle de producto. El código no demuestra que el bucket y los permisos estén correctamente habilitados en producción. |
| DataStore Preferences | En uso para sesión/onboarding y, en una instancia separada, selección de dirección por UID. |
| Room | Configurado para `app_config`, sin consumidor funcional encontrado. No es la persistencia actual de carrito, pedidos ni Address. |
| Retrofit, OkHttp y kotlinx.serialization | Infraestructura HTTP preparada con contrato `health`; no hay backend operativo de pedidos demostrado por este repositorio. |
| MapLibre, OpenFreeMap y servicios Android | Mapa de selección y ubicación puntual con `LocationManagerCompat`; texto sugerido mediante `Geocoder`. No usa FusedLocationProvider ni Google Maps en ese flujo. |

El backend personalizado de compra/pedidos y la integración con Rider siguen pendientes. Node/Express del plan previo es una propuesta, no una decisión vigente acreditada por este código; Spring Boot tampoco es una elección confirmada. WorkManager, FCM, chat e IA son integraciones previstas, no funcionalidades implementadas aquí.

## 9. Convenciones para nuevas funcionalidades

1. Crear `feature/<nombre>/` para una responsabilidad de producto definida; añadir las capas y subcarpetas necesarias al implementar un flujo real. Mantener nombres de paquetes alineados con sus rutas.
2. Ubicar modelos, reglas y contratos consumidos por presentación en Domain. Mantener implementaciones SDK, fuentes y mappers en Data; los contratos técnicos internos de Data pueden permanecer allí.
3. Exponer estado desde el ViewModel y observarlo desde Compose con el ciclo de vida. Las pantallas emiten acciones; no deben acceder directamente a Firebase, Room o HTTP.
4. Usar `@Binds` para interfaz/implementación y `@Provides` para objetos cuya obtención requiere una fábrica o configuración. Mantener DI propia en `feature/<nombre>/data/di/` cuando esa separación aporte claridad, sin exigir mover de inmediato los módulos existentes.
5. Reutilizar los providers compartidos de Core sin duplicarlos. Al reorganizar, conservar tipos, scopes, qualifiers, inicialización, nombres de almacenes y contratos persistidos.
6. Introducir casos de uso cuando encapsulen reglas, coordinación reutilizable o una operación significativa. No exigir una clase por cada método del repositorio.
7. Revisar imports, consumidores y ownership antes de extraer elementos compartidos. Evitar duplicaciones y ciclos; no convertir Core en un contenedor de todos los modelos.
8. Tras refactorizar código, revisar el diff y ejecutar desde la raíz `.\gradlew.bat assembleDebug` y las pruebas pertinentes; usar `.\gradlew.bat :app:testDebugUnitTest` para comprobar la suite JVM. Verificar también UI/dispositivo o reglas del servicio cuando el cambio afecte esos contratos. Compilar no sustituye esa validación.

Para HU14–HU17, partir de los contratos existentes de Address, Catalog y Discover, y definir explícitamente propiedad del carrito, revalidación, persistencia, confirmación e histórico del pedido. Las pantallas de demostración y la regla aislada del carrito no constituyen esas implementaciones. Esta guía no elige su backend ni introduce esos cambios.

## 10. Limitaciones y próximas revisiones

- La organización de Address y la centralización de Auth/Firestore están aplicadas; las demás features todavía requieren evaluación individual. No se presupone una refactorización global terminada.
- Revisar el mapper de Catalog utilizado por Search, el uso de `Activity` en Domain de Auth y los componentes visuales prestados entre features antes de ampliar esos acoplamientos.
- Discover conserva `FakeDiscoverRepository` y hay pantallas/rutas de muestra o anteriores junto a flujos reales. El grafo marca varios destinos de compra/pedidos con `DemoNotice`; no deben presentarse como operaciones persistidas.
- Las verificaciones de acceso del cliente no sustituyen auditoría de reglas remotas; tampoco se certifica aquí el servicio Storage. No se consultó ni modificó Firebase durante esta documentación.
- El plan previo y los resúmenes locales contienen antecedentes que ya no describen todo el código actual. Contrastar afirmaciones con las fuentes vigentes; no adoptar automáticamente tecnologías propuestas ni publicar documentación local excluida.
- La validación previa de la fase 1C registró `assembleDebug` y 232 pruebas unitarias correctas. Esta fase solo documenta: no repite esas pruebas ni acredita recorridos completos en dispositivo. Para comprobar cobertura, consultar `app/src/test/` y `app/src/androidTest/` junto con los criterios de cada funcionalidad.
