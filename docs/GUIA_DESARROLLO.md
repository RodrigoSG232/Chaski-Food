# Guía de desarrollo de Chasqui Food

Cómo trabajamos en el proyecto, para personas y para asistentes de IA (Claude Code, Codex u otros). Cómo funciona el sistema: [ARCHITECTURE](ARCHITECTURE.md). Qué está hecho y qué falta: [ESTADO_IMPLEMENTACION](ESTADO_IMPLEMENTACION.md).

## 1. Requisitos
- JDK 21, Android SDK (plataforma 37), Android Studio, un AVD o teléfono con ADB.
- Node.js 22 y pnpm 11.25.0. El Firebase CLI viene en las dependencias de `functions/` (no hace falta instalarlo global).
- Comandos desde la **raíz del repo** en PowerShell, con el wrapper `.\gradlew.bat`.

## 2. Entornos de trabajo y comandos

Regla de oro: **todo cambio se prueba primero en LOCAL**. CONECTADO usa Firebase real con datos y cuentas de prueba autorizadas.

### 2.1 LOCAL (Firebase Emulator Suite)
1. JSON de Debug del proyecto demo (solo si no tienes ya un `app/src/debug/google-services.json` personal; respáldalo antes de cambiarlo):
   ```powershell
   Copy-Item app/src/debug/google-services.demo.json app/src/debug/google-services.json
   ```
2. Backend y emuladores (desde `functions/`). Crea antes `functions/.secret.local` con un valor **ficticio** `HEIGIT_API_KEY=local-fixture-only` si no existe:
   ```powershell
   pnpm install --frozen-lockfile
   pnpm run build
   $env:CHASKI_ROUTE_FIXTURE = 'enabled'
   pnpm run emulators        # Auth 9099, Firestore 8089, Functions 5001 en demo-chaski-hu15
   ```
   Se detiene con `Ctrl+C` en esa terminal.
3. App (otra terminal, desde la raíz):
   ```powershell
   .\gradlew.bat :app:assembleDebug -PchaskiFirebaseMode=LOCAL
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   En AVD el host es `10.0.2.2`. En teléfono físico: `adb reverse tcp:9099 tcp:9099`, `adb reverse tcp:8089 tcp:8089`, `adb reverse tcp:5001 tcp:5001` y compilar con `-PchaskiEmulatorHost=127.0.0.1`.
4. Datos: las pruebas automáticas crean sus propios datos ficticios. **No hay** un cargador de datos para recorrer toda la app manualmente en LOCAL; prepara solo datos ficticios y nunca copies datos reales.

### 2.2 Pruebas
| Qué | Comando |
|---|---|
| Unitarias backend | `pnpm --dir functions test` (tras `build`) |
| Backend + reglas en emulador (laboratorio limpio, secuencial) | desde `functions/`: `pnpm exec firebase emulators:exec --only auth,firestore,functions --project demo-chaski-hu15 --config ../firebase.json "node --test --test-concurrency=1 lib/test/*.emulator.js"` |
| Paquete de Functions (antes de publicar) | `pnpm --dir functions run verify:package` |
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
Nada en Cloud se modifica automáticamente: ni Functions, ni reglas, ni índices, ni datos, ni IAM, ni secretos. Procedimiento usado en HU16/HU34 (desde la raíz, con `node functions/node_modules/firebase-tools/lib/bin/firebase.js` o `pnpm --dir functions exec firebase`):
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
- Si algo en la documentación contradice al código, verificar en el código y corregir el documento.

## 4. Git y colaboración
- Una rama por historia o incremento; PR hacia `main` para integrar. No trabajar directamente sobre `main`.
- Commits en español con prefijo (`feat(HUxx - área): …`, `fix(área): …`, `docs: …`, `test(...)`). **Sin líneas de coautoría de asistentes de IA.**
- Nunca subir: `google-services.json`, `.secret.local`, `local.properties`, keystores, tokens, APKs, respaldos de Cloud.
- `docs/` está ignorada salvo los documentos definitivos (lista blanca en `.gitignore`); `docs/HU04_PLAN.md` y `contexto/` (fuentes originales del curso) quedan locales.
- Hacer push o merge **no despliega** Cloud.

## 5. Diseño de interfaces
- Dante trabaja algunas interfaces en **Pencil** (archivos `.pen`, mediante su servidor MCP con el asistente). El diseño se traduce a Compose reutilizando `ui/theme`, `ui/components` y la navegación existente; las decisiones de interfaz vigentes están en [DECISIONES § Interfaz](DECISIONES.md#interfaz).
- **Pencil no es obligatorio**: cualquier integrante puede diseñar en Android Studio o con sus herramientas, respetando Material 3, `ChaskiTheme`, los componentes compartidos y los criterios de UX vigentes.
- Decisión aprobada DEC-HU34-29: **se conserva la interfaz actual del cliente** (Inicio, Buscar, Pedidos, Cuenta, catálogo, carrito, direcciones y Checkout); cambiarla requiere decisión explícita.
- Si cambias una interfaz de forma relevante, anota en el PR (o en el estado) qué cambió y por qué. No hace falta documentar cada ajuste visual.
- Comprueba estados de carga/error, textos largos, tamaños táctiles y accesibilidad.
