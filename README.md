# Chasqui Food: desarrollo local y Checkout HU15

Esta guía usa el backend `foodApi` de `functions/`. `checkoutApi` es otro despliegue y no forma parte de este paquete.

## Requisitos

- JDK 21, Android SDK con plataforma 37 y Android Emulator o un dispositivo con ADB.
- Node.js 22 y pnpm 11.25.0 (`node --version` y `pnpm --version`). El Firebase CLI está en las dependencias de desarrollo de `functions/`.
- Gradle Wrapper incluido en el repositorio; no hace falta instalar Gradle globalmente.

Desde `functions/`, `pnpm install --frozen-lockfile` instala las dependencias y `pnpm run build` genera `lib/`. El código de `src/`, las pruebas de `test/`, `package.json`, el lockfile, `pnpm-workspace.yaml` y `tsconfig.json` están versionados. `lib/`, `node_modules/` y `.secret.local` son locales. `firebase.json` define `source: functions`, `codebase: food-local` y los emuladores.

## Elegir Firebase para Debug

El plugin Google Services usa `app/src/debug/google-services.json` antes que `app/google-services.json`. Ambos nombres están ignorados por Git. La plantilla versionada `app/src/debug/google-services.demo.json` no se usa hasta copiarla con el nombre activo. Gradle comprueba el proyecto y el paquete antes de compilar Debug; nunca cambia un JSON personal.

- **LOCAL:** usa la plantilla demo en `app/src/debug/google-services.json` y compila con `-PchaskiFirebaseMode=LOCAL` (valor predeterminado). Si ya tienes un JSON activo, respáldalo fuera del repositorio antes de cambiarlo. En un clon nuevo sin archivo activo:

  ```powershell
  Copy-Item app/src/debug/google-services.demo.json app/src/debug/google-services.json
  ```

- **CONECTADO:** coloca en la misma ruta el JSON legítimo del proyecto `chaski-food-9adba`, para el paquete `com.chaskifood.app`. Solicítalo por el canal interno del equipo; no lo publiques en Git ni sustituyas el de otra persona. Necesitas una cuenta de prueba habilitada y datos de prueba autorizados en ese proyecto. Comprueba el modo al compilar: instalar un APK LOCAL con JSON real provoca un cierre al inicio.

Si existe también `app/google-services.json`, Debug prefiere el de `app/src/debug/`; cuando falta el de Debug, usa el de la raíz de `app/`. Conserva un solo archivo activo conocido para evitar confusiones. Release sigue con URLs no configuradas y no está preparado para distribución.

## LOCAL con Firebase Emulator

Desde `functions/`:

```powershell
pnpm install --frozen-lockfile
pnpm run build
$env:CHASKI_ROUTE_FIXTURE = 'enabled'
pnpm run emulators
```

El último comando usa **solo** `demo-chaski-hu15`: Auth en `127.0.0.1:9099`, Firestore en `127.0.0.1:8089` y Functions en `127.0.0.1:5001`. Antes de iniciarlo, crea `functions/.secret.local` con un valor **ficticio** `HEIGIT_API_KEY=local-fixture-only` si todavía no existe; nunca sobrescribas un secreto local de otra persona. La opción `CHASKI_ROUTE_FIXTURE=enabled` produce rutas SIMULATED sin llamar a HeiGIT. El archivo de secreto no se versiona. No utilices el proyecto Firebase real para estas pruebas.

En otra terminal, desde la raíz del repositorio:

```powershell
.\gradlew.bat assembleDebug -PchaskiFirebaseMode=LOCAL
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

En Android Emulator, `10.0.2.2` apunta al equipo que ejecuta los emuladores. En un teléfono físico, `127.0.0.1` apunta al propio teléfono; para usar los emuladores del equipo con ADB, ejecuta `adb reverse tcp:9099 tcp:9099`, `adb reverse tcp:8089 tcp:8089` y `adb reverse tcp:5001 tcp:5001`, y compila también con `-PchaskiEmulatorHost=127.0.0.1`. Estas redirecciones requieren que el teléfono siga conectado. El laboratorio HU15 no inicia Storage; las imágenes u otras funciones de Storage no forman parte de esta prueba.

Las pruebas de `functions/test/*.emulator.ts` y `CheckoutEmulatorFixture` crean datos ficticios para sus casos y los aíslan en el proyecto demo. No existe todavía un cargador de datos para recorrer manualmente toda la aplicación LOCAL: Checkout también necesita un carrito en Room y una dirección seleccionada, además de documentos y una cuenta ficticia. No reutilices datos reales para prepararlo.

## CONECTADO con `foodApi` publicada

Con el JSON Debug legítimo seleccionado, compila desde la raíz:

```powershell
.\gradlew.bat assembleDebug `
  -PchaskiFirebaseMode=CONECTADO `
  -PchaskiConnectedProjectId=chaski-food-9adba `
  -PchaskiCheckoutApiBaseUrl=https://foodapi-u65lcqarqa-tl.a.run.app/api/v1/
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

En este modo Auth y Firestore usan Firebase real, y Checkout envía el Firebase ID Token a los endpoints HTTPS de `foodApi`. No se necesita una clave HeiGIT en Android. Usa únicamente cuentas y datos de prueba autorizados; probar Checkout no crea pedidos. En un dispositivo físico no se necesita `adb reverse` para CONECTADO. Al alternar APKs LOCAL/CONECTADO con el mismo `applicationId`, `adb install -r` conserva los datos locales: verifica la sesión, dirección y carrito antes de probar; no borres datos de otra persona.

## Pruebas y nuevas rutas del backend

```powershell
cd functions
pnpm run build
pnpm test
# Con los tres emuladores LOCAL ya iniciados en otra terminal:
pnpm run test:emulator
```

Las pruebas JVM Android se ejecutan desde la raíz con `.\gradlew.bat :app:testDebugUnitTest` y las propiedades del modo elegido. Las instrumentadas requieren un dispositivo o Android Emulator; `FirebaseCheckoutRepositoryEmulatorTest` utiliza el proyecto demo y los tres emuladores. Las pruebas HTTP usan datos ficticios y no deben apuntar a producción.

Para agregar un endpoint, conserva `foodApi` como entrada HTTP en `functions/src/index.ts`, separa validación, política y acceso a datos en `functions/src/`, exige identidad cuando corresponda y añade pruebas en `functions/test/`. Compila y prueba con el proyecto demo antes de proponer un despliegue. El despliegue selectivo del **codebase `food-local`**, previa revisión y autorización del equipo, se prepara desde `functions/` con:

```powershell
pnpm exec firebase deploy --config ../firebase.json --only functions:food-local --project chaski-food-9adba
```

Ese comando es una referencia para una futura operación autorizada; no uses `--force` y revisa cualquier propuesta de eliminación del CLI. `checkoutApi` pertenece a otro codebase y no debe modificarse.
