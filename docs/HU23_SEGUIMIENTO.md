# HU23 · Consultar seguimiento de la entrega

Implementación local en `feat/hu23-seguimiento`, basada en `main` `722764f` (HU22). No se hicieron commits, despliegues ni cambios Cloud durante esta implementación.

## Comportamiento

- En **Pedidos → detalle**, muestra el estado logístico y el nombre del repartidor cuando existe.
- Sin asignación o antes de `PICKED_UP`, no activa el mapa ni muestra coordenadas/ETA.
- Durante `IN_DELIVERY`, muestra la última ubicación enviada por la asignación vigente, hora de captura, precisión y ETA opcional de Rider.
- La ausencia de muestras conserva estado y ubicación. Después de 120 segundos advierte que la ubicación no está actualizada y oculta el ETA. Un fallo HTTP transitorio conserva lo último y ofrece reintentar.
- Consulta cada 10 segundos mientras la pantalla está activa; ante fallos espera 20/30 segundos. Se detiene en estados finales, al salir de pantalla o al cerrar/cambiar sesión.
- Un pedido ajeno y uno inexistente devuelven el mismo 404. El móvil borra las coordenadas al perder autorización.
- Solo una transición válida desde `IN_DELIVERY` completa el pedido. `COMPLETED_SUCCESSFULLY` significa «Entregado»; `COMPLETED_UNSUCCESSFULLY`, «Entrega no exitosa». Los históricos sin resultado muestran «Finalizado».
- El mapa reutiliza MapLibre/OpenFreeMap y necesita internet para cargar sus teselas. Si falla, la última coordenada sigue visible en texto. No requiere permiso de ubicación del teléfono.

## Lo que falta recibir de Rider

1. Confirmación del campo opcional propuesto `estimatedArrivalAt` (hora UTC ISO con milisegundos), semántica y frecuencia de actualización. Food no calcula ni inventa un ETA para pedidos reales.
2. Confirmación de los eventos ya publicados en el contrato: el equivalente de DELIVERED es `COMPLETED_SUCCESSFULLY`; secuencias, asignación y reintentos deben respetar ese contrato.
3. Identidad de servicio definitiva y prueba entre backends en el entorno acordado. Esta implementación no acredita IAM ni interoperabilidad real.

El umbral de muestra antigua (120 s), tolerancia de reloj futuro (60 s) y límite de ETA (24 h desde la muestra) son provisionales. Revisarlos con Rider. Contrato: [API_CHASKI_RIDER.md](API_CHASKI_RIDER.md).

La diferencia entre código de recojo en Food y PIN de entrega propuesto en los documentos Rider sigue fuera de esta HU. Tampoco se implementa chat.

## Simulación exclusivamente LOCAL

Usa los emuladores y APK LOCAL de la [guía](GUIA_DESARROLLO.md#21-local-firebase-emulator-suite). No reemplaces archivos personales de configuración. El script rechaza cualquier proyecto/host distinto de `demo-chaski-hu15`, Auth `127.0.0.1:9099` y Firestore `127.0.0.1:8089`. Solo invoca la función en `127.0.0.1:5001`; no acepta direcciones, UID o pedidos arbitrarios.

Desde `functions/`, con emuladores Auth, Firestore y Functions encendidos:

```powershell
node scripts/build.cjs
$env:GCLOUD_PROJECT = 'demo-chaski-hu15'
$env:FIRESTORE_EMULATOR_HOST = '127.0.0.1:8089'
$env:FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099'
# Solo si aún necesitas las identidades del laboratorio:
node lib/test/lab/seed.js base
node lib/test/lab/hu23Simulation.js prepare
```

`seed.js base` restablece las identidades y datos sintéticos del laboratorio existente; no lo ejecutes si deseas conservar ese escenario. `prepare` exige el comprador ya creado y restablece solamente el pedido HU23 y sus subcolecciones. Nunca toca pedidos reales.

Entrar al APK LOCAL con el comprador ficticio del laboratorio (`buyer@chaski.test`, contraseña `emulator-password`), abrir el pedido reciente «Local laboratorio» y ejecutar las acciones por separado para observar cada fase:

```powershell
node lib/test/lab/hu23Simulation.js assign
node lib/test/lab/hu23Simulation.js merchant
node lib/test/lab/hu23Simulation.js pickup
node lib/test/lab/hu23Simulation.js move
node lib/test/lab/hu23Simulation.js arrive
node lib/test/lab/hu23Simulation.js finish
```

- `move`: envía una coordenada ficticia y llegada estimada a 10 minutos; se identifica como simulación LOCAL.
- `no-eta`: envía una muestra del contrato anterior, sin ETA; debe aparecer «ETA no disponible».
- `stale`: envía una muestra capturada hace 3 minutos. Usarla como primera muestra después de `pickup`; si ya existe una más reciente se ignora, como debe ocurrir con paquetes retrasados.
- Para pérdida de ubicación, dejar de enviar muestras; al superar 120 segundos se conserva el mapa y se advierte antigüedad. Para pérdida de red, detener Functions temporalmente; no borrar Firestore.
- `release`: libera antes del recojo; después se puede volver a `assign` con versión nueva.
- `fail`: finaliza como entrega no exitosa desde `IN_DELIVERY`. Para otro recorrido, usar `prepare`.

El simulador escribe el pedido inicial ficticio; todas las acciones posteriores pasan por la API privada real del emulador y sus validaciones. Los archivos de `test/` no forman parte del paquete Cloud.

## Verificación

Las pruebas de HU23 cubren privacidad de la proyección, propiedad del pedido, compatibilidad sin ETA, secuencias/replay, posiciones antiguas y futuras, liberación/reasignación, finalización válida e inválida y recorrido del simulador LOCAL. Android añade pruebas de dominio, repositorio y ViewModel: frescura, autorización, cambio de cuenta con respuestas tardías, polling, pausa y recuperación.

También hay cinco pruebas instrumentadas de la tarjeta sin depender de red de mapas: ausencia de Rider, antes del recojo, ausencia de ubicación/ETA, entrega fallida y reintento. Compilarlas no equivale a ejecutarlas.

Para repetir backend:

```powershell
node scripts/build.cjs
node --test lib/test/*.test.js
$env:CHASKI_ROUTE_FIXTURE = 'enabled'
node node_modules/firebase-tools/lib/bin/firebase.js emulators:exec --only auth,firestore,functions --project demo-chaski-hu15 --config ../firebase.json 'node --test --test-concurrency=1 lib/test/buyerTrackingHttp.emulator.js lib/test/buyerOrdersHttp.emulator.js lib/test/storeOrdersHttp.emulator.js'
```

Desde la raíz:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
# Con dispositivo o AVD LOCAL conectado:
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.chaskifood.app.feature.orders.presentation.BuyerTrackingCardTest
```

Ejecución del 08-11-2026: backend 150 unitarias y 31 de integración aprobadas; CLI LOCAL probado con asignación, recojo, GPS/ETA, muestra sin ETA, finalización y rechazo de entorno no demo. Android: APK y androidTest compilados, pruebas JVM aprobadas. Las cinco instrumentadas **no se ejecutaron** porque ADB no detectó dispositivo/AVD. Queda pendiente la validación visual del mapa en dispositivo y la integración con Rider real. Backend ejecutado con Node 24.13.1; repetir con el runtime declarado Node 22 antes de publicar. Más contexto en [ESTADO_IMPLEMENTACION.md](ESTADO_IMPLEMENTACION.md).
