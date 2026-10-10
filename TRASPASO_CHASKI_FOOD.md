# Chaski Food — estado del proyecto y traspaso de desarrollo

**Fecha de corte: 10 de octubre de 2026.** Documento para que otro desarrollador pueda continuar con el contexto de lo revisado, corregido y pendiente. Describe el código local y las comprobaciones disponibles; no certifica el estado remoto de Firebase ni que todos los recorridos funcionen en un celular.

## 1. Qué estamos construyendo

Chaski Food es una aplicación Android de delivery: acceso y perfil del cliente, direcciones de entrega, incorporación de negocios, administración de locales y catálogo, compra y seguimiento de pedidos. La planificación contempla integrar el reparto con **Chaski Rider**.

El trabajo revisado se concentró en **EN01, EN02 y HU01–HU10**, además de corregir problemas de experiencia de usuario. Las pantallas de compra y seguimiento existentes sirven como demostración; su presencia no significa que esas historias estén integradas.

Se consultó la planificación de `contexto`. Sus estados originales, como «Por iniciar», no reflejan automáticamente el avance del código. Se excluyeron `Export` y `docs` del análisis de contenido solicitado.

## 2. Estado de Git: importante antes de continuar

- Repositorio configurado: [RodrigoSG232/Chaski-Food](https://github.com/RodrigoSG232/Chaski-Food).
- Rama local revisada: `main`.
- HEAD local al redactar este documento: `a5cd32a` — `Correcíón ubicación`.
- Commit anterior relevante: `90332a9` — `fix: corregir autenticación, permisos, navegación, reenvío SMS y conservación de borradores`.
- **Los cambios de los bloques 3 y 4 siguen en el directorio de trabajo, sin commit.** Incluyen fuentes modificadas y archivos nuevos de dominio, componentes y pruebas.
- No se comprobó si el remoto contiene el HEAD local. No asumir que clonar GitHub entrega todo lo descrito aquí.

El compañero debe recibir el código actualizado, incluidos los archivos nuevos de `app/src/test` y `app/src/androidTest`. Este documento por sí solo no incorpora las correcciones a su copia.

Para comprobar el punto de partida, desde la raíz:

```powershell
git status --short
git log -5 --oneline
git diff --stat
git diff --check
```

No aplicar nuevamente los parches antiguos de revisión: las correcciones descritas ya están incorporadas a las fuentes locales.

## 3. Arquitectura y herramientas actuales

La app usa **Kotlin, Jetpack Compose y Material 3**, con organización por funcionalidades y separación entre `presentation`, `domain` y `data`. Los ViewModels exponen estados y usan corrutinas/Flow; Hilt conecta las implementaciones de los repositorios.

| Elemento | Configuración del proyecto |
|---|---|
| Identificador Android | `com.chaskifood.app` |
| Android mínimo | API 24 |
| SDK de compilación y destino | API 37 |
| Android Gradle Plugin | `9.4.1` |
| Kotlin / KSP | `2.2.10` / `2.2.10-2.0.2` |
| Gradle Wrapper | `9.6.0` |
| Compose BOM | `2026.02.01` |
| Hilt | `2.60.1` |
| Firebase BOM | `33.10.0` |
| JVM del daemon configurada | Toolchain 25 en `gradle/gradle-daemon-jvm.properties` |
| Compatibilidad Java del código Android | Java 11 |

En el equipo revisado, el wrapper se inició desde un JDK 21 y Gradle utilizó su configuración de daemon/toolchain. No confundir la compatibilidad Java 11 del código con la versión que necesita ejecutar el entorno de compilación.

Firebase Authentication, Firestore y Storage tienen implementación real para acceso, direcciones, negocios, locales y catálogo. DataStore mantiene sesión y selección de dirección. Room y Retrofit/OkHttp están configurados como base técnica; **no existe por ello un backend completo de delivery ni una sincronización offline completa**.

`ChaskiApi` expone actualmente un endpoint de salud. `BuildConfig.API_BASE_URL` contiene `http://10.0.2.2:3000/api/v1/`, dirección pensada para un servidor local accesible desde el emulador. Revisar la configuración de entorno cuando se conecte una API real o se pruebe desde un teléfono físico.

### Mapa de código

Las rutas de esta tabla parten de `app/src/main/java/com/chaskifood/app/`:

| Ruta | Responsabilidad |
|---|---|
| `core/navigation` | Rutas, navegación principal y selección/restauración de pestañas |
| `core/firebase/FirebaseAccessControl.kt` | Comprobaciones de administrador, propietario y operador |
| `core/common` | `ApiResult`, `UiState` y consultas con reintento |
| `core/datastore` | Sesión local |
| `core/database`, `core/network` | Bases de Room y Retrofit |
| `feature/auth` | Correo/contraseña, Google, recuperación y verificación telefónica |
| `feature/profile` | Perfil, Cuenta y acceso a funciones secundarias |
| `feature/address` | Direcciones, mapa, ubicación, selección y persistencia |
| `feature/business` | Solicitudes, evaluación, supervisión, auditoría, locales y responsables |
| `feature/catalog` | Categorías, productos, precios y fotos |
| `feature/home`, `discover`, `search`, `restaurants`, `menu` | Interfaz de exploración; conserva datos de muestra |
| `feature/cart`, `checkout`, `orders` | Recorrido de compra/pedido de ejemplo |
| `ui/components`, `ui/theme` | Componentes reutilizables y diseño Chaski |

## 4. Funcionalidades presentes antes de los últimos bloques UX

«Implementado» en esta tabla significa que existe el código correspondiente. La integración remota y los criterios de aceptación requieren las comprobaciones indicadas más adelante.

| Alcance | Código disponible y límites |
|---|---|
| EN01 | Arquitectura por capas, Hilt, corrutinas, navegación, estados y bases de red/Room |
| EN02 | Tema Material 3, colores, tipografía, dimensiones y componentes comunes |
| HU01–HU02 | Registro, ingreso, cierre de sesión, recuperación, Google y flujo telefónico con Firebase |
| HU03 | Consulta y edición del nombre del perfil; conservación del borrador al rotar |
| HU04 | Direcciones propias, coordenadas/mapa, ubicación actual, edición/eliminación, predeterminada y selección de entrega |
| HU05 | Registro de negocio, estado de solicitud y corrección/reenvío de una solicitud observada |
| HU06 | Evaluación administrativa: aprobar, observar o rechazar, con responsable/fecha y auditoría |
| HU07 | Suspensión/reactivación de negocios y locales, con control de acceso y registro administrativo |
| HU08 | Gestión de locales y responsables, edición y reasignación de locales |
| HU09 | Horarios semanales y estado operativo; restricción por asignación y disponibilidad, con un pendiente de acceso explicado en la sección 8 |
| HU10 | Categorías y productos, activación, precio, tiempo de preparación y fotos de cámara/galería |

### Recorrido básico para probar los roles

Con cuentas y datos de prueba separados:

1. Cliente/propietario: registrar o iniciar sesión, completar el flujo telefónico cuando se solicite y gestionar una dirección propia.
2. Propietario: desde Cuenta, registrar el negocio y enviar la solicitud; debe quedar pendiente de evaluación.
3. Administrador Chaski: entrar con una cuenta que tenga el claim administrativo y aprobar, observar o rechazar la solicitud desde Evaluación.
4. Propietario de un negocio aprobado: crear/editar locales, responsables, categorías y productos. Una aprobación no convierte a otra cuenta en propietaria.
5. Propietario: asignar uno o más locales al correo de una cuenta responsable de prueba.
6. Responsable: verificar su correo, iniciar sesión con esa cuenta y abrir Operaciones de Locales. Comprobar horarios y estados únicamente de sus asignaciones.
7. Administrador: comprobar suspensión/reactivación y auditoría. La suspensión administrativa y la pausa que aplica el responsable deben mantenerse diferenciadas.

Usar números de prueba de Firebase Authentication para verificar SMS sin enviar mensajes a números reales durante estas comprobaciones. No esperar que los productos recién guardados aparezcan en Inicio o en el carrito de muestra: esa conexión está pendiente.

### Permisos y persistencia reforzados

- Ser administrador depende del custom claim **`admin: true`**. Un correo que contenga la palabra «admin» no concede permisos.
- El propietario del negocio se comprueba por `ownerUid`; la solicitud actual se guarda en `business_requests/{uid}`.
- Las decisiones administrativas usan transacciones y auditoría. Las reglas candidatas comprueban el registro asociado mediante `lastAuditId`/`getAfter()` y restringen su modificación posterior.
- Las asignaciones usan el ID **`<businessId>_<correo-en-minúsculas>`**, para poder comprobarlas desde las reglas.
- Al editar un responsable antiguo con ID aleatorio, la app guarda el documento con el ID nuevo y elimina el anterior en un mismo batch.
- Una suspensión administrativa no es una pausa operativa. Reactivar conserva horarios y estado operativo; suspender no borra el historial.
- Las fotos nuevas se suben a Storage como JPEG. Firestore guarda la URL HTTPS; las imágenes Base64 anteriores mantienen compatibilidad y se convierten al editar/guardar.
- Si una escritura de Firestore queda sin confirmación, no se elimina automáticamente la imagen recién subida: la escritura podría completarse después. La limpieza de imágenes huérfanas queda para un proceso de servidor con retención y comprobación de referencias.

## 5. Correcciones de experiencia de usuario realizadas

La revisión local se documentó originalmente en `revision-ux.md`, archivo que se acordó no subir a Git. Este documento incluye el contexto necesario para continuar sin depender de ese archivo.

### Bloque 1 — Márgenes de sistema, teclado y pestañas

Problema reportado: la barra inferior se veía bien con gestos, pero quedaba afectada por la navegación Android de tres botones.

Cambios aplicados:

- Protección del área segura en la navegación y tratamiento de los márgenes de `Scaffold` para evitar solapamientos o márgenes duplicados.
- Restauración de los márgenes de sistema en las barras inferiores comunes.
- Ocultación de las pestañas cuando aparece el teclado.
- Navegación de las pestañas secundarias hacia la entrada principal existente, con selección única y restauración de estado.
- Desplazamiento en diálogos administrativos y pantallas de progreso/entrega para alcanzar acciones en ventanas cortas.

Archivos de referencia: `ChaskiNavHost.kt`, `MainScreen.kt`, `MainTabNavigation.kt`, `ChaskiBottomNav.kt`, `ChaskiFlowBottomBar.kt` y pantallas/diálogos que consumen los márgenes.

### Bloque 2 — Recuperación de errores, reenvío SMS y borradores

- Evaluación, Supervisión, Auditoría, Locales, Catálogo y Operaciones representan fallos y permiten reintentar la carga.
- El reintento sustituye la observación anterior y expone el nuevo estado de carga.
- Observación, rechazo y suspensión muestran motivo, progreso y error dentro del diálogo; bloquean confirmación vacía y solicitudes simultáneas.
- Envío y verificación telefónica comparten `PhoneAuthViewModel`. `SavedStateHandle` conserva número, identificador, código y próxima fecha de reenvío.
- Reenvío SMS con espera de **60 segundos**, reutilización del token disponible para el mismo teléfono/usuario y actualización del identificador sin abandonar la pantalla.
- Posibilidad de corregir el número; mensajes para código inválido/vencido y límites de intentos. Un envío sin respuesta termina con error a los **90 segundos**.
- Conservación al rotar de formularios, diálogos y selecciones de negocio, local, responsable, categoría y producto.
- `SaveableStateHolder` mantiene borradores cuando una carga/error oculta temporalmente el formulario.
- El nombre del perfil no se reemplaza por una recarga del mismo usuario. Se conservan país, horarios sin guardar y motivo de pausa; plegar el horario conserva los cambios.
- En fotos, el estado guardado conserva una URI o referencia al archivo de caché, sin bitmap/Base64 dentro del estado de instancia. Una referencia que ya no se puede leer requiere nueva selección.

La captura actual usa `TakePicturePreview` y guarda el resultado en caché; la galería conserva la referencia y solicita permiso persistente cuando está disponible. La conservación de estado no equivale a guardar permanentemente un formulario ni garantiza conservarlo tras desinstalar, borrar datos o descartar su pantalla.

### Corrección adicional — «Usar mi ubicación actual»

Se revisó el fallo reportado incluso con permisos y ubicación activados. El código actual consulta las fuentes de ubicación disponibles —fused cuando corresponde, GPS con permiso preciso y red— con un plazo compartido de **15 segundos**. Acepta la primera captura válida y cancela las restantes, evitando quedar esperando una fuente mientras otra ya respondió.

Se rechazan coordenadas inválidas y capturas con antigüedad superior a **60 segundos**. La app distingue permiso requerido, ubicación desactivada, posición no disponible y ubicación aproximada. El ViewModel actualiza el punto/centrado del mapa y solicita una sugerencia de dirección.

Archivos clave: `AndroidDeviceLocationProvider.kt`, `CurrentLocationSearch.kt` y `AddressMapViewModel.kt`. La precisión real depende del dispositivo y debe comprobarse en el mapa; obtener una captura no significa guardar automáticamente la dirección.

### Bloque 3 — Acciones por estado, guardados y validaciones

**Evaluación de negocios:** Aprobar/Observar/Rechazar solo aparecen en `PENDING_REVIEW`. El ViewModel vuelve a comprobar el estado antes de enviar. La lista permanece visible durante el guardado; se bloquean acciones/filtros y se muestra progreso. El repositorio mantiene su validación transaccional.

**Teléfono del negocio:** se guarda con prefijo internacional, validado por Android según el país. Se admiten contactos fijos y móviles válidos. Al corregir una solicitud se separan prefijo y número; registros antiguos sin prefijo se interpretan como números nacionales de Perú. Pegar un número internacional reconoce su prefijo; país/número se conservan ante reenvío o fallo. Hay error junto al campo.

**Precio decimal:** se aceptan punto y coma, con hasta dos decimales y sin separadores de miles. `12`, `12.5`, `12,5` y `12.50` se muestran como `12.00` o `12.50`, según corresponda. El valor se normaliza al finalizar la edición y al presentarlo en el catálogo. No se redondean silenciosamente entradas con más de dos decimales ni se convierte un error a cero. Se rechazan vacío, cero, valores ambiguos/no finitos y cantidades que no puedan conservarse con la precisión requerida. El modelo persistido sigue usando `Double`; esto todavía no constituye un sistema de cobros.

**Operación de locales:** cada local tiene estado independiente de guardado, error y éxito. No se envían dos cambios simultáneos de estado/horario al mismo local; otros locales pueden seguir operándose. El diálogo de pausa conserva el motivo ante error y se cierra solo al confirmar éxito, incluso al rotar. Los borradores sobreviven a cargas que oculten temporalmente la tarjeta.

Archivos nuevos importantes: `BusinessContactPhone.kt`, `AndroidBusinessPhoneNormalizer.kt` y `ProductPrice.kt`. Revisar también los ViewModels de Registro, Evaluación, Catálogo y Operaciones.

### Bloque 4 — Accesibilidad, idioma y funciones pendientes

- El botón de contraseña anuncia **Mostrar/Ocultar contraseña** según su acción. Los campos comunes de acceso usan etiqueta nativa asociada al campo.
- Búsqueda tiene un nombre accesible; eliminar un reciente anuncia el término concreto y utiliza un botón de 48 dp. Se conservan consulta/recientes al restaurar estado.
- Acciones de búsqueda/filtros y menús de Cuenta tienen roles explícitos; los títulos de sección se marcan como encabezados. Los logotipos decorativos no repiten el nombre del botón.
- La selección de medio de pago de ejemplo anuncia su estado como botón de opción.
- Los siete días del horario aparecen completos en español. Apertura/Cierre abren un selector de **24 horas**, con hora de Perú. Aceptar cambia el borrador; Cancelar lo conserva.
- Los horarios nocturnos indican día/hora de cierre; por ejemplo, domingo 23:00–03:00 cierra el lunes. No se guarda un turno habilitado con apertura y cierre iguales.
- El editor usa controles de ancho completo y alturas mínimas; se retiraron los anchos fijos de los campos y la altura fija de 36 dp de los botones operativos.
- Facebook se oculta cuando no existe una acción configurada. El ingreso real con Google se mantiene.
- Tarjetas, métodos de pago del perfil, vinculación de cuentas y referidos muestran **Próximamente** con explicación y regreso. Se retiró el formulario de tarjeta sin integración y las promesas ficticias de descuentos/enlaces de referido.
- Notificaciones SMS/push/promocionales, Califícanos y FAQ quedan deshabilitados y señalados como pendientes.
- Inicio, búsqueda y Pedidos identifican sus datos de muestra. Las rutas de restaurantes/compra/seguimiento muestran un aviso de ejemplo que ocupa su propio espacio. Usa una versión compacta en ventanas cortas o con letras grandes, con descripción completa para el lector de pantalla.
- «Completar compra» cambió a **Ver ejemplo de pedido**. La confirmación explica que no se registró un pedido ni se realizó un cobro y permite desplazarse.
- Se retiraron doce controles de más opciones/flechas sin acción.

Referencias de implementación: `FeatureAvailability.kt`, `strings_ux.xml`, `AuthComponents.kt`, `SearchFoodScreen.kt`, `StoreOperationsScreen.kt`, pantallas del perfil y `ChaskiNavHost.kt`.

## 6. Qué todavía es demostración

El catálogo que gestiona el negocio tiene persistencia en Firestore, pero **la oferta que ve el cliente todavía no está conectada a ese catálogo**. Inicio/búsqueda/restaurantes contienen listas de muestra; `DiscoverDataModule` enlaza `FakeDiscoverRepository`.

Carrito, checkout, confirmación, historial y seguimiento/calificación no representan un pedido real integrado. No hay que interpretar nombres de restaurantes, repartidores, precios, tarjetas enmascaradas ni estados de esas pantallas como datos reales.

Todavía están pendientes las integraciones de Facebook, vinculación de cuentas, pagos/tarjetas, referidos, notificaciones de pedidos y comunicación/reparto. El envío de SMS para **autenticación telefónica** sí tiene código Firebase; es distinto de las futuras notificaciones SMS de pedidos.

Al conectar cada historia, sustituir sus datos de muestra y retirar su aviso de ejemplo únicamente cuando el recorrido tenga persistencia y comportamiento real verificados.

## 7. Firebase: configuración y archivos que se necesitan por separado

Proyecto usado en la sesión: **`chaski-food-9adba`**. El compañero necesita acceso autorizado al proyecto o un entorno de pruebas propio, y un `app/google-services.json` correspondiente a `com.chaskifood.app`. Ese archivo está excluido de Git y no lo aporta este documento.

| Elemento | Lo que sabemos al corte |
|---|---|
| Custom claim administrativo | El usuario informó haber completado `set-admin`; no se volvió a consultar el estado remoto al redactar este documento |
| Reglas completas de Firestore | Existe candidata local fusionada; su versión publicada actual no está confirmada aquí |
| Storage y sus reglas | Existe implementación y archivo local de reglas; habilitación/publicación actual no confirmada aquí |
| Proveedores de Authentication | Hay código para correo/contraseña, Google y teléfono; revisar su habilitación/configuración en el proyecto usado por el compañero |
| Cambios remotos durante los bloques UX | No se publicaron reglas ni se modificaron datos remotos desde estas correcciones |

### Authentication y permisos administrativos

Los custom claims se asignan desde un entorno de confianza, no desde Android. Después de cambiarlos debe renovarse el token; en el recorrido usado se indicó cerrar sesión y volver a entrar. [Documentación de custom claims](https://firebase.google.com/docs/auth/admin/custom-claims).

El helper local `firebase/set-admin-cloud-shell.py` contiene parámetros `PROJECT` y `USER_UID`: revisarlos antes de usarlo para otra cuenta. Conserva los claims existentes al añadir `admin: true` y comprueba el resultado. No incluir credenciales de Admin SDK/cuentas de servicio en la app ni en este traspaso.

Durante la sesión apareció un HTTP 403 de Identity Toolkit por proyecto de cuota/API. La versión local del helper obtiene el token con `gcloud auth print-access-token` y envía `x-goog-user-project` con el proyecto objetivo. Si se reproduce, comprobar identidad, permisos, API y proyecto de cuota del mecanismo de autenticación usado; no quitar controles de la app para resolver un error de Cloud Shell.

Para Google, registrar la huella de firma de la compilación del compañero y descargar la configuración actualizada cuando corresponda. Una compilación con otra clave debug puede necesitar configuración adicional. [Configuración oficial de Google en Android](https://firebase.google.com/docs/auth/android/google-signin).

Para el flujo telefónico, comprobar el proveedor Teléfono, la política de regiones permitidas para SMS y las huellas SHA-256/SHA-1 usadas por la verificación de la app y su alternativa reCAPTCHA. Configurar números de prueba en Authentication para probar envío/reenvío sin SMS reales. [Configuración oficial de autenticación telefónica](https://firebase.google.com/docs/auth/android/phone-auth).

### Qué reglas van en cada sitio

| Archivo local | Uso |
|---|---|
| `firebase/firestore-merged.candidate.rules` | Candidata completa: direcciones HU04 más permisos de negocios/locales/catálogo/auditoría; revisar contra las reglas remotas y probar antes de publicar en **Firestore → Reglas** |
| `firebase/firestore-business.rules` | Plantilla de negocios; **no sustituye sola** las reglas completas porque omite las direcciones |
| `firebase/storage.rules` | Reglas de fotos; se revisan/fusionan y publican en **Storage → Reglas**, no en Firestore |

Evitar permisos amplios que coincidan con las rutas restringidas: si varias reglas coinciden, basta que una permita la operación. [Comportamiento de reglas superpuestas](https://firebase.google.com/docs/rules/rules-behavior).

Las reglas locales de Storage comprueban el propietario y estado aprobado del negocio mediante Firestore; permiten crear JPEG de hasta 5 MB en la ruta de productos. La conexión entre productos puede requerir habilitar los permisos correspondientes al guardar esas reglas. [Reglas de Storage con consultas a Firestore](https://firebase.google.com/docs/storage/security/rules-conditions).

### Esquema utilizado por la app

| Ruta | Uso |
|---|---|
| `users/{uid}/addresses/{addressId}` | Direcciones privadas del usuario |
| `users/{uid}/preferences/delivery` | Predeterminada y metadatos del libro de direcciones |
| `users/{uid}/address_operations/{operationId}` | Comprobantes de operaciones de direcciones para reconocer escrituras/reintentos |
| `business_requests/{uid}` | Solicitud del propietario y estado del negocio |
| `stores/{storeId}` | Local, negocio asociado, ubicación, estados y horario |
| `store_managers/{businessId}_{emailLowercase}` | Responsable y locales asignados |
| `categories/{categoryId}` | Categorías del negocio |
| `products/{productId}` | Productos del negocio y URL de foto |
| `audit_logs/{auditId}` | Historial de decisiones administrativas |
| Storage: `businesses/{businessId}/products/{productId}/{uuid}.jpg` | Fotografías nuevas |

Las direcciones usan `label`, `addressText`, `location` con `lat`/`lng`, `reference`, `instructions`, `createdAt` y `updatedAt`. No cambiar nombres/tipos o escribir manualmente documentos incompletos sin revisar el mapper, las transacciones y las reglas. La selección de entrega local y la dirección predeterminada remota tienen responsabilidades distintas.

## 8. Pendiente detectado al final: verificar correo para operar

El usuario preguntó por qué al gestionar se solicita verificar el correo. Se revisó el código y **todavía no se aplicó una corrección a este flujo**.

### Comportamiento actual

1. `StoreOperationsViewModel.loadAssignedStores()` obtiene el correo de la sesión y consulta `getStoresForManager()`.
2. `FirebaseStoreRepository.getStoresForManager()` recarga el usuario cuando corresponde y exige correo verificado antes de consultar asignaciones.
3. `FirebaseAccessControl.requireStoreOperator()` comprueba verificación/asignación cuando quien opera no es el propietario del negocio.
4. Las reglas candidatas también exigen `email_verified` para el acceso del responsable.

La protección por correo es coherente para una asignación basada en email: registrar un correo ajeno no debe conceder acceso a sus locales. **`admin: true` y correo verificado son datos distintos.**

Sin embargo, la pantalla de Operaciones siempre comienza por el flujo de responsables, incluso si la cuenta también es propietaria o administrativa. La comprobación de propietario por UID que permite la capa de autorización no se refleja en esa carga inicial.

Además, el registro llama a `sendEmailVerification()` sin esperar/exponer su resultado. No hay una acción explícita para reenviar ese correo desde el error de Operaciones. El usuario puede abrir el enlace recibido y tocar REINTENTAR; la pantalla necesita una recuperación más clara si el mensaje no llegó.

### Trabajo recomendado como siguiente corrección

- Distinguir carga, fallo de conexión, correo pendiente, ausencia de asignaciones y local no habilitado.
- Añadir envío/reenvío de correo con progreso, éxito/error y protección frente a repetición; comprobar nuevamente el usuario/token al volver del enlace.
- Mantener éxito del registro aunque falle el envío del correo, porque la cuenta ya pudo haberse creado; no repetir el alta.
- Definir el acceso operativo del propietario: consultar por su UID/negocio aprobado y mostrar únicamente sus propios locales cuando corresponda.
- No dar operación universal a un Administrador Chaski solo por su claim: supervisión administrativa, propiedad del negocio y asignación operativa son roles distintos.
- Mantener la exigencia de correo verificado para responsables identificados por email. No sustituirla por un booleano local ni eliminarla de las reglas para ocultar el mensaje.
- Agregar pruebas de propietario, responsable verificado/no verificado, administrador sin asignación, revocación y renovación del token.

## 9. Compilación y pruebas: qué se verificó

Último resultado disponible, después del bloque 4:

| Comprobación | Resultado |
|---|---|
| `assembleDebug` | BUILD SUCCESSFUL; APK generado |
| `testDebugUnitTest` | **169 pruebas**, 0 fallos, 0 errores, 0 omitidas |
| `lintDebug` | 0 errores; **56 advertencias y 1 sugerencia** pendientes |
| `assembleDebugAndroidTest` | BUILD SUCCESSFUL; APK de pruebas generado |
| Ejecución instrumentada de los últimos cambios | **Pendiente** |
| Revisión visual final en celular | **Pendiente** |
| Validación remota completa de permisos/reglas/fotos | **Pendiente de confirmar** |

Los resultados previos de los bloques 1, 2 y 3 fueron 112, 120 y 165 pruebas unitarias respectivamente. Son cortes diferentes del proyecto; no se suman. El último conjunto incluye cuatro pruebas nuevas de día de cierre de jornada.

Pruebas relevantes: direcciones y ubicación, reintentos, flujo de acceso, disponibilidad de locales, ViewModels de negocio/registro/operación y precio decimal. Los repositorios dobles de pruebas están en `app/src/test/.../feature/business/testing/`.

Las pruebas instrumentadas compiladas incluyen navegación de pestañas, restauración de borradores, normalizador telefónico nativo y cinco recorridos nuevos de contraseña, eliminación de un reciente, disponibilidad Google/Facebook, tarjeta pendiente y cancelación de horario nocturno. Compilar esas pruebas no equivale a ejecutarlas ni a comprobar TalkBack.

### Comando usado para limitar memoria

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'
```

`--offline` requiere que las dependencias estén descargadas. En una máquina nueva, realizar la sincronización/descarga inicial con acceso a red y luego usar ese modo si se desea. `gradle.properties` mantiene un heap predeterminado de 2048 MB; el comando anterior lo sustituye para esa ejecución, sin cambiar la configuración global.

Durante la sesión se reportó sobrecarga de la PC al preparar un emulador. Las últimas comprobaciones se hicieron sin abrir otro emulador, con un trabajador y heap de 768 MB. Para esa PC, conservar este enfoque y usar el celular desde Android Studio para la comprobación manual.

Artefactos locales, generados y excluidos de Git:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
app/build/reports/tests/testDebugUnitTest/index.html
app/build/reports/lint-results-debug.html
```

En un dispositivo/emulador destinado a pruebas, `connectedDebugAndroidTest` permite ejecutar la suite instrumentada. Esa tarea controla la interfaz automáticamente; no se ejecutó en el teléfono del usuario, que prefirió hacer los toques manualmente. También hubo un rechazo de instalación por restricciones del teléfono; no se forzó ni se reintentó automáticamente.

## 10. Pruebas manuales e integración que debe cerrar el compañero

| Escenario | Resultado esperado |
|---|---|
| Gestos y tres botones | Barra/etiquetas encima de la navegación del sistema, sin margen duplicado |
| Pestañas desde perfil, carrito y filtros | Destino principal correcto, selección única y sin copias innecesarias en la pila |
| Teclado, pantalla corta y horizontal | Campos y acciones alcanzables; pestañas se ocultan y vuelven al cerrar teclado |
| Rotación de formularios y diálogos | Motivo, campos, selección y referencia de foto conservados; cancelar no guarda |
| Reenvío SMS con número de prueba | Espera de 60 s, progreso, recuperación de error, código actualizado y corrección de número |
| Ubicación actual | Se actualiza punto/centrado, se distingue aproximada/error y puede confirmarse la ubicación |
| Carga fallida y REINTENTAR | Error visible, nueva carga y recuperación sin duplicar observaciones ni perder borrador |
| Evaluación administrativa | Acciones solo en pendientes; motivo/error dentro del diálogo y un solo envío |
| Precio | Punto/coma normalizan a dos decimales; entradas inválidas no se guardan como cero |
| Guardado de horario/estado | Progreso local, error junto a controles, pausa cierra solo tras éxito y no hay doble envío |
| Horario nocturno y domingo | Disponibilidad y aviso corresponden al día siguiente; hora de cierre es límite exclusivo |
| TalkBack y teclado | Nombre, acción, estado y orden de enfoque comprensibles |
| Letras grandes/contraste | Sin controles inalcanzables; medir contraste y comprobar selector/diálogos visualmente |
| Cuenta y recorrido de ejemplo | Pendientes claros; tarjetas no pide datos; compra no aparenta un pedido/cobro real |
| Cuenta normal frente a administrador | Una cuenta normal no evalúa ni altera auditoría |
| Responsable y revocación | Solo opera sus locales; revocar asignación impide nuevas operaciones, también con pantalla abierta |
| Suspensión | Negocio/local suspendido bloquea operaciones nuevas sin borrar historial |
| Fotos en dos dispositivos | Cámara/galería sube JPEG y otra sesión autorizada puede cargar la URL |
| Solicitud observada | Corrige/reenvía conservando identidad y mostrando las observaciones |

Usar datos y números de prueba para los escenarios que escriben, envían SMS o cambian estados. Las pruebas locales con dobles no demuestran que las reglas desplegadas permitan/impidan lo mismo.

## 11. Qué puede desarrollar después

Antes de conectar más pantallas, cerrar el flujo de correo/roles y confirmar las reglas e integración del alcance actual. Después, la continuación más directa de la planificación es:

| Historia | Próximo trabajo funcional |
|---|---|
| HU11 — Configurar productos por local | Modelo/persistencia de precio y disponibilidad por establecimiento |
| HU12 — Descubrir locales disponibles | Consulta real con negocio/local habilitado, horario, estado operativo y cobertura |
| HU13 — Buscar y explorar la oferta | Búsqueda/categorías/filtros sobre datos reales, con vacío/error/carga |
| HU14 — Gestionar carrito de compra | Carrito real, cantidades, productos y consistencia por local |
| HU15 — Checkout y revalidación | Revalidar oferta, disponibilidad, totales y dirección antes de confirmar |
| HU16 — Confirmar pedido | Persistencia, identidad/idempotencia y snapshot de los datos de la compra |
| HU17–HU18 | Pedidos activos/históricos y cancelación según estado |

La planificación posterior incluye HU19–HU21 para atención/preparación/recojo; HU22–HU23 para servicio de reparto y seguimiento; HU24–HU27 para notificaciones, comunicación, incidencias y valoración; HU28 para dashboard; HU29/HU32/HU33 para capacidades inteligentes; HU30–HU31 para offline/sincronización y EN04 para publicación.

Para esas historias, acordar contratos con Chaski Rider/backend y criterios de aceptación antes de reutilizar como definitivos los datos o estados del prototipo. La base Room/Retrofit existente no implementa esos procesos por sí sola.

## 12. Qué compartir y qué mantener fuera del repositorio

La preferencia acordada fue **no subir `firebase`, `Export`, `docs`, `contexto` ni `revision-ux.md`**. Tampoco se suben `google-services.json`, claves de firma, credenciales administrativas o artefactos de compilación.

Este documento está en la raíz, fuera de esas carpetas. Se puede entregar junto con los fuentes actualizados. Los archivos de reglas y el contexto original deben proporcionarse por separado al compañero autorizado si los necesita.

### Diferencia entre la intención y el estado actual de `.gitignore`

Al revisar, `.gitignore` ya contiene exclusiones para Firebase, Export y la revisión UX, además de archivos locales/generados. **Todavía faltan las entradas de `docs` y `contexto`**, y `git ls-files` muestra que hay archivos de esas dos carpetas ya versionados.

Entradas necesarias para representar la preferencia completa:

```gitignore
/firebase/
/Export/
/docs/
/contexto/
/revision-ux.md
**/google-services.json
local.properties
*.jks
*.keystore
```

Añadirlas no retira archivos que Git ya sigue. Para preparar su salida del índice sin borrar las copias locales, el comando aplicable a las carpetas actualmente versionadas es:

```powershell
git rm -r --cached -- docs contexto
```

Esto debe revisarse como parte del commit correspondiente. **No se modificó `.gitignore`, no se quitó nada del índice ni se hizo commit/push al generar este documento.**

### Entrega mínima para que el compañero pueda arrancar

1. Código actual completo, incluidos los archivos nuevos de dominio, componentes y pruebas de los bloques 3/4.
2. Este documento y, si hace falta, la planificación original por separado.
3. Acceso autorizado a Firebase o un proyecto de pruebas, con su configuración Android correspondiente.
4. Archivos locales de reglas/helper por separado, indicando cuáles se publicaron realmente y cuándo.
5. Confirmación de la rama/commit compartidos y de los recorridos manuales que ya se hayan probado, sin marcar como aprobados los pendientes de este documento.

El siguiente trabajo recomendado es resolver **verificación de correo y acceso operativo por rol**, ejecutar las comprobaciones de dispositivo/integración y continuar HU11–HU16 para convertir el recorrido de compra de ejemplo en funcionalidad real.
