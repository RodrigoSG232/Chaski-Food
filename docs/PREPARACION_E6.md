# Preparación de E6 — integración real Food ↔ Rider

> Documento operativo de E5.6 (15-11-2026). Reúne en un solo lugar el checklist, el rollback, el IAM, los índices y las decisiones abiertas que E6 necesita. Así se evita repartir pasos de despliegue entre ESTADO, DECISIONES y GUIA, que solo enlazan aquí.
>
> **Etiquetas:**
> - **[LOCAL]:** verificado en emuladores.
> - **[CLOUD]:** verificado en Firebase real.
> - **[APROBADO]:** decidido por el responsable.
> - **[PENDIENTE]:** falta hacerlo o decidirlo.
> - **[REQUIERE AUTORIZACIÓN]:** necesita permiso explícito antes de ejecutarse.
>
> **Ninguna verificación de Cloud de este documento se ha ejecutado.**

## 1. Estado de partida
- **[LOCAL]** Commits `630b633..5edaf91` en `ramaDante`, sin push.
- **[LOCAL] Regresiones de E5.5 sobre `5edaf91`:**
  - Functions 184/184;
  - emulador completo en secuencia: ver ESTADO (incluye `riderEndToEnd.emulator.ts`);
  - Android JVM 680/680;
  - `assembleDebug` LOCAL correcto;
  - androidTest **compilado, no ejecutado**.
- **[CLOUD] Publicado hoy:**
  - `riderapi-00001-xeg` (contrato anterior a E1);
  - foodApi anterior a E2–E5;
  - índice `status+readyAt+__name__` **no desplegado**.
- **Entorno:** el proyecto declara Node 22 y la máquina tiene Node 24.15 (fallos intermitentes `0xC0000409`). **[PENDIENTE]** Instalar Node 22 para desplegar, con autorización.

## 2. Orden seguro de despliegue — checklist
Cada paso es **[REQUIERE AUTORIZACIÓN]** y se marca solo con evidencia.

| # | Paso | Verificación | Si falla |
|---|---|---|---|
| 1 | Consultas de solo lectura en Cloud (§4 y §5) | Resultados anotados en ESTADO | NO-GO |
| 2 | Revisar pedidos históricos, índices publicados (y exenciones) e invocadores de IAM | Ningún pedido incompatible en el local del piloto; matriz §5 = realidad | Excluir ese local o decidir una migración |
| 3 | Desplegar **solo** el índice `status+readyAt+__name__` | Estado `READY` en la consola o el CLI | No continuar: sin índice el listado da `FAILED_PRECONDITION` |
| 4 | Publicar el código en GitHub (push/PR autorizado) | La rama coincide con `5edaf91`+ | — |
| 5 | Instalar el APK nuevo en **todos** los dispositivos | Versión instalada anotada por participante | No activar el piloto |
| 6 | Desplegar `foodApi` y `riderApi` con `CHASKI_RIDER_ACTIVATION` **sin definir** (DISABLED) | ACTIVE; foodApi sin token → 401; riderApi sin invocador → 403; listado de Rider vacío | Rollback §3 |
| 7 | Rider despliega su versión compatible (§ resumen del contrato) | Confirmación escrita de Rider | No activar |
| 8 | Verificación de contrato y permisos con un pedido de prueba **fuera** del piloto | Detalle 404 con DISABLED; los permisos coinciden con §5 | Corregir antes de activar |
| 9 | `CHASKI_RIDER_ACTIVATION=pilot:<storeId>` (un local) | El listado muestra solo ese local | Volver a DISABLED |
| 10 | Pruebas conjuntas ([PRUEBAS_E6](PRUEBAS_E6.md)) | Criterios de aceptación | Criterios de suspensión |

**Compatibilidad revisada [LOCAL]:**
- **APK nuevo con el backend publicado:** funciona. Los campos nuevos son opcionales y `pickupCode` se ignora. La verificación de PIN, las incidencias y las revisiones de cobro responden 404, que se muestra como «no disponible» sin cerrar la app. Por eso el paso 5 va **antes** del 6.
- **APK antiguo con el backend nuevo:** el responsable deja de ver el PIN (E5.2, sin compatibilidad hacia atrás) **[APROBADO]**.
- **`newOrderNotifier`:** su código no cambió en el rango (en `index.ts` solo se añadió la activación a foodApi y riderApi). **No se despliega.**

## 3. Rollback
Hay dos acciones distintas y no deben confundirse:

1. **Detener nuevas asignaciones (pausa operativa):** quitar la variable `CHASKI_RIDER_ACTIVATION` o vaciarla, y redesplegar o actualizar la variable.
   - Las entregas ya iniciadas pueden cerrarse y conciliarse (§7).
   - No requiere cambiar de versión. **Es la primera respuesta ante cualquier incidencia que no sea de seguridad.**
2. **Deshacer un despliegue:**
   - **Solo con autorización y tras evaluarlo.** No hay ninguna revisión anterior que sea segura por defecto: `riderapi-00001-xeg` y el foodApi anterior **reintroducen** la exposición del PIN, la falta de verificación de recojo y las simulaciones sin bloqueo.
   - Los datos nuevos (`pickupSecurity`, `collectionReview`, `paymentCollection.source=RECONCILED`) son aditivos, pero la versión antigua no sabe tratarlos: los pedidos con revisión abierta quedarían sin forma de conciliarse.
   - **Antes de volver atrás:**
     - anotar las revisiones de Cloud Run (`gcloud run revisions list`);
     - listar los pedidos activos con Rider y las revisiones `OPEN` o `PENDING_REFUND`;
     - preferir **corregir hacia adelante** con un cambio acotado.
3. **Índice:** no se retira (es inocuo).
4. **APK:** no se puede volver atrás sin perder E3; se mantiene.

## 4. Firestore: índices e históricos
- **Consulta de Rider [LOCAL]** (`riderIntegrationService.available`):
  - `status == READY_FOR_PICKUP`, `readyAt >= Timestamp(0)`, `orderBy(readyAt ASC)`, `orderBy(__name__ ASC)`;
  - cursor opaco `{v:1,s,n,id}` sobre el último documento **examinado**;
  - índice compuesto `food_orders: status ASC, readyAt ASC, __name__ ASC` (`firestore.indexes.json`).
- **Pedidos sin `readyAt`:** no aparecen en el listado. Solo se alcanzan por el detalle, si Rider conoce el id.
- **Pedidos sin `paymentStatus`:** la proyección del responsable lo tolera, pero `riderPaymentEligibility` los descarta (`ORDER_NOT_READY`): ni se ofrecen ni se pueden asignar.
- **Conciliación:** usa igualdad sobre un solo campo (`collectionReview.status`, `collectionReview.resolution.excessStatus`), servida por índices automáticos. Hay que verificar que **no** existan exenciones de índice para `collectionReview`.
- **Consultas preparadas para el paso 1** (solo lectura, Admin SDK o consola; **no ejecutadas**):
  1. `food_orders where status == "READY_FOR_PICKUP"` → contar por `storeId` los que no tienen `readyAt` de tipo Timestamp, o no tienen `paymentStatus`, `pickupCode` o `pickupSnapshot`, `dropoffSnapshot` válidos.
  2. `food_orders where status in ["PREPARING","IN_DELIVERY"]` del local candidato → pedidos en curso que entrarían en el piloto.
  3. Índices y exenciones publicados: `firebase firestore:indexes --project chaski-food-9adba`, comparando con el archivo.
- **Regla [APROBADO]:** un local con pedidos `READY_FOR_PICKUP` incompatibles **no entra** en el piloto hasta resolverlos. **No se migra nada automáticamente.**

## 5. Matriz IAM esperada

| Recurso | Principal | Permiso | Estado esperado | Nota |
|---|---|---|---|---|
| riderApi (Cloud Run, `invoker:"private"`) | Cuenta de servicio del backend de Rider | `roles/run.invoker` sobre **ese** servicio | Sí | El ID token de Google con **audiencia = URL del servicio riderApi** |
| riderApi | Cuenta personal usada en D1 | `run.invoker` | **No** antes del piloto | Retirar **solo** el invocador, **después** de verificar que funciona la cuenta de Rider; conserva sus roles de administración del proyecto |
| riderApi | `allUsers` / `allAuthenticatedUsers` | invoker | **Nunca** | La app no comprueba identidad: cualquier invocador puede actuar como Rider |
| foodApi (`invoker:"public"`) | `allUsers` | invoker | Sí | La autorización real es el ID token de Firebase, comprobado en cada ruta |
| Despliegue | Cuenta del responsable | Roles de Firebase y Cloud Functions | Sí | Separados de la invocación |
| Secretos | `HEIGIT_API_KEY` | Secret Manager | Sin cambios | No se toca en E6 |

**[G7-A, código]** `riderApi` declara `invoker: RIDER_INVOKER` (`functions/src/integration/riderActivation.ts`). Cada despliegue deja el enlace `roles/run.invoker` **directo** del servicio exactamente con esa cuenta de servicio. Con `"private"`, firebase-tools 15.31 vaciaba el enlace (causa de la pérdida en G5).
- **Política directa del servicio:** solo la cuenta de Rider.
- **Permisos heredados del proyecto (sin cambios; G7-B):** `run.invoker` para la cuenta de cómputo por defecto, de la que **depende** el trigger de Eventarc de `newOrderNotifier`. No se puede retirar sin conceder antes un invocador en ese servicio.
- **Privilegios de administración:** `roles/owner` (4 usuarios), que también permite invocar.

**Rider no es el único principal con acceso efectivo** mientras existan los permisos heredados.

**[G7-B, código]** `riderApi` verifica criptográficamente al llamador (`functions/src/integration/riderCaller.ts`, `google-auth-library` 11.3.4 fijada):
- firma de Google, expiración, emisor `accounts.google.com`;
- audiencia exacta `RIDER_AUDIENCE`;
- `email = RIDER_INVOKER` y `email_verified`;
- rechazo de `X-Serverless-Authorization` (Cloud Run lo usa para IAM y le quita la firma).

En CLOUD se aplica antes de cualquier lógica. Solo se omite cuando `resolveRuntimeEnvironment` determina LOCAL (señales del emulador, nunca cabeceras). Con esto, `roles/editor` y `roles/owner` **pueden pasar IAM pero no la aplicación**.

**[CLOUD 16-11]** Desplegado en `riderapi-00004-moq`; invocador directo = solo Rider. La verificación de la aplicación **todavía no recibió ninguna petición real**: está pendiente de Cloud Shell y de G6.

**Alternativa B (futura, antes de GENERAL):** identidades dedicadas de ejecución y de Eventarc, y reducción de `roles/editor` de la cuenta de cómputo. **Descartado:** retirar solo el `run.invoker` de proyecto (editor ya incluye `run.routes.invoke`).

## 6. Operaciones Cloud preparadas (no ejecutadas)
Desde la raíz, con Node 22 y `functions/node_modules/firebase-tools`. **No ejecutar `--dry-run` en E5.6**: puede habilitar o consultar APIs del proyecto.

| Operación | Comando propuesto | Permisos | Riesgo | Verificación posterior |
|---|---|---|---|---|
| Índice | `firebase deploy --only firestore:indexes --project chaski-food-9adba` (sin `--force`) | Editor de Firestore | Bajo; el CLI pregunta antes de borrar índices ausentes del archivo | `READY` en la consola |
| Funciones | `firebase deploy --only "functions:food-local:foodApi,functions:food-local:riderApi" --project chaski-food-9adba` | Desarrollador de Cloud Functions y cuenta de servicio | Medio (cambio de contrato) | ACTIVE, 401/403, listado vacío |
| Activación | Variable `CHASKI_RIDER_ACTIVATION` en el entorno de Functions (mecanismo `.env.<proyecto>` o consola; **[PENDIENTE]** confirmar cuál se usa) | Desarrollador de Cloud Functions | Un valor inválido → 503 (falla cerrado) | Listado según el ámbito |
| IAM | `gcloud run services remove-iam-policy-binding riderapi --member=user:<cuenta> --role=roles/run.invoker --region=southamerica-west1` | Administrador de Cloud Run | Bajo si antes se verificó la cuenta de Rider | Llamada personal → 403 |

## 7. Pausa operativa frente a suspensión de emergencia
**Qué hace hoy `CHASKI_RIDER_ACTIVATION` vacía (DISABLED) [LOCAL, probado en E5.3]:**
- **Listado:** vacío.
- **Detalle, eventos y tracking:** `404` para los pedidos que **no** iniciaron el flujo de Rider; **siguen funcionando** para los que tienen `riderIntegration`.
- **`ASSIGNMENT_ACCEPTED` fuera del ámbito:** `409 ORDER_NOT_READY`, también tras una liberación.
- **Simulaciones de Food:** siguen bloqueadas para los pedidos gestionados por Rider.
- **foodApi** (verificación de PIN, desbloqueo, conciliación): sin cambios.

**Conclusión:**
- **A. Pausa operativa:** ya existe. Es DISABLED.
- **B. Suspensión de emergencia:** **no existe.** Una vulnerabilidad en el flujo de eventos seguiría siendo explotable para los pedidos en curso.

**Opciones para B [PENDIENTE, decisión de seguridad]:**

| Opción | Qué bloquea | Qué sigue | Reactivación | Evidencia | Coste |
|---|---|---|---|---|---|
| B1. Retirar el `run.invoker` de Rider (procedimiento) | **Todo** riderApi | foodApi completo (PIN, conciliación de lo ya cerrado) | Volver a conceder el rol | Registros de auditoría de IAM | Sin código; minutos; requiere un administrador de IAM |
| B2. Bandera `CHASKI_RIDER_EMERGENCY_STOP=on` | Eventos y tracking → 503 retryable; detalle y listado opcionalmente 503 | foodApi; las revisiones `OPEN` se concilian con normalidad | Quitar la variable y redesplegar | Registro de la función, más un estado visible | Código pequeño más pruebas de emulador |

**Recomendación:**
- Para la **primera entrega**, B1 como procedimiento documentado: no requiere código y es inmediato si la persona con permisos está disponible.
- B2 solo si se aprueba explícitamente. Su diseño: comprobarla en `riderApiHandler` antes de cualquier servicio; 503 `RIDER_API_SUSPENDED` retryable, para que Rider conserve su cola duradera y reenvíe los mismos `eventId` al reactivar.
- **Pruebas de B2:** sin la variable no cambia nada; con `on` todo devuelve 503 sin escrituras; al reactivar, los reintentos con los mismos `eventId` se aplican una sola vez.

**Entregas físicas en curso durante B:** el repartidor completa la entrega física y Rider guarda los eventos en su cola. Tras reactivar, se reenvían en orden; si el dinero no coincide, se abre la revisión normal de E5.4. **Coordinar con Rider** que su cola resiste horas de 503 o 403.

## 8. Entrega fallida con dinero — procedimiento manual [APROBADO para la primera entrega]
- **Contrato vigente (sin cambios):** `COMPLETED_UNSUCCESSFULLY` con `collection` distinto de `null` → **422**.
- **No existe registro administrativo integrado** de este caso **[PENDIENTE]**.

1. **Cuándo:** Rider lo reporta **en el momento**, cuando la entrega no se completa y el repartidor recibió dinero, total o parcial.
2. **Quién recibe el reporte:** el responsable de Food del piloto, por el canal acordado con Rider **[PENDIENTE]**.
3. **Datos mínimos:**
   - id corto del pedido;
   - fecha y hora;
   - método (efectivo, Yape o Plin);
   - importe;
   - motivo del fallo;
   - quién tiene el dinero.

   Sin teléfonos, sin números de cuenta y sin capturas con datos personales.
4. **Gestión:** la devolución o regularización se hace **fuera de la aplicación**, entre Rider y el comprador o el local. Se anota en el registro del piloto (documento compartido, no Firestore).
5. **En Chaski:** Rider envía `COMPLETED_UNSUCCESSFULLY` con `collection: null` (el pedido queda «Entrega no exitosa», sin cobro). **Nunca** se declara el dinero ahí.
6. **Prohibido:**
   - editar `food_orders` u otros documentos a mano;
   - marcar `PAID`;
   - abrir una revisión ficticia;
   - usar la conciliación de E5.4 para este caso.

## 9. Revisión de código y seguridad `630b633..5edaf91` (E5.5)

| Severidad | Hallazgo | Estado |
|---|---|---|
| Alta (configuración) | riderApi no comprueba la identidad del llamador en la aplicación: depende de que solo la cuenta de Rider tenga `run.invoker` (§5) | Mitigar con IAM antes del piloto; no hace falta código |
| Media | No hay suspensión de emergencia para los pedidos en curso (§7) | Decisión pendiente |
| Media | Entrega fallida con dinero sin registro integrado (§8) | Procedimiento manual aprobado |
| Baja | Android registra HTTP en nivel `BASIC` también en release (URLs con `orderId`, sin cabeceras ni cuerpos). Es anterior al rango | Sugerencia: limitarlo a debug (sin autorización no se toca) |
| Baja | El comentario de `StoreOrdersApi.kt` dice que foodApi «todavía envía `pickupCode`»; ya no lo hace (E5.2) | Corrección documental menor, no aplicada |
| Info | Listado de conciliación limitado a 50 por tipo, sin paginación | Necesidad registrada |

**Puntos sin hallazgos** (cubiertos por el código y las pruebas):
- PIN solo en el detalle con la asignación vigente;
- versión de la asignación y secuencia de eventos;
- idempotencia por `eventId`, `operationId` y hash;
- cobro atómico en la misma transacción;
- conciliación solo para administradores;
- simulaciones bloqueadas;
- activación que falla cerrada;
- históricos tolerados;
- reintentos de Android con el mismo `operationId`.

## 10. GO / NO-GO para activar el piloto
**GO solo si se cumplen todas:**
- [ ] Pasos 1–8 con evidencia.
- [ ] Índice `READY`.
- [ ] IAM igual a §5 (sin la cuenta personal ni `allUsers`).
- [ ] APK nuevo en todos los dispositivos.
- [ ] Rider confirma por escrito la compatibilidad con el resumen del contrato.
- [ ] Local del piloto sin pedidos incompatibles.
- [ ] Procedimientos §7 (B1) y §8 conocidos por el responsable y por Rider.
- [ ] Persona con permisos de IAM disponible durante las pruebas.

**NO-GO ante cualquiera de estos:**
- índice no `READY`;
- invocador público o desconocido;
- APK antiguo en un participante;
- Rider sin confirmar `collection` o `PICKUP_*`;
- pedidos incompatibles en el local;
- regresiones LOCAL en rojo.

## 11. Procedimiento G5 — Functions con la integración desactivada (preparado 16-11, no ejecutado)
- **Alcance:** solo `foodApi` y `riderApi` (codebase `food-local`). `newOrderNotifier` **se excluye**: su código no cambió desde la versión publicada.
- **Integración desactivada:**
  - no hay `functions/.env.chaski-food-9adba` y Cloud no tiene `CHASKI_RIDER_ACTIVATION` [CLOUD, G1];
  - `parseRiderActivation(undefined)` → `DISABLED`: el listado viene vacío, el detalle y los eventos responden 404 (no hay pedidos con `riderIntegration`);
  - las simulaciones de Food siguen disponibles fuera del ámbito de Rider.

  **No crear archivos `.env` de producción.**
- **Dependencias:**
  - `package-lock.json` (desde `de669af`) fija `firebase-admin 14.5.0` (motor `>=22`);
  - `@firebase/database*`, transitiva, declara `>=24.12`;
  - el código **no** importa `firebase-admin/database`, así que no se carga al ejecutar;
  - no hay `.npmrc` con `engine-strict`, así que `npm ci` lo trata como aviso (`EBADENGINE`).

  Probado con Node 22 en LOCAL: build, 184/184 y emulador. **Riesgo bajo pero no verificado en Cloud Build** hasta G5.
- **`predeploy`:** `npm run build` y `npm run verify:package` (el `ignore` excluye las pruebas, `.env*`, `.secret*` y las credenciales).

| # | Paso | Tipo | Comando o verificación |
|---|---|---|---|
| 1 | Versiones publicadas | LECTURA | Revisión y estado de foodApi y riderApi (API de Cloud Functions v2 o Cloud Run); hoy son `foodapi-00012-net` y `riderapi-00001-xeg` |
| 2 | Respaldo | LECTURA | Anotar las revisiones, variables (solo nombres), secretos (solo nombres), IAM de los servicios y `firestore.rules` publicadas |
| 3 | Entorno | LOCAL | Node 22 en la sesión, árbol limpio, commit publicado (`origin/ramaDante`) |
| 4 | Despliegue | **MODIFICA CLOUD** | `node functions/node_modules/firebase-tools/lib/bin/firebase.js deploy --only "functions:food-local:foodApi,functions:food-local:riderApi" --project chaski-food-9adba` (sin `--force`; si el CLI propone **eliminar** funciones, detenerse) |
| 5 | Estado | LECTURA | Las dos funciones ACTIVE, `nodejs22`, revisión nueva con el 100 % del tráfico |
| 6 | Autenticación | LECTURA/HTTP | foodApi sin token → 401; riderApi sin token o con una cuenta no autorizada → 403 (Cloud Run) |
| 7 | Integración desactivada | HTTP de solo lectura (cuenta de Rider o propietario autorizado) | `GET /integration/v1/orders` → `{"orders":[],"nextCursor":null}`; detalle de un pedido → 404 |
| 8 | Android | Manual (responsable) | El APK `1.1.0-e6` abre pedidos, la bandeja y el panel; la verificación de PIN y las revisiones responden |
| 9 | Registros | LECTURA | Sin 5xx nuevos en ambas funciones |

**Recuperación:**
- ante un error de despliegue, Cloud Functions conserva la revisión anterior;
- ante un fallo funcional, **corregir hacia adelante**. Volver a `riderapi-00001-xeg` o `foodapi-00012-net` reintroduce el PIN expuesto, solo con autorización (§3);
- los datos no cambian en G5.

**Secuencia acordada después de G5:**
1. verificar seguridad y funcionamiento (G7 IAM);
2. coordinar con Ángel (G6);
3. confirmación de compatibilidad;
4. activar el piloto (G8);
5. pruebas conjuntas (G9).

## 12. Estado de G7-B y del APK (16-11)
- **A. LOCAL:** verificación del llamador probada (190/190, incluida criptografía RS256 real; emulador 71/71).
- **B. CLOUD:** `riderapi-00004-moq` ACTIVE (nodejs22, `southamerica-west1`):
  - IAM directo solo de Rider;
  - herencia de proyecto sin cambios (editor y `run.invoker` de la cuenta de cómputo; owner);
  - DISABLED por configuración;
  - registros sin ERROR.
- **C. NO PROBADO:** la respuesta autenticada de la aplicación (`CALLER_NOT_ALLOWED`, el 200 vacío y el 404) → G6 §5 de CAMBIOS_CONTRATO_RIDER_E6.
- **APK `1.1.0-e6` sigue vigente:** sin cambios en `app/` ni en Gradle desde `9cd6336`; SHA-256 `fa53246785897da4adde8c0ae9691190a3cd338a16a7dfde7daf25098221f708`.
- **Instalación (cuando se autorice):**
  1. Comprobar el SHA-256 del archivo.
  2. Instalar por USB (`adb install -r`) o copiando el APK.
  3. Si Android indica **conflicto de firma** (el APK anterior se firmó con otra clave debug), **no desinstalar sin autorización**: desinstalar borra los datos locales de la app (sesión y caché; los pedidos están en Firestore y no se pierden).
  4. Comprobar en Ajustes → Aplicaciones → Chaski Food que la versión es `1.1.0-e6`.
