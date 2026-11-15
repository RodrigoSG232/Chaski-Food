# Guía de desarrollo de Chasqui Food

Cómo trabajamos en el proyecto, para personas y para asistentes de IA (Claude Code, Codex u otros). Cómo funciona el sistema: [ARCHITECTURE](ARCHITECTURE.md). Qué está hecho y qué falta: [ESTADO_IMPLEMENTACION](ESTADO_IMPLEMENTACION.md).

## 1. Requisitos
- JDK 21, Android SDK (plataforma 37), Android Studio, un AVD o teléfono con ADB.
- Node.js 22 y npm (incluido con Node.js; instalación reproducible con `npm ci` y `package-lock.json`). El Firebase CLI viene en las dependencias de `functions/` (no hace falta instalarlo global).
- Comandos desde la **raíz del repo** en PowerShell, con el wrapper `.\gradlew.bat`.

## 2. Entornos de trabajo y comandos

Regla de oro: **todo cambio se prueba primero en LOCAL**. CONECTADO usa Firebase real con datos y cuentas de prueba autorizadas.

**Selección del modo (Debug).** Sin propiedades, Gradle compila en **LOCAL** (`app/build.gradle.kts`), incluido el botón Run de Android Studio. CONECTADO solo se activa con las tres propiedades de §2.3, pasadas con `-P` o puestas en tu `%USERPROFILE%\.gradle\gradle.properties` personal, que tiene prioridad sobre el del repositorio. **Nunca pongas `chaskiFirebaseMode` ni las demás propiedades `chaski*` en el `gradle.properties` del repositorio**: afectarían a todo el equipo. Si el JSON activo no coincide con el modo, `validateDebugFirebaseConfiguration` detiene la compilación; no hay cambio automático a Firebase real.

### 2.1 LOCAL (Firebase Emulator Suite)
1. JSON de Debug del proyecto demo (solo si no tienes ya un `app/src/debug/google-services.json` personal; respáldalo antes de cambiarlo):
   ```powershell
   Copy-Item app/src/debug/google-services.demo.json app/src/debug/google-services.json
   ```
2. Backend y emuladores (desde `functions/`). Crea antes `functions/.secret.local` con un valor **ficticio** `HEIGIT_API_KEY=local-fixture-only` si no existe:
   ```powershell
   npm ci
   npm run build
   $env:CHASKI_ROUTE_FIXTURE = 'enabled'
   npm run emulators        # Auth 9099, Firestore 8089, Storage 9199 y Functions 5001
   ```
   Se detiene con `Ctrl+C` en esa terminal.
3. App (otra terminal, desde la raíz):
   ```powershell
   .\gradlew.bat :app:assembleDebug -PchaskiFirebaseMode=LOCAL
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   En AVD el host es `10.0.2.2`. En teléfono físico: `adb reverse tcp:9099 tcp:9099`, `adb reverse tcp:8089 tcp:8089`, `adb reverse tcp:9199 tcp:9199`, `adb reverse tcp:5001 tcp:5001` y compilar con `-PchaskiEmulatorHost=127.0.0.1`.
4. Datos: las pruebas automáticas crean sus propios datos ficticios. Para recorridos manuales usa el laboratorio automatizado siguiente; nunca copies datos reales.

#### Laboratorio automatizado para pruebas manuales

El repositorio incluye un sembrador idempotente exclusivo de `demo-chaski-hu15`. Se bloquea si Auth y Firestore no apuntan exactamente a `127.0.0.1:9099` y `127.0.0.1:8089`; su código está bajo `functions/test/` y no forma parte del paquete de Cloud.

1. Levanta los emuladores y deja la terminal abierta:
   ```bash
   cd functions
   CHASKI_ROUTE_FIXTURE=enabled npm run emulators
   ```
2. Desde otra terminal, compila y crea el escenario deseado:
   ```bash
   npm --prefix functions run build
   npm --prefix functions run lab:seed -- hu20
   ```
   Repetir el comando reinicia únicamente los documentos y cuentas fijos del laboratorio. Para retirarlos: `npm --prefix functions run lab:reset`.
3. Cuentas creadas (todas usan `emulator-password`):

   | Experiencia | Correo |
   |---|---|
   | Administrador | `admin@chaski.test` |
   | Propietario | `owner@chaski.test` |
   | Responsable | `manager@chaski.test` |
   | Cliente | `buyer@chaski.test` |

El escenario `hu20` crea dos locales, catálogo mínimo, una asignación VERIFIED, un pedido pendiente y dos pedidos `PREPARING` (25 y 35 minutos; el segundo con `deliveryId`). El responsable usa **Local Central Lab → En preparación**; el cliente consulta los mismos pedidos en **Pedidos**. Los emuladores conservan estos datos mientras sigan encendidos.

En teléfono físico ejecuta antes `adb reverse` para los cuatro puertos y compila con `-PchaskiFirebaseMode=LOCAL -PchaskiEmulatorHost=127.0.0.1`. El JSON activo debe ser `app/src/debug/google-services.demo.json`, copiado como `app/src/debug/google-services.json`; ninguna propiedad CONECTADO debe permanecer en el `gradle.properties` personal.

#### Marketplace preestablecido con imágenes

Para explorar catálogos sin crear manualmente negocios y productos, con los cuatro emuladores levantados ejecuta:

```bash
npm --prefix functions run build
npm --prefix functions run marketplace:seed
```

El comando es idempotente y solo acepta el proyecto local `demo-chaski-hu15`. Crea dos negocios aprobados, tres locales por negocio, ocho categorías, veinte productos y configuraciones distintas por local. Las 26 imágenes JPEG se cargan como objetos en Storage; Firestore guarda solamente sus URL. No crea clientes ni repartidores.

Las cuatro cuentas usan la contraseña `emulator-password` y ya tienen teléfono y correo verificado en Auth:

| Perfil | Correo | Local asignado |
|---|---|---|
| Propietario Chicken | `owner.chicken@chaski.test` | Todos los de Chicken House |
| Responsable Centro Chicken | `manager.chicken.centro@chaski.test` | Chicken House Centro |
| Propietario Burger | `owner.burger@chaski.test` | Todos los de Burger Station |
| Responsable Este Burger | `manager.burger.este@chaski.test` | Burger Station Este |

Para retirarlos usa `npm --prefix functions run marketplace:reset`. Los archivos fuente están en `functions/test/marketplace/assets/`, quedan excluidos del paquete de Cloud y no se publican con Functions.

#### Marketplace permanente compartido

El proyecto real `chaski-food-9adba` contiene también el mismo marketplace como datos persistentes: cuatro perfiles comerciales, dos negocios, seis locales, ocho categorías, veinte productos, sesenta configuraciones por local y veintiséis JPEG en Firebase Storage. No incluye clientes ni repartidores. Los compañeros no deben ejecutar el sembrador para usarlo; solo deben compilar la aplicación en modo `CONECTADO` con su `google-services.json` autorizado.

La carga administrativa es idempotente, exige credenciales fuera del repositorio, una contraseña fuerte por variable de entorno y la confirmación literal `CHASKI_PERMANENT_SEED_CONFIRM=chaski-food-9adba`. Antes de escribir aborta ante colisiones de UID, correo, documento u objeto. `marketplace:verify:permanent` permite auditar cantidades y vínculos sin mostrar secretos. No se ejecuta como parte de compilaciones, pruebas ni despliegues.

### 2.2 Pruebas
| Qué | Comando |
|---|---|
| Unitarias backend | `npm --prefix functions test` (tras `build`) |
| Backend + reglas en emulador (laboratorio limpio, secuencial; requiere `$env:CHASKI_ROUTE_FIXTURE='enabled'` y `.secret.local` ficticio) | desde `functions/`: `npx firebase emulators:exec --only auth,firestore,functions --project demo-chaski-hu15 --config ../firebase.json "node --test --test-concurrency=1 lib/test/*.emulator.js"` |
| Paquete de Functions (antes de publicar) | `npm --prefix functions run verify:package` |
| JVM Android | `.\gradlew.bat :app:testDebugUnitTest` + las propiedades del modo cuyo JSON esté activo |
| Compilar pruebas instrumentadas | `.\gradlew.bat :app:compileDebugAndroidTestKotlin` + propiedades del modo |
| Instrumentadas | `.\gradlew.bat :app:connectedDebugAndroidTest` con AVD/teléfono y, si usan Firebase, los emuladores levantados |

Compilar androidTest **no** equivale a ejecutarlas. Ejecuta las pruebas proporcionales al cambio; no toda la batería por rutina.

### 2.3 CONECTADO (Firebase real)
1. Coloca el JSON **legítimo** de `chaski-food-9adba` en `app/src/debug/google-services.json` (pídelo por el canal interno; nunca a Git).
2. Compila e instala:
   ```powershell
   .\gradlew.bat :app:assembleDebug "-PchaskiFirebaseMode=CONECTADO" "-PchaskiConnectedProjectId=chaski-food-9adba" "-PchaskiCheckoutApiBaseUrl=https://foodapi-u65lcqarqa-tl.a.run.app/api/v1/"
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   `adb install -r` conserva los datos de la app. Si la app instalada fue firmada con otra clave de depuración (otro PC), Android rechaza la actualización: desinstala primero (solo borra datos locales de la app).
3. **Botón Run de Android Studio en CONECTADO:** añade las tres propiedades a tu archivo **personal** `%USERPROFILE%\.gradle\gradle.properties` (no al del repo):
   ```properties
   chaskiFirebaseMode=CONECTADO
   chaskiConnectedProjectId=chaski-food-9adba
   chaskiCheckoutApiBaseUrl=https://foodapi-u65lcqarqa-tl.a.run.app/api/v1/
   ```
   Para volver a LOCAL, quítalas (o pon `chaskiFirebaseMode=LOCAL`) y cambia el JSON activo; la validación de Gradle avisa si no coinciden.
4. Pruebas en CONECTADO: solo con cuentas y dispositivos autorizados. Para probar avisos push, el responsable debe tener **la sesión abierta** en su dispositivo y el comprador debe pedir desde **otro** dispositivo (p. ej. un AVD): cerrar la sesión del responsable retira su dispositivo y el evento termina en `NO_RECIPIENTS`. Crear un pedido escribe datos reales.

### 2.4 Cambios en Cloud (siempre con autorización humana explícita)
Nada en Cloud se modifica automáticamente: ni Functions, ni reglas, ni índices, ni datos, ni IAM, ni secretos. Procedimiento usado en HU16/HU34 (desde la raíz, con `node functions/node_modules/firebase-tools/lib/bin/firebase.js` o `npm --prefix functions exec -- firebase`):
1. Comparar lo publicado con lo local (reglas, índices, funciones) y guardar respaldo de lo publicado.
2. Ensayo: `deploy --only "functions:food-local:foodApi,functions:food-local:newOrderNotifier" --dry-run --project chaski-food-9adba --non-interactive`.
3. Despliegue acotado a las funciones exactas. `--force` solo si el CLI lo exige por el aviso de reintentos de `newOrderNotifier`, nunca para aceptar eliminaciones.
4. Reglas: `deploy --only firestore:rules`; índices: `deploy --only firestore:indexes`. Por separado y verificados.
5. Verificar después (funciones ACTIVE, `FUNCTION_TARGET`, trigger, llamada sin token → 401, reglas publicadas = archivo local) y probar desde Android.

## 3. Metodología para implementar historias de usuario
Proceso riguroso pero proporcional al tamaño de la historia:
1. Leer la HU y sus criterios de aceptación (fuente original: planificación del curso) y la sección de la historia en [ESTADO_IMPLEMENTACION](ESTADO_IMPLEMENTACION.md#4-pendientes-y-mejoras-futuras).
2. Revisar el código actual, las [decisiones vigentes](DECISIONES.md) y la arquitectura relacionada.
3. Analizar procesos de negocio, roles, dependencias y restricciones.
4. Detectar escenarios relevantes no explícitos en los criterios (concurrencia, sesión, red, permisos, datos antiguos) **sin cambiar unilateralmente el alcance**.
5. Identificar riesgos, incompatibilidades y decisiones pendientes.
6. Ante decisiones importantes, proponer alternativas con ventajas y desventajas y recomendar una.
7. **La decisión final es del desarrollador humano** o responsable correspondiente.
8. Preparar un plan proporcional (incrementos pequeños).
9. Implementar siguiendo la arquitectura actual y reutilizando servicios existentes.
10. Probar en LOCAL: casos exitosos, negativos, errores y regresiones relevantes.
11. Revisar reglas, índices, colecciones, funciones y configuración afectadas.
12. Pedir autorización antes de tocar Cloud o datos reales.
13. Desplegar de forma controlada (§2.4), verificar y probar desde Android.
14. Documentar decisiones y resultados en el documento responsable (sin duplicar).
15. Commits claros y pequeños; integración por PR.

Buenas prácticas:
- Los criterios de aceptación no son una lista exhaustiva de escenarios.
- Una propuesta técnica de la IA **no** está aprobada hasta que el humano la aprueba.
- No introducir arquitecturas, librerías ni abstracciones innecesarias; reutilizar lo existente.
- La autorización la decide el backend (foodApi + reglas); el cliente nunca concede permisos.
- Proteger datos personales y cuentas Firebase; usar datos ficticios.
- Evitar documentación excesiva y planificación burocrática.
- Detenerse a pedir aprobación ante cambios de seguridad, eliminaciones, despliegues, cambios de arquitectura o decisiones de alcance.
- **Fuente de verdad:** los requisitos y las decisiones aprobadas definen el comportamiento esperado. El código es evidencia de la implementación actual, no autoridad. Ante una contradicción entre documentación y código:
  1. revisar los requisitos, las decisiones, el código y las pruebas;
  2. determinar el origen de la inconsistencia antes de proponer cambios;
  3. si el documento solo describe mal el código y el código cumple lo aprobado, corregir el documento.

  No se modifica un requisito aprobado únicamente para hacerlo coincidir con el código, y no se modifica código sin autorización humana. Las implementaciones existentes se reutilizan cuando cumplen correctamente los requisitos.

## 4. Git y colaboración
- Una rama por historia o incremento; PR hacia `main` para integrar. No trabajar directamente sobre `main`.
- Commits en español con prefijo (`feat(HUxx - área): …`, `fix(área): …`, `docs: …`, `test(...)`). **Sin líneas de coautoría de asistentes de IA.**
- Nunca subir: `google-services.json`, `.secret.local`, `local.properties`, keystores, tokens, APKs, respaldos de Cloud.
- `docs/` está ignorada salvo los documentos definitivos (lista blanca en `.gitignore`); `docs/HU04_PLAN.md` y `contexto/` (fuentes originales del curso) quedan locales.
- Hacer push o merge **no despliega** Cloud.

## 5. Diseño de interfaces
- Dante trabaja algunas interfaces en **Pencil** (archivos `.pen`, mediante su servidor MCP con el asistente). El diseño se traduce a Compose reutilizando `ui/theme`, `ui/components` y la navegación existente; las decisiones de interfaz vigentes están en [DECISIONES § Interfaz](DECISIONES.md#interfaz).
- **Pencil antes de Compose (desde el 15-11-2026, para las historias que coordina el responsable de Food, empezando por HU21-B/HU22).** Cuando un cambio requiere una decisión de diseño (pantalla nueva, estado nuevo o cambio visual relevante):
  1. Revisar los diseños y componentes existentes y comprobar si ya hay una pantalla equivalente.
  2. Proponer los cambios respetando el sistema visual.
  3. Diseñar o modificar en Pencil **solo con autorización**, en frames nuevos con prefijo de historia, sin sobrescribir los aprobados.
  4. Presentar las pantallas y estados al responsable de Food, que los revisa (también con ChatGPT).
  5. Implementar en Compose **solo después de la aprobación visual y funcional**.
  6. Verificar que la app real corresponde al diseño aprobado.

  - Si una pantalla existente ya satisface los requisitos, se reutiliza sin rediseñarla.
  - Los cambios exclusivamente de backend, persistencia o pruebas no requieren Pencil.
  - Los ajustes menores sin decisión de diseño (textos, espaciados, correcciones) se hacen dentro del alcance aprobado, con los componentes existentes.
- *Coherencia:* esta regla concreta la de [DECISIONES § Interfaz](DECISIONES.md#interfaz) («Pencil primero»). La guía decía antes «Pencil no es obligatorio»; esa frase queda sustituida. Si otro integrante diseña una historia propia fuera de Pencil, debe acordarlo con el responsable de Food antes de implementar.
- **Arquitectura:** todo diseño aprobado se implementa respetando [ARCHITECTURE](ARCHITECTURE.md):
  - feature-first y MVVM, con dirección Presentation → Domain ← Data;
  - Domain sin SDKs ni infraestructura;
  - casos de uso, interfaces, módulos o carpetas nuevos solo cuando aportan una responsabilidad real;
  - sin abstracciones anticipadas;
  - reutilizar `ui/components` y los servicios existentes.
- Decisión aprobada DEC-HU34-29: **se conserva la interfaz actual del cliente** (Inicio, Buscar, Pedidos, Cuenta, catálogo, carrito, direcciones y Checkout); cambiarla requiere decisión explícita.
- Si cambias una interfaz de forma relevante, anota en el PR (o en el estado) qué cambió y por qué. No hace falta documentar cada ajuste visual.
- Comprueba estados de carga/error, textos largos, tamaños táctiles y accesibilidad.

## 6. Herramientas y matriz de verificación

### 6.1 Cuándo usar cada herramienta
Esta sección indica **cuándo** conviene cada herramienta; no afirma que estén instaladas. Antes de usar una, compruébalo en tu equipo.

| Herramienta | Cuándo usarla |
|---|---|
| Claude Code, modo Plan | Analizar una HU, investigar código, comparar alternativas y proponer. No edita |
| Claude Code, modo Manual | Aplicar cambios **autorizados**, revisando cada edición y comando |
| Pencil (MCP) | Diseñar y aprobar interfaces antes de Compose (§5). Los `.pen` se leen solo por MCP |
| Firebase MCP | Consultas de **solo lectura** autorizadas a Cloud (funciones, reglas, entorno). Nunca para escribir datos ni desplegar sin autorización |
| Revisión de código y de seguridad del asistente | Antes de pedir un commit con lógica de autorización, pagos o integración |
| Android CLI / QA de emulador | Recorridos instrumentados o capturas en AVD, cuando estén instalados y verificados |
| Kotlin LSP | Navegación y diagnósticos de Kotlin, si es compatible con el proyecto |
| Skills propias (futuras) | Análisis de HU, implementación, verificación, contratos y diseño. No existen todavía |

### 6.2 Verificación según el tipo de cambio
Informa siempre qué se ejecutó realmente, con su resultado, y también los fallos, las omisiones y las limitaciones. Compilar no equivale a validar.

| Tipo de cambio | Verificación mínima | Evidencia adicional cuando aplique |
|---|---|---|
| Kotlin / Compose | `:app:testDebugUnitTest`, `assembleDebug` del modo activo, previews con datos ficticios | Instrumentadas **ejecutadas** (no solo compiladas); revisión visual en AVD o teléfono |
| Interfaces de usuario | Diseño aprobado en Pencil (§5); estados de carga, error, vacío y textos largos | Comparación entre Pencil y la app real; accesibilidad y tamaños táctiles |
| Cloud Functions | `npm --prefix functions run build`, `npm --prefix functions test`, emulador en secuencia (§2.2) | `verify:package` antes de publicar; comparar lo desplegado con el commit |
| Contratos REST | Pruebas HTTP en el emulador con cuerpos válidos, inválidos (claves extra o faltantes) y errores esperados | Ejemplos del contrato reproducidos en las pruebas; compatibilidad con los consumidores existentes |
| Autorización y seguridad | Casos positivos y negativos por rol (responsable de otro local, propietario, comprador, administrador, sin sesión) | Revisión de seguridad; ningún secreto ni PIN en logs ni en historial |
| Firestore (reglas, índices, esquema) | Pruebas de reglas en el emulador; índices en `firestore.indexes.json` | Comparar con lo publicado antes de desplegar (§2.4); compatibilidad con datos antiguos sin migraciones destructivas |
| Integración con Rider | Emulador: idempotencia, secuencia, asignación vigente, concurrencia, reintentos y respuesta perdida | Prueba conjunta con pedidos de prueba identificados por ambos equipos ([API_CHASKI_RIDER §12](API_CHASKI_RIDER.md)) |

### 6.3 HeiGIT: pruebas deterministas y pruebas reales
- **LOCAL determinista** (reglas de negocio y regresiones):
  - fixtures de rutas con `CHASKI_ROUTE_FIXTURE=enabled`;
  - `functions/.secret.local` con un valor **ficticio** (§2.1) cuando el fixture no necesita una conexión real;
  - sin depender de servicios externos.
- **Integración real con HeiGIT** (requiere configuración segura y **autorización previa** en cada ejecución):
  - solicitud real de ruta y distancia con una credencial válida que nunca se copia al repositorio ni a los registros;
  - comprobación de la tarifa a partir de la respuesta y de la coherencia entre la cotización y el pedido creado;
  - evidencia sin credenciales ni datos personales;
  - distinguir los fallos del proveedor de los de Food.

Nunca copies el secreto real para que pase una prueba LOCAL.

**Estado (15-11-2026, D1):** con el fixture de rutas, el secreto ficticio y las pruebas actualizadas a la tarifa v2, la batería completa da 197/197. En Windows con Node 24 se observaron caídas intermitentes de `node.exe` (`0xC0000409`) al lanzar procesos hijos. Si un archivo falla sin aserción, repítelo aislado antes de diagnosticar un defecto. La guía recomienda Node 22.

**Gestor de paquetes:** `functions/package.json` declara `packageManager: npm@11.12.1` y existe solo `package-lock.json`. Las menciones a pnpm en auditorías antiguas son históricas.
