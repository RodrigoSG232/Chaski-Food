# HU34 — Acceder a la interfaz según permisos

> **Registro histórico de decisiones (HU34).** Conserva las decisiones DEC-HU34-01–29 y la cronología de implementación. El estado vigente, la arquitectura y los pendientes están en [ESTADO_IMPLEMENTACION](ESTADO_IMPLEMENTACION.md) y [ARCHITECTURE](ARCHITECTURE.md); ante cualquier diferencia prevalecen esos documentos y el código. HU34 se publicó y validó en Cloud el 31-10-2026; las menciones a rutas cerradas en Cloud, «docs ignorado» o `checkoutApi` son históricas.

Fecha: 2026-10-28 (America/Lima). Base: ramaDante, `1e9d45b9960b71f9a86510c962949d72a1ebd416`.
Etapa 2 documental y HU34-3A.1 implementado únicamente como dominio puro, sin integración ni autorización de despliegues. Identificador provisional hasta formalizar planificación.

Estados: **APROBADO** = acuerdo explícito de la solicitud completa de Etapa 2; **EXISTENTE** = comprobado en código; **PROPUESTO** = diseño sin aprobar; **PENDIENTE** = detalle/verificación por resolver; **FUERA DE ALCANCE** = otra etapa/historia. Las recomendaciones de auditoría no son automáticamente acuerdos. No se utiliza el mensaje expresamente retirado por el usuario.

## 1. Objetivo y alcance

**APROBADO.** Como usuario autenticado, quiero acceder a la experiencia correspondiente a mis permisos para utilizar las funcionalidades autorizadas de Chasqui Food.
Tres experiencias comerciales principales: cliente, propietario y responsable. Administrador Chaski es independiente. Reutilizar Firebase Authentication y UID; no crear otro sistema de cuentas. Diseño visual en Pencil antes de implementar.

## 2. Historias originales relacionadas y fuentes

- CSV de planificación original (local, `contexto/`): HU05–HU08 registro, aprobación, supervisión y asignaciones; HU09 horarios; HU10–HU11 catálogo/oferta; HU16–HU17 compras; HU19–HU21 operación; HU24 avisos; HU28 métricas. Los estados del CSV no acreditan ejecución.
- Caso de negocio (PDF local, `contexto/`), páginas 1–4: actores y procesos; las decisiones presentes refinan sus términos amplios de administración.
- Contexto y guía HU15 (integrados en [arquitectura](ARCHITECTURE.md) y [guía de desarrollo](GUIA_DESARROLLO.md)), [README](../README.md), [arquitectura](ARCHITECTURE.md), [AGENTS](../AGENTS.md).
- Auditoría Etapa 1 disponible en la conversación; no se inventa una ruta para ese informe.
- ARCHITECTURE y ESTADO_IMPLEMENTACION aún describen 39b1a135 y HU15 no integrada. Contrastar con código vigente; no se actualizan aquí. PLAN_PROYECTO referenciado por AGENTS no está disponible; no añadir un enlace roto.

## 3. Actores y experiencias

**APROBADO.** Cliente: compras, direcciones, carrito, Checkout y pedidos personales. Propietario: negocios, locales, responsables y facultades comerciales autorizadas; no operación individual de pedidos recibidos. Responsable: locales asignados, bandeja y avisos; acciones de preparación posteriores. Administrador Chaski conserva supervisión de plataforma sin adquirir automáticamente permisos de responsable.

## 4. Matriz de capacidades y permisos

| Capacidad | Cliente | Propietario | Responsable | Administrador Chaski |
|---|---|---|---|---|
| Iniciar nuevas compras (carrito/Checkout) | Sí | No, tampoco suspendido | No | No se deduce del claim admin; depende de experiencia comercial autorizada |
| Compras personales anteriores | Propias | Consulta y seguimiento de propias anteriores | Consulta y seguimiento de propias anteriores | Solo propias; sin acceso ajeno por claim admin |
| Registrar solicitud de negocio | Sí, según HU05 | Consulta/corrección según estado | No; otra cuenta Firebase independiente puede solicitar | Sin propiedad implícita |
| Administrar negocio/locales/responsables | No | Según propiedad y habilitación; suspendido sin listado ni gestión de locales | No | Supervisión, no propiedad |
| Estado y motivo de suspensión / futura apelación | No por propiedad ajena | Conserva experiencia restringida; estado y motivo; apelación futura HU35 | Sin facultad propietaria implícita | Supervisión HU07; evaluación de apelación futura |
| Horarios y oferta HU09/HU11 | No | Conserva autorizaciones actuales | Según asignación y habilitación | Sin permiso operativo implícito |
| Bandeja recibida y avisos individuales | No | No | Solo locales autorizados | No automáticamente |
| Aceptar/rechazar/preparar | No | No | Futuro HU19–HU21 | No automáticamente |
| Indicadores agregados | No | Futuro HU28 | Según alcance HU28 | Fuera de esta especialización |

Cuenta sin permisos comerciales confirmados: cliente. Carga, error y conflicto son estados distintos; la desconexión no confirma ausencia de permisos. Conflicto restringe operaciones comerciales protegidas sin borrar datos ni impedir consulta personal histórica autorizada independientemente. Dos cuentas de una persona no se vinculan automáticamente. El índice derivado coordina, pero no concede capacidades por sí mismo.

La interfaz no concede permisos. Compras personales y pedidos recibidos son accesos distintos. Solo la experiencia de cliente permite nuevas compras. El exresponsable sin asignaciones ni otra autorización comercial retorna a cliente y puede comprar. La suspensión conserva identidad propietaria y no habilita compras ni bandeja recibida.

## 5. Decisiones aprobadas

### DEC-HU34-01 — Conservación de compras anteriores

**APROBADO.** Cuando se aprueba el negocio y la cuenta pasa a propietario, conserva consulta y seguimiento de compras personales anteriores. No obtiene acceso a pedidos recibidos por sus locales. HU17 desarrollará el historial productivo; documentar esta garantía no lo implementa.

### DEC-HU34-02 — Una experiencia comercial principal por cuenta

**APROBADO.** Sin autorizaciones comerciales se comienza como cliente. Solicitud pendiente no convierte en propietario aprobado; la aprobación sí cambia a experiencia de propietario. Las asignaciones deben respetar exclusividad. Prevenir o resolver explícitamente combinaciones incompatibles, sin precedencia inventada. Administrador Chaski sigue independiente.
Propietario por ownerUid autenticado. Correo comercial es contacto, no identidad propietaria. Responsables mediante correo normalizado correspondiente al correo autenticado y verificado. Autorizar de forma segura en backend y reglas; no crear otro sistema de cuentas.
**PENDIENTE técnico.** Especificar protocolo concreto de exclusividad y concurrencia conforme a DEC-HU34-08–15; la dirección mediante foodApi y coordinación derivada ya está aprobada. No se ha aprobado nueva colección de roles ni claim comercial; DEC-HU34-08 aprueba un índice derivado de coordinación, con esquema físico pendiente.

### DEC-HU34-03 — Actualización y revocación dinámica

**APROBADO.** Actualizar experiencia cuando cambian permisos. Retirar consultas y datos de locales revocados. Los pedidos permanecen por storeId y un nuevo responsable autorizado puede consultar los existentes. Sin asignaciones ni otro rol comercial, retornar a cliente. Reautorizar operaciones en servidor. Cambiar permisos no exige cerrar Firebase Auth.
Desconectado no se garantiza recepción inmediata de revocación ni retirada de información ya vista. No usar permisos cacheados como autorización actual. Verificar al reconectar; cancelar listeners/solicitudes anteriores y evitar exposición entre cuentas. Política concreta de caché pendiente.

### DEC-HU34-04 — Exclusividad de autorizaciones comerciales

**APROBADO.** Para el MVP cada cuenta tiene una experiencia comercial principal: cliente, propietario o responsable. Una cuenta propietaria de negocio aprobado no puede ser responsable de otro negocio ni de sus propios locales. Un responsable puede estar asignado a varios locales autorizados. Prevenir incompatibilidades tanto al asignar responsables como al aprobar negocios, con verificación autoritativa; ocultar opciones Android no basta. Identificar y resolver explícitamente conflictos preexistentes sin retirar automáticamente permisos/asignaciones. No introducir otro sistema de cuentas.
Conservar Firebase UID/ownerUid para propiedad y correo normalizado correspondiente al correo autenticado y verificado para asignaciones.
**PENDIENTE.** Protocolo técnico de colisiones, asignaciones anteriores al registro y concurrencia aprobación/asignación. No hay colección definitiva de roles ni mecanismo de resolución aprobado. El tratamiento de asignaciones incompatibles preexistentes de propietarios suspendidos requiere precisión, sin convertir la suspensión en pérdida de propiedad.

### DEC-HU34-05 — Experiencia de propietario con negocio suspendido

**APROBADO.** Conserva experiencia especializada; no retorna automáticamente a cliente. Consulta estado y motivo de suspensión, pero no accede al listado ni a la gestión de locales mientras siga suspendido. Las operaciones administrativas restringidas permanecen bloqueadas. Tendrá acceso a solicitar apelación cuando se implemente esa funcionalidad. No obtiene bandeja de pedidos recibidos ni permiso para nuevas compras. Pérdida de autorización operativa no equivale a pérdida de identidad propietaria.

**EXISTENTE, no implementación de HU34.** Suspender cambia business_requests.status de APPROVED a SUSPENDED y los locales ACTIVE a INACTIVE; los previamente INACTIVE/SUSPENDED conservan estado. No modifica OPEN/CLOSED/PAUSED. Reactivar cambia el negocio a APPROVED sin activar locales. StoreOperationsViewModel descarta al propietario no aprobado y puede mostrar NoStoresAssigned; BusinessStatusScreen muestra suspensión/motivo. Existe suspensión/reactivación individual de locales independiente. Referencias verificadas en sección 8. No cambiar estos comportamientos en esta etapa.

### Futura apelación de suspensión

**APROBADO funcionalmente.** El propietario suspendido podrá solicitar revisión al Administrador Chaski. **PROPUESTO.** HU35 — Solicitar y evaluar apelaciones de suspensión; numeración provisional hasta formalizar planificación. HU07 conserva suspensión/reactivación administrativa existente; HU34 conserva experiencia restringida y acceso futuro; HU35 propuesta abarcaría solicitud, seguimiento, evaluación y resolución.
**PENDIENTE.** Atributos, estados, colecciones, endpoints, formularios, reglas y tratamiento de suspensiones individuales de locales. No definirlos ni implementar apelación dentro de HU34; no crear documento HU35 en esta etapa.

### DEC-HU34-06 — Resolución centralizada y actualización de permisos

**APROBADO.** Resolver experiencias con Firebase Authentication y fuentes existentes de propiedad/asignaciones. No duplicar cuentas ni crear colección de roles sin justificación aprobada. Centralizar un contexto de autorización reutilizable durante la sesión Android, evitando consultas innecesarias por pantalla. Actualizar al cambiar identidad, aprobación/suspensión o asignaciones. Distinguir carga, fallo de consulta y ausencia real de permisos; un error de red no convierte al usuario en cliente. No confiar indefinidamente en permisos almacenados: autorizar operaciones sensibles mediante reglas Firestore/backend. Revocar consultas y retirar datos privados al perder asignación. Preservar ámbitos y flujos Address, Cart y Checkout.
**PENDIENTE.** Interfaces Kotlin, repositorios, observadores, estados, ubicación y navegación durante HU34-3A; estos principios no aprueban una arquitectura concreta nueva.

### DEC-HU34-07 — Compras exclusivamente desde la experiencia de cliente

**APROBADO.** Solo cliente inicia nuevas compras. Propietario y responsable no pueden hacerlo; ambos conservan consulta/seguimiento de compras personales anteriores. Aprobar un negocio cambia la experiencia de cliente a propietario. Exresponsable sin asignaciones ni otra autorización comercial retorna a cliente y puede comprar. foodApi aplicará autoritativamente la restricción al implementar HU16, incluso con acceso directo al endpoint. La experiencia visible no es la única barrera. Pedidos personales históricos y pedidos recibidos por local son accesos distintos. HU17 implementará el historial completo; no adelantar pantallas fuera del alcance.

### DEC-HU34-08 — Coordinación autoritativa UID–correo

**APROBADO.**

Se utilizará `foodApi` para coordinar las operaciones sensibles de aprobación de negocios y asignación de responsables.

Se incorporará un índice mínimo derivado que permita coordinar identidad UID y correo normalizado, manteniendo:

- Firebase Authentication como proveedor de identidad.
- `business_requests` como fuente de propiedad.
- `store_managers` como fuente de asignaciones.

Las operaciones que puedan conceder permisos incompatibles deberán coordinarse transaccionalmente.

No se creará un nuevo sistema independiente de cuentas o roles.

**PENDIENTE:** esquema exacto del índice, claves, mantenimiento, reconciliación, migración y detalles de concurrencia.

La dirección arquitectónica está aprobada; el diseño físico todavía no.

### DEC-HU34-09 — Vinculación segura de responsables

**APROBADO.**

1. Permitir asignar un responsable por correo antes de que exista una cuenta Firebase.
2. Conservar esa asignación sin exigir registro o activación previa.
3. Exigir autenticación y correo verificado antes de otorgar acceso operativo.
4. Vincular de forma autoritativa UID y asignaciones cuando corresponda.
5. Verificar que la cuenta no tenga propiedad comercial incompatible.
6. No transferir automáticamente permisos entre correos o UID.
7. Suspender accesos afectados cuando cambie la identidad hasta completar una reconciliación autorizada.
8. Proteger el sistema frente a credenciales o vinculaciones obsoletas.

Conservar DEC-HU16-05: la asignación registrada puede permitir que el local reciba pedidos, aunque el responsable todavía no haya iniciado sesión.

### DEC-HU34-10 — Cuenta de responsable y registro de negocio

**APROBADO: alternativa B.**

Una cuenta con experiencia de responsable no podrá solicitar registrar un negocio propio.

Si la persona desea registrar un negocio, utilizará otra cuenta de Firebase Authentication con otro correo y UID.

Ambas cuentas serán independientes.

Reglas:

- La cuenta de responsable conserva sus asignaciones operativas.
- La nueva cuenta podrá presentar una solicitud comercial.
- Una solicitud pendiente no concede experiencia de propietario.
- La aprobación convertirá esa nueva cuenta en propietaria.
- No se vincularán automáticamente las cuentas por corresponder a la misma persona.
- No es necesario retirar los permisos de la primera cuenta para registrar el negocio con la segunda.

La exclusividad se aplica por identidad de cuenta autenticada, no globalmente por persona física.

Esta decisión sustituye cualquier propuesta anterior que permitiera a una cuenta de responsable solicitar un negocio mientras conservaba sus asignaciones.

### DEC-HU34-11 — Conflictos de autorizaciones incompatibles

**APROBADO.**

Si una cuenta presenta simultáneamente autorizaciones incompatibles:

1. Detectar un estado explícito de conflicto.
2. No aplicar una precedencia automática entre propietario y responsable.
3. Restringir las operaciones comerciales protegidas mientras el conflicto permanezca.
4. No eliminar negocios, locales, pedidos ni asignaciones automáticamente.
5. Permitir una resolución mediante operaciones autorizadas, con supervisión administrativa cuando corresponda.
6. Recalcular las autorizaciones después de resolver el conflicto.
7. Conservar el derecho a consultar compras personales históricas, mediante autorización independiente.

No inventar todavía una nueva interfaz administrativa ni procedimientos definitivos de resolución.

### DEC-HU34-12 — Cambio de correo de responsables

**APROBADO.**

Cuando el responsable cambia el correo de su cuenta Firebase:

- No se transfieren automáticamente sus asignaciones.
- Se suspende el acceso operativo afectado hasta verificar la nueva vinculación.
- Se conservan los documentos de asignación existentes.
- El propietario autorizado debe aprobar la actualización correspondiente.
- Si hay asignaciones en varios negocios, cada propietario administra las que le corresponden.
- El backend comprueba identidad, correo verificado y exclusividad.
- Tokens y vinculaciones anteriores no deberán mantener acceso indebido.

No cambiar el mecanismo existente por uno nuevo sin necesidad justificada.

### DEC-HU34-13 — Migración controlada hacia foodApi

**APROBADO.**

Las operaciones sensibles de aprobación de negocios y modificación de asignaciones deberán coordinarse mediante `foodApi`.

La transición deberá:

1. Preparar las operaciones autoritativas y el índice aprobado.
2. Adaptar los clientes Android correspondientes.
3. Restringir mediante Firestore Rules las escrituras que puedan eludir el protocolo.
4. Probar compatibilidad y comportamiento de clientes anteriores.
5. Evitar conservar rutas inseguras únicamente para permitir versiones antiguas.
6. Preservar las operaciones existentes que no requieran migración, especialmente las facultades correspondientes a HU09/HU11.
7. Probar localmente antes de cualquier despliegue autorizado.

No implica migrar todas las operaciones de Firebase a `foodApi`.

**PENDIENTE:** secuencia técnica exacta de transición y compatibilidad.

### DEC-HU34-14 — Seguridad durante desconexiones y cambios de sesión

**APROBADO.**

1. Perder conectividad no cambia automáticamente la experiencia a cliente.
2. Las operaciones protegidas requieren autorización vigente del servidor.
3. No se mostrarán datos operativos privados desde cachés sin autorización verificable según la política definida.
4. Al reconectar, se reevaluarán permisos y asignaciones.
5. El cambio de cuenta cancelará consultas y aislará datos privados por UID.
6. Se conservarán datos locales legítimos, como carritos Room, sin exponerlos a otras cuentas.
7. Los errores de red tendrán estado explícito y no se interpretarán como revocación confirmada.

**PENDIENTE:** mecanismos concretos de almacenamiento local, limpieza de datos sensibles y política de revalidación.

No prometer revocación instantánea en dispositivos desconectados.

### DEC-HU34-15 — Identidad y sesión compartidas

**APROBADO.**

HU34 utilizará un contexto compartido de identidad y sesión, reutilizando Firebase Authentication y patrones existentes.

Objetivos:

- Evitar listeners duplicados.
- Centralizar información de identidad.
- Diferenciar generaciones de sesión.
- Descartar respuestas antiguas, incluso cuando el mismo UID vuelve a iniciar sesión.
- Preservar los mecanismos actuales de Cart, Address y Checkout.
- No reconstruir innecesariamente los ámbitos de navegación.
- Evitar introducir un sistema paralelo de autenticación.

**PENDIENTE:** contrato Kotlin concreto, ubicación del adaptador, ciclo de vida y estrategia de compartición.

No hacer depender el dominio compartido directamente de la implementación interna del carrito.

### DEC-HU34-16 — Contrato inicial del resolvedor de autorizaciones

**APROBADO.** Ubicación core/access/domain; modelos inmutables de identidad, verificación, propiedad, asignaciones y sesión; resolvedor Kotlin puro con experiencia/capacidades explícitas. Distinguir carga, error, conflicto y ausencia confirmada. Probar los 18 escenarios documentados. Sin Firebase SDK, Firestore, Room, Compose, navegación o HTTP. Conservar comportamiento HU14/HU15.

### DEC-HU34-17 — Origen y validación de hechos

**APROBADO.**

- Reutilizar Firebase Authentication y mecanismos existentes de sesión.
- No introducir una colección independiente de roles.
- Diferenciar hechos confirmados, carga, error y verificación pendiente.
- Evitar datos de sesiones anteriores.
- No utilizar permisos calculados en Android como autorización del backend.
- No fabricar evidencia autoritativa de vinculaciones comerciales.

### DEC-HU34-18 — Separación de identidad y autorización definitiva

**APROBADO.**

- Adoptar la alternativa C recomendada en la auditoría.
- Implementar primero identidad compartida y contrato de eventos.
- Cada evento tendrá procedencia de sesión y revisión.
- Cambios de correo, verificación o sesión invalidarán información anterior.
- No emitir `AccessFacts.Checked` con garantías que todavía no hayan sido acreditadas.
- No conectar el resultado comercial con pantallas hasta disponer del coordinador correspondiente.
- Preservar los mecanismos de Cart, Address y Checkout.

### DEC-HU34-19 — Asignaciones pendientes y conflictos confirmados

**APROBADO.**

- Solo declarar conflicto propietario/responsable si existe evidencia autoritativa de autorizaciones incompatibles para el mismo UID.
- Una asignación pendiente no concede permisos ni provoca automáticamente conflicto.
- Una asignación vinculada de manera confirmada a otro UID no pertenece al usuario actual.
- La reconciliación debe ser autorizada, sin transferencias automáticas.
- Si no puede comprobarse identidad, vinculación o exclusividad, utilizar estado restrictivo.
- Una invitación pendiente no bloqueará por sí sola la propiedad legítima cuya exclusividad esté verificada.
- Agregar pruebas para estas combinaciones.

### DEC-HU34-20 — Índice privado derivado por UID y correo

**APROBADO.** Una colección privada derivada coordina UID y correo normalizado; Auth, business_requests y store_managers mantienen sus autoridades. No es una colección independiente de roles.

### DEC-HU34-21 — Identidad vigente y credenciales anteriores

**APROBADO.** Verificar identidad vigente y proteger operaciones frente a credenciales o vinculaciones obsoletas. No afirmar atomicidad Auth–Firestore.

### DEC-HU34-22 — Migración segura de operaciones

**APROBADO.** Preparar y migrar operaciones sensibles; cerrar posteriormente escrituras directas que eludan el coordinador. No declarar producción segura antes de completar esa transición.

### DEC-HU34-23 — Contratos HTTP, revisiones y reintentos

**APROBADO.** Contratos mínimos con revisiones y repetición segura, sin efectos duplicados ni permisos ante fallo.

### DEC-HU34-24 — Preparación controlada de datos existentes

**APROBADO.** Preparación/reconciliación repetible, detección de conflictos e inconsistencias y conservación de fuentes; sin eliminaciones automáticas.

### DEC-HU34-25 — Desarrollo incremental desde foodApi

**APROBADO.** Comenzar con coordinador aislado y pruebas locales en foodApi; integrar Android y cerrar escrituras en incrementos posteriores autorizados.

## 6. Requisitos funcionales y no funcionales

| ID | Estado | Requisito |
|---|---|---|
| RF34-01 | APROBADO | Resolver permisos al autenticar; carga/error no equivalen a ausencia de permisos |
| RF34-02 | APROBADO | Mantener identidad, propiedad y asignaciones existentes |
| RF34-03 | APROBADO | Exclusividad sin autorización por visibilidad de menú |
| RF34-04 | APROBADO | Varios locales asignados y revocación por local |
| RF34-05 | APROBADO | Preservar consulta/seguimiento de compras anteriores de propietario y responsable |
| RF34-06 | APROBADO | Navegar desde aviso solo tras verificar sesión y acceso al local |
| RF34-07 | APROBADO | Propietario suspendido conserva experiencia, estado/motivo, sin listado/gestión de locales; apelación futura |
| RF34-08 | APROBADO | Solo cliente inicia compras; backend HU16 rechaza propietario/responsable |
| RF34-09 | APROBADO | Responsable no registra negocio; separación de cuentas sin retirar asignaciones |
| RF34-10 | APROBADO | Coordinación foodApi e índice derivado; asignación previa al registro y vinculación verificada |
| RF34-11 | APROBADO | Conflicto explícito, sin precedencia ni eliminación automática |
| RF34-12 | APROBADO | Cambio de correo suspende acceso afectado; cada propietario autoriza actualización de sus asignaciones |
| RNF34-01 | APROBADO | MVVM/capas, sin acceso Firebase ni reglas de permiso en Compose |
| RNF34-02 | APROBADO | Ciclo de vida, cancelación, aislamiento de cuenta y respuestas tardías |
| RNF34-03 | APROBADO | Conservar ámbitos Address/Cart y evitar observadores duplicados |
| RNF34-04 | APROBADO | Minimizar datos privados en logs, cachés y push |
| RNF34-05 | APROBADO | Identidad compartida, generaciones de sesión y seguridad offline; transición controlada de escrituras |

## 7. Restricciones de seguridad

**APROBADO.** Pedidos exclusivamente para responsables asignados, sin concesión por propiedad o claim admin. Verificar relación negocio/local/asignación y correo autenticado verificado; no aceptar correos/locales declarados por Android como autorización.
**EXISTENTE.** requireStoreOperator incluye propietario/responsable: conservar HU09/HU11. **PENDIENTE.** Reglas locales managerOf/managesBusiness no comprueban email_verified como Android; alinear sin asumir que estas reglas están desplegadas. store_managers permite lectura autenticada amplia: revisar mínimo acceso sin romper consultas legítimas. Un push previo puede llegar después de revocación; minimizar payload y reautorizar lectura.

## 8. Dependencias arquitectónicas

Rutas relativas reales desde raíz; líneas de referencia en el HEAD indicado:

| Evidencia EXISTENTE | Archivo y líneas |
|---|---|
| Contacto separado del UID propietario | app/src/main/java/com/chaskifood/app/feature/business/presentation/RegisterBusinessViewModel.kt:126–149 |
| Registro por UID y aprobación | app/src/main/java/com/chaskifood/app/feature/business/data/FirebaseBusinessRepository.kt:109–149 |
| Operador incluye propietario | app/src/main/java/com/chaskifood/app/core/firebase/FirebaseAccessControl.kt:25–50 |
| Asignaciones normalizadas/listeners con limpieza | app/src/main/java/com/chaskifood/app/feature/business/data/FirebaseStoreRepository.kt:177–263 |
| Prioridad actual propietario sobre asignaciones; hallazgo, no acuerdo | app/src/main/java/com/chaskifood/app/feature/business/presentation/StoreOperationsViewModel.kt:70–90 |
| Menú compartido | app/src/main/java/com/chaskifood/app/feature/profile/presentation/AccountScreen.kt:123–175 |
| Contenedor compartido | app/src/main/java/com/chaskifood/app/feature/main/presentation/MainScreen.kt:63–113 |
| Ámbitos compartidos ligados a MAIN | app/src/main/java/com/chaskifood/app/core/navigation/ChaskiNavHost.kt:336,493,515,736 |
| Suspensión general, cascada y auditoría; máximo ocho activos | app/src/main/java/com/chaskifood/app/feature/business/data/FirebaseBusinessRepository.kt:152–215 |
| Verificación posterior; reactivación general sin activar locales | app/src/main/java/com/chaskifood/app/feature/business/data/FirebaseBusinessRepository.kt:236–284 |
| Estado y motivo visibles | app/src/main/java/com/chaskifood/app/feature/business/presentation/BusinessStatusScreen.kt:278–319 |
| Suspensión individual independiente; no modifica estado operativo | app/src/main/java/com/chaskifood/app/feature/business/data/FirebaseStoreRepository.kt:371–398 |
| Permisos locales | firestore.rules:285–317,616–635 |

**APROBADO como principio (DEC-HU34-06); contrato técnico PENDIENTE.** Contexto de autorización reutilizable y alcance derivado de autoridades existentes; no persistir un rol independiente que contradiga asignaciones. **PENDIENTE.** Contrato, ubicación y estrategia de observación/grafos. Reutilizar sesión sin introducir SDKs en Domain. Resolver autorización y preservar ámbitos MAIN antes de separar pantallas. No diseñar interfaces finales fuera de Pencil.

## 9. Criterios de aceptación verificables

| ID | Criterio APROBADO |
|---|---|
| CA34-01 | Cuenta sin autorización entra como cliente; solicitud pendiente no concede propiedad aprobada |
| CA34-02 | Aprobación cambia experiencia y conserva consulta/seguimiento de compras anteriores |
| CA34-03 | Propietario no lee bandeja recibida, no opera pedidos ni recibe sus avisos individuales |
| CA34-04 | Responsable solo ve locales asignados y sus pedidos; varios locales funcionan |
| CA34-05 | Correo no verificado no habilita acceso de responsable, tampoco en servidor/reglas |
| CA34-06 | Revocar local retira acceso; revocar todas las asignaciones retorna a cliente si no hay otro rol |
| CA34-07 | Sustitución de responsable conserva pedidos accesibles al nuevo autorizado |
| CA34-08 | Cambio de cuenta/respuesta tardía no muestra datos de otra sesión |
| CA34-09 | HU09/HU11 y ámbitos Address/Cart conservan funcionamiento |
| CA34-10 | Combinación incompatible no acumula permisos silenciosamente; resolución según protocolo por cerrar |
| CA34-11 | Admin no recibe acceso operativo a pedidos automáticamente |
| CA34-12 | Propietario suspendido conserva experiencia, estado/motivo; sin listado ni gestión de locales ni bandeja recibida |
| CA34-13 | Propietario/responsable no inician compras; acceso directo a foodApi también rechazado al implementar HU16 |
| CA34-14 | Ambos consultan compras personales anteriores; exresponsable sin otro permiso retorna a cliente y puede comprar |
| CA34-15 | Prevenir incompatibilidades al aprobar/asignar; conflictos previos se resuelven explícitamente sin retirar permisos automáticamente |
| CA34-16 | Carga/error/ausencia de permisos se distinguen; fallo de red no convierte a cliente; actualizar contexto y preservar ámbitos Address/Cart/Checkout |
| CA34-17 | Responsable no solicita negocio desde su cuenta; otra cuenta independiente sí puede hacerlo sin retirar asignaciones originales |
| CA34-18 | Asignación previa al registro no exige activación; acceso exige identidad verificada y vinculación autoritativa sin propiedad incompatible |
| CA34-19 | Conflicto bloquea operaciones protegidas, conserva datos e histórico personal con autorización independiente |
| CA34-20 | Cambio de correo conserva asignaciones y suspende acceso afectado hasta actualización aprobada por cada propietario y reconciliación backend |
| CA34-21 | Aprobación/asignación coordinadas no dejan permisos incompatibles; clientes antiguos no eluden protocolo |
| CA34-22 | Sesión compartida descarta respuestas de otra generación incluso con mismo UID; offline no concede acceso privado no verificable |

CA34-10 requiere cerrar el protocolo, no implica una precedencia aprobada.

## 10. Escenarios y pruebas esperadas

**PROPUESTO; no ejecutado aquí.** Unitarias de capacidades, negocio pendiente/observado/rechazado/suspendido y concurrencia aprobación/asignación. Emulator: correo no verificado, asignación ajena/revocada, owner/admin rechazados para bandeja, nuevo responsable y varios locales. Compose: cambios de experiencia, acceso directo/desde aviso, retorno, A→logout→A/B, ámbitos compartidos. Regresiones HU04, HU05–HU11, HU14/HU15. Offline: no afirmar autorización actual ni filtrar caché entre cuentas; no prometer revocación instantánea sin red.

Pruebas adicionales **PROPUESTAS / NO EJECUTADAS**: propietario y responsable intentando confirmar directamente por backend; lectura propia histórica permitida y bandeja ajena rechazada. Suspensión mantiene experiencia sin listado/gestión; reactivación no activa locales ni abre CLOSED/PAUSED. Verificar conservación de INACTIVE/SUSPENDED y operación individual independiente. Asignación múltiple válida, propietario intentando ser responsable propio/ajeno, aprobación con asignación previa, correo asignado antes de registro y carreras aprobación/asignación. Error de red conserva estado de error, sin degradar a cliente; revocación retira suscripciones/datos y conserva ámbitos. Apelación futura no se presenta como flujo implementado.

## 11. Riesgos y asuntos pendientes

**APROBADO, no reabrir como decisión pendiente:** coordinación foodApi/índice derivado; asignación previa al registro; cuentas independientes; conflicto explícito; actualización de correo autorizada; transición controlada; seguridad offline e identidad compartida (DEC-HU34-08–15).

**PENDIENTE técnico:** esquema/claves/mantenimiento/migración del índice; reconciliación Auth–Firestore y tokens anteriores; contratos REST; secuencia de transición de escrituras y clientes anteriores; estructura Kotlin; reglas e índices de consulta; caché y limpieza sensible; implementación y pruebas de concurrencia. Auth y Firestore no forman una transacción conjunta. No aprobar mecanismos físicos por describirlos aquí.

Riesgos: coordinación divergente de sus fuentes, escrituras antiguas que eludan protocolo, respuestas obsoletas, cachés entre cuentas y pérdida de ámbitos MAIN. Resolver conflictos sin eliminación automática. HU35 sigue propuesta futura, sin contratos definidos. Documentos generales desactualizados y docs ignorada requieren compartir copias por canal acordado; no forzar staging.

## 12. Relación con HU16 y HU19–HU28

[HU16](HU16_DECISIONES_Y_REQUISITOS.md) exige asignación de responsable para nuevas confirmaciones. Responsable vigente puede leer pedidos anteriores durante pausa/suspensión; suspensión no equivale a revocar asignación. HU19 precisa acciones durante suspensión y aceptación directa a PREPARING. HU20–HU21 preparación/recojo. HU24 amplía avisos; el mínimo de nuevo pedido se adelanta a HU16. HU28 ofrece agregados, no acceso individual al propietario. HU17 completa historial/seguimiento personal de compras anteriores de propietario y responsable. DEC-HU34-07 restringe nuevas compras a cliente, con control backend en HU16. HU35 propuesta queda fuera de implementación de HU34/HU16.


## 13. HU34-3A.1 — Modelos y resolución pura de autorizaciones

**IMPLEMENTADO Y PROBADO, sin integración.** Esta sección conserva la evidencia inicial 3A.1; correcciones y contratos vigentes en sección 14 (3A.2). Ubicación aprobada mediante DEC-HU34-16: app/src/main/java/com/chaskifood/app/core/access/domain; pruebas en app/src/test/java/com/chaskifood/app/core/access/domain.

### Modelos y contrato real

- AccessIdentity.kt: AccessSession(uid, generation), EmailVerification (VERIFIED/UNVERIFIED/UNKNOWN), AccessIdentity.SignedOut/Authenticated. No tokens. Claim admin independiente de autorizaciones comerciales.
- AccessFacts.kt: Loading, Failed, Unverifiable y Checked(session, ownership, assignments, stores). Checked declara hechos comprobados para esa sesión; propiedad nula y lista vacía solo significan ausencia confirmada dentro de Checked. AccessOwnership usa ownerUid, businessId, los cinco estados actuales y suspensionReason; AccessAssignment usa businessId/storeId/boundUid/correo autorizado y vinculación VERIFIED o RECONCILIATION_REQUIRED. AccessStore separa estado administrativo de negocio/local y estado operativo. Colecciones con copia defensiva no modificable.
- AccessResolution.kt: CommercialExperience, AccessCapability, AccessGrant y ámbitos Account(uid), Business(businessId), Store(businessId,storeId). AccessResolution.SignedOut/Loading/Error/Conflict/Resolved. Resultado autenticado conserva sesión o identidad con generación. Error tipado: FACTS_UNAVAILABLE, FACTS_UNVERIFIABLE, STALE_SESSION, INVALID_FACTS.
- ResolveAccess.kt: `operator fun invoke(identity: AccessIdentity, facts: AccessFacts): AccessResolution`. Evaluación determinista sin I/O, SDKs ni estado global. Solo biblioteca Kotlin/JVM (Locale/Collections), sin inyección ni consumidor nuevo.
- ResolveAccessTest.kt: suite JUnit 4 con dependencias existentes, sin Firebase ni servicios externos.

### Reglas implementadas y capacidades

Sesión cerrada no autoriza. Carga permanece carga; error/inverificable no produce cliente; hechos de otro UID/generación son rechazados. Propiedad con ownerUid distinto o datos estructurales inválidos falla restrictivamente.
Sin propiedad aprobada/suspendida ni asignaciones y con ausencia comprobada: CLIENT. PENDING_REVIEW/OBSERVED/REJECTED no conceden OWNER. START_PURCHASE es por cuenta cliente; REQUEST_BUSINESS solo sin solicitud propia existente y sin responsabilidad. Esto no modifica el flujo HU05 de consulta/corrección de una solicitud existente.
APPROVED: OWNER con gestión de negocio/locales/responsables por businessId y horarios/oferta solo para locales propios comprobados ACTIVE. SUSPENDED: OWNER con estado/motivo, sin listado ni gestión. Ambos sin nuevas compras ni bandeja recibida.
Asignaciones: MANAGER requiere correo verificado coincidente normalizado, boundUid actual y vinculación VERIFIED en todas las filas. Vinculación pendiente, identidad diferente o verificación desconocida/no verificada: Error FACTS_UNVERIFIABLE, sin operaciones ni conversión a cliente. Es salida restrictiva, no una experiencia visual definitiva. Última retirada confirmada devuelve cliente si no hay propiedad comercial.
Propiedad APPROVED/SUSPENDED con asignaciones genera Conflict sin precedencia ni mutación; solo READ_PERSONAL_PURCHASES por UID, como autorización personal independiente. Todos los resueltos conservan histórico personal por UID.
READ_STORE_ORDERS es por negocio/local asignado y no exige ACTIVE/APPROVED ni fecha de asignación del pedido. OPERATE_HOURS_AND_OFFERS requiere negocio APPROVED y local ACTIVE; OPEN/CLOSED/PAUSED no alteran la facultad de gestionar horarios/oferta. No hay capacidad para recibir nuevos pedidos derivada de esa operación: horarios/cobertura/oferta se validan aparte. ACCEPT_STORE_ORDER/REJECT_STORE_ORDER/PREPARE_STORE_ORDER están representadas pero nunca concedidas, pendientes de política HU19–HU21.
El claim admin no añade facultades comerciales; dos UID no comparten hechos ni permisos. El índice de coordinación no participa como fuente autónoma de capacidades.

### Verificaciones ejecutadas

28 pruebas JVM ejecutadas: 28 aprobadas, 0 fallos, 0 errores, 0 omitidas. Cubren los 18 escenarios del plan: cliente, solicitud pendiente, propietario aprobado/suspendido, responsable uno/varios locales, correo no verificado, retirada total, conflicto, admin, nuevas compras bloqueadas, histórico personal, cambio de correo/reconciliación, fallo frente a ausencia, generaciones, estados administrativos/operativos, registro de negocio denegado al responsable y cuentas independientes. Extras: propietario sin bandeja/acciones, sesión cerrada, carga, lectura durante suspensión, retirada parcial, ownerUid ajeno, boundUid ajeno, copias defensivas, verificación desconocida y estados OBSERVED/REJECTED.
Comando desde raíz: `.\gradlew.bat :app:testDebugUnitTest --tests com.chaskifood.app.core.access.domain.ResolveAccessTest -PchaskiFirebaseMode=CONECTADO -PchaskiConnectedProjectId=chaski-food-9adba -PchaskiCheckoutApiBaseUrl=https://foodapi-u65lcqarqa-tl.a.run.app/api/v1/ --console=plain`. BUILD SUCCESSFUL; incluye compileDebugKotlin. No se ejecutó aplicación ni hubo llamadas Firebase.
Primer intento con LOCAL se detuvo por validación Gradle: el JSON personal activo pertenece al proyecto real. No se cambió ni leyó su contenido; se usaron las propiedades CONECTADO ya documentadas. Avisos preexistentes de JDK 25/Kotlin y anotaciones no impidieron la ejecución final. No se ejecutaron pruebas instrumentadas, Emulator ni suites generales HU14/HU15 en este incremento.

### Límites y próximos incrementos

No integrado en pantallas, ViewModels ni repositorios: este código aún no protege endpoints ni reemplaza Firestore Rules. Checked es contrato de entrada para adaptadores futuros, no una verificación criptográfica. El resolvedor etiqueta generaciones y rechaza hechos antiguos, pero no implementa cancelación, listeners o transacciones. No coordina Auth–Firestore ni impide carreras backend por sí mismo.
Pendiente HU34-3A.2 y siguientes: adaptador/observación de identidad y hechos autoritativos, ciclo de vida compartido, protocolo de frescura/reconciliación, índice y concurrencia, rutas foodApi/reglas y migración autorizadas. HU34-3B/Pencil: representación visual y navegación preservando MAIN/Address/Cart/Checkout. HU16: confirmación y bandeja productiva; HU19–HU21: acciones; HU35: apelación. No iniciados.


## 14. HU34-3A.2 — Correcciones, identidad compartida aislada y procedencia

**IMPLEMENTADO dentro de core/access, sin integración comercial.** DEC17–19 aprobadas arriba. No hay adaptador Firebase real ni Hilt nuevo: AuthRepository es un flujo frío con IdTokenListener; Cart/Address tienen listeners propios y restricciones impiden adaptarlos ahora. Crear otro listener habría duplicado infraestructura. Se implementó el contrato aislado y la compartición comprobada con dobles; conexión Firebase PENDIENTE con autorización específica, sin necesidad de ampliar este incremento.

### Contratos y correcciones vigentes

AccessFacts.Checked incorpora AccessExclusivity.VERIFIED/UNVERIFIABLE (valor predeterminado restrictivo). No basta un correo coincidente para afirmar exclusividad. AccessBinding distingue VERIFIED, PENDING, RECONCILIATION_REQUIRED y UNVERIFIABLE; VERIFIED con boundUid distinto es asignación confirmada ajena, ignorada para permisos/conflicto actuales. VERIFIED sin UID es no verificable. Ausencia de asignaciones aplicables solo resuelve cliente con exclusividad comprobada y sin pendientes relevantes.
ResolveAccess declara conflicto solo por propiedad APPROVED/SUSPENDED y asignación VERIFIED vinculada al mismo UID. Invitación PENDING no efectiva no bloquea propiedad con exclusividad VERIFIED. Reconciliación no comprobada continúa restrictiva. El correo/verificación del responsable se comprueba antes de conceder operación, sin transferir asignaciones. Checked no valida por sí mismo la veracidad de hechos externos; el futuro coordinador debe acreditarlos.
AccessCapabilities y Checked implementan equals/hashCode por contenido manteniendo copias defensivas. Orden de asignaciones/locales no implica cambio semántico; duplicados de locales siguen diferenciados para no deduplicar hechos inválidos como si fueran válidos.

AccessEvents.kt: AccessProvenance(session?, generation, revision), AccessEvent.Identity(origin, identity) y AccessEvent.Facts(origin, facts). Todos los tipos de hechos, incluidos Loading/Failed/Unverifiable, viajan dentro del evento con origen. `EvaluateAccessEvent.invoke(current, identity, event): AccessResolution?`: origen diferente devuelve null (descartar); no publica error viejo como actual. Capturar origin al comenzar el trabajo; nunca retaggear al llegar la respuesta. El consumidor futuro debe evaluar/publicar de forma serializada y mantener un origen vigente único; este contrato no implementa transacciones ni cancelación real de consultas.

SharedAccessIdentitySource.kt (Data): `AccessIdentityDriver.observe(): Flow<IdentityObservation>`; `SharedAccessIdentitySource(driver, scope).states: StateFlow<AccessIdentityState>`. Observaciones Loading/Failed/SignedOut/Authenticated, sin tokens. Estado con identidad, procedencia y disponibilidad LOADING/READY/FAILED. Una suscripción eager al driver por instancia, en scope propiedad del llamador; cancelar scope termina observación. Requiere instancia compartida futura, no construir una por pantalla. Driver Firebase NO IMPLEMENTADO: debe emitir observaciones ordenadas y filtrar sus callbacks obsoletos. sessionKey es identificador opaco de inicio de sesión, nunca credencial.
Generación cambia ante UID/sessionKey diferentes o logout; revisión cambia al variar identidad relevante (correo/verificación/admin) o disponibilidad. Dos emisiones equivalentes no cambian revisión ni estado observable. Error conserva identidad anterior con disponibilidad FAILED, sin convertirla en cliente. Fuente solo emite identidad/disponibilidad, nunca Checked ni capacidades comerciales. El cambio futuro de autorización deberá invalidar revisión/origen al iniciar otra comprobación; la recepción sola de datos no acredita su frescura.

### Pruebas y evidencia

Resultado final ejecutado: 53 pruebas, 53 aprobadas, 0 fallos, 0 errores y 0 omitidas; BUILD SUCCESSFUL, incluida compilación Kotlin. Suites: ResolveAccessTest y SharedAccessIdentitySourceTest. Se conservaron las 28 pruebas anteriores; la expectativa de vinculación confirmada ajena se adaptó a DEC19 (no transfiere permisos; sin otras autorizaciones se resuelve cliente con ausencia/exclusividad confirmada).
Nuevas aserciones: propietario+invitación pendiente; propietario+asignación propia confirmada conflicto (prueba anterior); propietario+asignación ajena sin conflicto; exclusividad/vinculación desconocida restrictiva; retirada compara antes/después; dos cuentas responsable/propietaria independientes; hechos semánticamente equivalentes y locales duplicados inválidos.
Eventos antiguos Loading/Failed/Unverifiable/Checked de A no alteran B; logout/login mismo UID cambia generación; cambio correo/verificación o revisión descarta anteriores; cierre e identidad antiguos no reemplazan estado vigente. Error explícito no resuelve cliente. Múltiples consumidores mantienen una suscripción al driver doble y cancelar scope la cierra. Identidad equivalente se deduplica; evento de identidad solo da Loading/SignedOut, sin permisos comerciales definitivos.
Comando: `.\gradlew.bat :app:testDebugUnitTest --tests com.chaskifood.app.core.access.* -PchaskiFirebaseMode=CONECTADO -PchaskiConnectedProjectId=chaski-food-9adba -PchaskiCheckoutApiBaseUrl=https://foodapi-u65lcqarqa-tl.a.run.app/api/v1/ --console=plain`. Propiedades existentes para el JSON legítimo activo; JVM sin ejecutar aplicación, sin Firebase real. Incluye compileDebugKotlin. Sin nuevas dependencias ni cambio de configuración personal.

### Clasificación PROPUESTA de pendientes

| Prioridad propuesta | Pendientes |
|---|---|
| P0 — Seguridad e integridad | Coordinador UID–correo, reconciliación/tokens anteriores, exclusividad transaccional, migración de escrituras y reglas; origen/revisión y publicación serializada en integración, revocación/caché privadas y aislamiento de cuentas; autorización backend independiente de Android |
| P1 — Producto actual | Conexión compartida Firebase aprobada y fuentes comerciales verificadas; integración HU34 y navegación diseñada en Pencil preservando MAIN; confirmación/bandeja HU16 y capacidades específicas de responsables |
| P2 — Ampliación/optimización evaluable | Optimización adicional de observación y mediciones; ampliaciones de experiencia como apelación HU35, según planificación del usuario |

P2 no elimina acuerdos ni descarta funcionalidades; el usuario puede adelantarlas. Prioridades no autorizan implementación extra. Seguridad de cada funcionalidad conserva P0 cuando se implemente.

### Límites y próximo incremento

No se modificaron Auth/Cart/Address/Checkout, Rules, backend, navegación, Room, Hilt general, dependencias ni HU16. Ningún endpoint queda protegido por este dominio aislado. No hay índice ni colección/claim de roles nueva. No se prueba observación Firebase ni revocación remota; las pruebas de suscripción usan dobles. Error terminal del driver se representa FAILED; reconexión/reintento y refresco concreto quedan para el adaptador real.
Próximo incremento PROPUESTO: especificar evidencia de exclusividad/vinculación y protocolo de origen/revisión del coordinador; autorizar separadamente conexión mínima a identidad existente sin duplicar listeners. No fabricar Checked para adelantar pantallas. Coordinador, integración y UI no iniciados.


## 15. HU34-3A.3A — Coordinador comercial aislado en foodApi

**IMPLEMENTADO y validado localmente; NO APTO PARA PRODUCCIÓN.** Las escrituras directas antiguas continúan abiertas deliberadamente en esta fase. Todas las rutas /api/v1/access/ responden 404 en runtime CLOUD; no se cambia el despliegue existente ni HU15. Solo runtime LOCAL estricto demo-chaski-hu15 permite el coordinador. No hay otra Function, Express o dependencias nuevas.

### Organización y responsabilidades

- functions/src/index.ts: enrutamiento local y traducción CommercialAccessError a HTTP; conserva elegibilidad/cotización y su autenticación previa.
- functions/src/auth/verifyBuyerToken.ts: comparte verificación de token/revocación; conserva verifyBuyerToken(): Promise<string>. verifyCommercialIdentity compara claims con getUser actual (correo, verificación, admin, cuenta habilitada); no acepta identidad/roles del cuerpo.
- functions/src/access/commercialAccessPolicy.ts: normalización, validación estricta, representación de asignaciones, exclusividad y digest canónico SHA-256 (incluye orden estable de claves para reintentos).
- functions/src/access/commercialAccessService.ts: preparación, transacciones, revisión, vinculación, auditoría/recibos y hechos propios. Ninguna operación escribe Auth.
- functions/test/commercialAccessPolicy.test.ts y commercialAccessHttp.emulator.ts: pruebas puras/dobles Auth y recorrido HTTP real contra Auth/Firestore/Functions Emulator.

### Contratos HTTP implementados (solo LOCAL)

`POST /api/v1/access/operations`: action PREPARE/APPROVE/ASSIGN/REVOKE/BIND/REGISTER/RESUBMIT, operationId estable y expectedRevision entero no negativo. Revisión corresponde al correo destinatario, no a un rol Android. Campos según acción: businessId; email; storeIds/fullName/phone; o business con businessName/ruc/legalAddress/phone/email/category. Correo comercial business.email sigue siendo contacto, no identidad. Respuesta {action, revision}. PREPARE/APPROVE requieren administrador actual; ASSIGN/REVOKE propietario del negocio APPROVED; BIND/REGISTER/RESUBMIT actúan solo para identidad autenticada.
`GET /api/v1/access/facts`: hechos propios mínimos, revisión, ownership, asignaciones confirmadas y estados de locales necesarios. exclusivity VERIFIED solo significa comprobación en la instantánea transaccional del laboratorio; no autorización permanente ni garantía de que clientes antiguos no escriban después.
`POST /api/v1/access/assignment-revision`: cuerpo {businessId,email}; propietario autorizado obtiene solo {revision} necesaria para actualizar/asignar. Correo en cuerpo, sin exponer UID ni documentos privados de terceros; no crea preparaciones.
Errores: 400 solicitud inválida; 401 token/identidad no vigentes; 403 permisos/verificación; 404 asignación ausente/ruta no disponible; 409 conflicto, preparación/reconciliación, revisión o ID reutilizado incompatible; 503 datos inconsistentes o fallo técnico. Ningún error concede permisos. Registro nuevo/reenvío mantienen PENDING_REVIEW y reenvío solo desde OBSERVED; responsable confirmado no registra ni reenvía; vinculación pendiente produce RECONCILIATION_REQUIRED, sin declarar cliente por ausencia no comprobada.

### Esquema físico e invariantes

Una colección `commercial_access_index`, denegada por el catch-all de reglas locales existentes, sin modificar Rules:
- email_<SHA256 del JSON canónico del correo normalizado>: kind EMAIL, schemaVersion 1, boundUid o null, revision, sourceHash y updatedAt.
- uid_<uid>: kind UID, schemaVersion 1, uid, emailKey, revision y updatedAt.
No contiene tokens, nombres, teléfonos ni roles. No duplica las listas de locales: sourceHash resume filas normalizadas de store_managers; contexto vuelve a leer fuentes. Ambas operaciones incompatibles leen/escriben los mismos documentos de coordinación. revision de correo es la precondición de mutación; la fila UID es coordinación de identidad, no otra revisión comercial autoritativa independiente.
store_managers conserva businessId/email/fullName/phone/createdAt/assignedStoreIds; añade bindingState, boundUid y updatedAt. VERIFIED vincula UID solo tras operación autorizada y comprobación Auth; PENDING no da acceso ni falso conflicto; REVOKED conserva documento sin alcance efectivo. Ausencia de metadatos es LEGACY, nunca VERIFIED. RECONCILIATION_REQUIRED bloquea. Cambios de correo no mueven asignaciones automáticamente.

### Fuentes, transacciones y recuperación

Cada contexto lee coordinación, business_requests del UID/consulta ownerUid y asignaciones por email/boundUid. Rechaza propietarios inconsistentes, referencias inválidas, enlace ajeno y sourceHash divergente. Todos los reads preceden writes. ASSIGN lee además los locales seleccionados y el contexto propietario; APPROVE vuelve a leer solicitud y asignaciones. Los callbacks no consultan Auth ni hacen HTTP/FCM.
Mutación comercial + índice + auditoría/recibo en audit_logs son atómicos. Recibo access_<digest actorUID/operationId> guarda requestHash canónico y resultado. Repetir mismo ID/contenido recupera resultado incluso tras perder respuesta o eliminarse posteriormente la cuenta destinataria; contenido incompatible devuelve OPERATION_ID_REUSED; revisión antigua STALE_REVISION. No hay colección extra de recibos. Rechazos no escriben parcialmente. APPROVE conserva APROBAR_NEGOCIO, reviewedBy/At, updatedAt y lastAuditId.
PREPARE es operación administrativa repetible: recalcula hash/referencias derivadas sin convertir LEGACY en vinculación ni borrar fuentes. Datos legacy requieren actualización explícita del propietario mediante ASSIGN o revocación; la preparación no decide conflictos por precedencia. No hay barrido/migración de producción.
Cambio de correo: credencial anterior se rechaza contra getUser. REVOKE autorizado puede retirar asignación del correo viejo aunque ya no exista cuenta bajo ese correo. Si quedan vínculos efectivos del UID a otro correo, PREPARE nuevo se bloquea. Tras revocaciones autorizadas de todos los negocios afectados, administrador puede preparar nuevo correo; cada propietario autoriza nueva asignación. Documentos antiguos quedan REVOKED y no se transfieren. Reciclado de correo/UID ajeno fijado en índice se bloquea; resolución administrativa de esos casos sigue pendiente, sin robo automático de vinculación.
No existe transacción Auth–Firestore: verificación actual se hace antes del trabajo Firestore; una mutación concurrente de Auth exige reconciliación y no permite prometer atomicidad o revocación instantánea. Antes de producción debe cerrarse el protocolo de cambio de identidad y las escrituras antiguas.

### Ejecución local y pruebas

Node 22 portátil existente. No se instalaron dependencias: se reutilizó node_modules del worktree aislado mediante junction local ignorada; versiones directas existentes coinciden (Admin 14.5.0, Functions 7.4.0, TypeScript 5.8.3, Firebase CLI 15.31.0). Los lockfiles de ambos directorios difieren: esta ejecución NO acredita instalación congelada desde cero. Package/lock/pnpm-workspace no se modificaron.
Compilación `node node_modules/typescript/bin/tsc -p tsconfig.json`, pruebas `node --test lib/test/*.test.js`. Emulator: `node node_modules/firebase-tools/lib/bin/firebase.js emulators:start --only auth,firestore,functions --project demo-chaski-hu15 --config ../firebase.json` desde functions, Node22 en PATH del proceso, CHASKI_ROUTE_FIXTURE=enabled. No usar ADC ni proyectos reales.
Para arrancar sin búsquedas Cloud del secreto declarado, usar un .secret.local temporal solo si no existe, con HEIGIT_API_KEY=fixture-not-a-real-secret; nunca sobrescribir configuración personal. El primer arranque con variable de entorno intentó acceso a Secret Manager del proyecto DEMO y recibió 403, sin obtener secreto. Se corrigió mediante override ficticio temporal y se repitió la suite; archivo ficticio retirado al finalizar y emuladores detenidos. Sin llamadas reales HeiGIT.
Resultado final: 42 unitarias aprobadas (11 nuevas de política/validación/dobles Auth, 31 regresiones); 42 HTTP/Emulator aprobadas (19 nuevas del coordinador, 23 regresiones HU15); 0 fallos/omitidas. Concurrencia HTTP real con Promise.all, tres carreras aprobación/asignación por ejecución: exactamente una operación confirma y no quedan ambos permisos efectivos. Repetición recupera recibo sin auditoría duplicada. No equivale a prueba de carga Cloud.
Cobertura de los 18 escenarios solicitados: aprobación válida/rechazada; asignación a propietario; correo sin cuenta y vinculación posterior; UID ajeno; varios locales; revocación parcial/total; propietario suspendido; invitación sin falso conflicto; carrera simultánea; reintentos; revisión antigua; fallos de verificación (dobles); cambio de correo/token anterior; registro/reenvío incompatibles; legacy y reparación del índice. Extras: rollback ante local ajeno, índice privado incluso para admin cliente, obtención autorizada de revisión y recuperación tras cambio de correo. Nunca se prueba Firebase real.

### Pendientes y prioridad

P0: cerrar writes directos de aprobación/asignaciones y proteger REGISTER/RESUBMIT en 3A.3B; verificar reglas desplegadas después de autorización, migración controlada y compatibilidad clientes antiguos; frescura/credenciales anteriores sin atribuir atomicidad Auth–Firestore. Comprobar instalación congelada reproducible antes de integración/despliegue y resolver casos de identidad reciclada sin transferencias automáticas.
P1: adaptadores Android, driver de identidad compartido y hechos con origen/revisión; consultas y revocación UI preservando HU09/HU11, Address/Cart/Checkout; supervisión de preparaciones autorizadas, sin nueva interfaz administrativa aquí.
P2 propuesto: optimización de queries de coordinación (hoy varias lecturas por identidad/asignación), métricas y experiencia de administración adicional. Seguridad no se pospone a P2.
No se modificó Android, HU16, Rules, Firebase real, checkoutApi, dependencias ni configuración privada existente. Las rutas Cloud nuevas siguen cerradas; el commit de código no incluye docs. 3A.3B NO INICIADO.


## 16. HU34-3A.3B — implementación LOCAL y validación pendiente (2026-10-28)

### Alcance y estado
Implementación conservada sin staging ni commit de finalización. Base ramaDante: 518adee1ebaf63a1c0610e62d8160149d4a80b89. No se desplegó ni se probó Firebase Cloud. No avanzar a 3A.4/3B por esta anotación. El recorrido instrumentado Android sigue pendiente por fallo del sistema del AVD; no confundir compilación o salida Gradle satisfactoria con ejecución de casos.

### Android y componentes reutilizados
FirebaseBusinessRepository migra submitBusinessRequest/resubmitBusinessRequest a REGISTER/RESUBMIT y evaluateBusinessRequest(APPROVED) a APPROVE. OBSERVED/REJECTED y HU07 suspensión/reactivación conservan sus transacciones/auditorías. FirebaseStoreRepository migra assignManager (incluida sustitución de correo) a ASSIGN; revokeManager ofrece revocación total explícita y ASSIGN permite reducir locales. No se añadió interfaz visual de revocación. Los documentos REVOKED permanecen históricos y no se muestran como asignaciones activas.
Se reutilizan contratos de repositorio, ApiResult, FirebaseAccessControl, FirebaseAuth, Retrofit/OkHttp/Json existentes y BusinessDataModule/Hilt. Adaptadores necesarios: remote/CommercialApi define DTO y rutas; CommercialOperations conserva revisión, intento, errores y cancelación; FirebaseCommercialSessionSource captura la instancia FirebaseUser/UID/correo/verificación sin listeners globales; PreferenceCommercialAttemptStore guarda un intento privado por UID en SharedPreferences, sin tokens, Room ni colas. Los cambios de tres dobles JVM solo implementan el nuevo método del contrato.
El intento guarda operationId, payload y revisiones antes de enviar la mutación. Timeout/cancelación/503/resultado incierto conservan el mismo intento; otro payload no lo sobrescribe. Repetir recupera el recibo backend sin duplicar auditoría. No hay reintentos automáticos. Se distingue STALE_REVISION de errores temporales. Cambio de UID, instancia de sesión o verificación invalida resultados; el repositorio transmite la identidad capturada antes de validaciones suspendidas. No se promete atomicidad de sesión y publicación UI; integración global de identidad sigue siendo 3A.4.
BIND se utiliza solo al consultar asignaciones LOCAL y solo ante RECONCILIATION_REQUIRED de facts; no se ejecuta por recomposición ni como resolvedor global. La consulta manager usa email + boundUid + bindingState VERIFIED; los listeners de locales se retiran al sustituirse y al cerrar Flow. CONECTADO conserva su consulta anterior compatible con las reglas publicadas; las nuevas mutaciones se rechazan expresamente LOCAL_ONLY y nunca recurren a escrituras antiguas.

### Defectos concretos del contrato resueltos
GET /api/v1/access/revision permite obtener la revisión propia sin exigir hechos ya vinculados (REGISTER/RESUBMIT/BIND). POST assignment-revision admite businessId sin email exclusivamente para admin y obtiene el correo Auth del propietario al aprobar; no confunde el correo comercial de contacto con identidad. ASSIGN incorpora previousEmail/previousRevision para cambiar correo de una asignación atómicamente, revocando la fila anterior y conservando historia; conflicto en el destino no revoca la anterior. El índice UID añade authEmail, utilizado por Rules. PREPARE controlado debe regenerar metadatos antiguos antes de aplicar estas reglas; no existe migración automática de datos compartidos.

### Firestore Rules y accesos directos
Se cierran create/RESUBMIT/APPROVE cliente en business_requests y todos los writes cliente de store_managers; commercial_access_index deniega read/write incluso a clientes admin. Lecturas de asignaciones: admin, propietario aprobado o responsable con correo verificado, boundUid, estado VERIFIED e índice UID/correo concordantes. No se amplían queries: las reglas no son filtros.
Permanecen accesos legítimos: decisiones OBSERVED/REJECTED y HU07 auditadas; locales, horarios HU09, catálogo HU10 y configuración de oferta HU11 bajo las restricciones existentes. No se modifica Address, Cart, Checkout, Room, Storage ni documentos HU16. Se verifican propietario suspendido, otro usuario, índice privado y revocación con token anterior en Rules Emulator real.

### Evidencia ejecutada sobre el código pendiente
- Android LOCAL: testDebugUnitTest y assembleDebug, BUILD SUCCESSFUL; XML: 407 pruebas JVM, 0 fallos, 0 omitidas, 47 suites. Incluye 18 pruebas CommercialOperations (identidad, mismo UID/relogin, cambio de verificación, cancelación, respuesta perdida, repetición estable, revisión antigua, 401/503/conflicto, cambio de payload, BIND y cambio de correo).
- TypeScript: compilación correcta; 42 pruebas unitarias Functions aprobadas.
- HTTP contra Auth/Firestore/Functions Emulator demo-chaski-hu15: 54/54 aprobadas, 0 omitidas: 22 coordinador, 9 reglas reales y 23 regresiones HU15. Incluye concurrencia aprobación/asignación, invitación sin cuenta, revocación parcial/total, pérdida de respuesta y repetición, revisiones, cambio de correo atómico, payloads antiguos válidos rechazados, lecturas/consultas autorizadas y denegadas, HU07/HU09/HU10/HU11. Sin escrituras reales ni peticiones HeiGIT.
- Instrumentación nueva CommercialRepositoryEmulatorTest contiene dos casos: recorrido de repositorios reales con registro/reenvío/aprobación/local/asignación/HU07/revocación y persistencia real SharedPreferences. Una ejecución anterior pasó el caso de persistencia y falló conectando al Auth Emulator; NO acredita el recorrido. El nuevo intento con adb reverse no ejecutó casos: Pixel_7 no inicia package/activity; system_server cae al leer runtime-permissions.xml, EIO. Arranque en frío sin snapshots conserva el mismo fallo. No se borraron datos ni se debilitaron pruebas. Pendiente ejecutar ambos casos con AVD/dispositivo estable; el BUILD SUCCESSFUL de ese intento no es evidencia instrumentada.
- git diff --check sin errores (advertencia existente de conversión LF/CRLF en rules, sin error de whitespace). Dependencias y configuración Gradle no cambian. No se acredita instalación congelada nueva; se reutilizó infraestructura existente.

### Riesgos y condiciones posteriores
P0 para Cloud: rutas access siguen cerradas fuera de LOCAL; no desplegar reglas ni Android migrado como solución Cloud funcional. Requiere autorización, publicación coordinada, preparación del índice, comparación con reglas compartidas vigentes y plan para clientes antiguos. El índice incorpora authEmail; datos sin ese metadato fallan cerrados hasta preparación autorizada.
P0 de identidad pendiente: Rules no consulta Auth actual ni verifica revocación de tokens con Admin SDK; una credencial emitida antes de cambiar correo/verificación puede seguir coincidiendo con una vinculación aún no reconciliada. La API sí comprueba la identidad Auth actual y las reglas sí bloquean tras REVOKE/actualización del índice; esto NO demuestra revocación instantánea tras un cambio Auth. Debe cerrarse el protocolo de cambio/reconciliación de identidad en 3A.4 antes de despliegue; no se afirma protección completa del sistema. La prueba con token antiguo acredita revocación comercial ya confirmada, no todos los cambios Auth posibles.
P1 de cierre de este incremento: ejecutar el recorrido Android real pendiente y revisar cualquier fallo antes del commit de finalización. No se requiere alterar código de negocio para reparar el AVD; no se autorizó wipe. Preparación explícita sigue siendo necesaria para nuevas identidades; no hay PREPARE automático desde cliente.
P1 recuperación: un intento con resultado incierto y datos distintos queda bloqueado hasta recuperar el mismo payload; no hay pantalla nueva para resolver/descartar intentos ni garantía frente a corrupción/pérdida de almacenamiento. Datos mínimos comerciales del intento se conservan privadamente por UID; no contienen credenciales.
P2: métricas y optimizaciones posteriores solo si se justifican. No se realizaron pruebas manuales visuales ni carga Cloud.


### Cierre de validación HU34-3A.3B (2026-10-28)
Se resolvió el bloqueo de entorno creando HU34_Local_20261028 con datos nuevos e imagen instalada android-37.1/google_apis_playstore_ps16k/x86_64, Emulator 37.1.11.0. No se alteró ni se borró Pixel_7. El AVD nuevo confirma sys.boot_completed=1 y servicios package/activity disponibles; sin el fallo EIO observado en Pixel_7. No se atribuye una causa física concreta a ese EIO: solo se verificó fallo de lectura de su archivo de permisos y que no afecta al AVD nuevo.
Ejecución real LOCAL con emulator-5556, adb reverse de 9099/8089/5001 y hu34Host=127.0.0.1: CommercialRepositoryEmulatorTest, 2 casos aprobados, 0 fallos/errores/omitidos. Casos: repositoriosRealesRegistroAprobacionAsignacionRevocacionYHU07 (10.220 s) e intentoPrivadoSeRecuperaAlRecrearAlmacen (0.009 s). Evidencia XML: app/build/outputs/androidTest-results/connected/debug/TEST-HU34_Local_20261028(AVD) - 17.xml. El recorrido real valida también instalación, Auth/Firestore/Retrofit/foodApi y consulta manager con Rules. No se modificó código ni se debilitaron aserciones para obtener este resultado.
Nueva confirmación assembleDebug LOCAL: BUILD SUCCESSFUL. TypeScript recompilado; 42 unitarias Functions y 9 Rules Emulator reales repetidas, todas aprobadas. Se conserva la evidencia previa del mismo código de 407 JVM y 54 HTTP/Emulator; no se presentan como nuevas ejecuciones completas. Las dos pruebas Android pendientes quedan resueltas; el incremento resulta apto para su commit local autorizado. Persisten las restricciones LOCAL y los riesgos de identidad/reconciliación, preparación del índice y publicación coordinada ya documentados: no es autorización ni garantía de despliegue Cloud. No iniciar 3A.4.


## 17. HU34-3A.4 — identidad y contexto compartido LOCAL (2026-10-29)

### Componentes y responsabilidades
Se reutilizan FirebaseAuth compartido, SharedAccessIdentitySource, AccessIdentity/AccessFacts, AccessProvenance, EvaluateAccessEvent y ResolveAccess; Retrofit/OkHttp/Json existentes y la URL ELIGIBILITY_API_BASE_URL. No se modifican AuthUser ni FirebaseAuthRepository: su proyeccion no contiene verificacion, instancia de sesion y revision de credencial suficientes para este contrato. No se toman generaciones de Cart ni se alteran sus fuentes, Address o Checkout.
Archivos nuevos: core/access/data/FirebaseAccessIdentityDriver.kt (driver real y token solo Data), RemoteAccessFacts.kt (DTO, API y adaptacion restrictiva), SharedAccessContext.kt (estado compartido y coordinacion), AccessDataModule.kt (SingletonComponent y scope de proceso); RemoteAccessFactsTest.kt y SharedAccessContextTest.kt; androidTest/core/access/data/AccessContextEmulatorTest.kt.
Modificados: SharedAccessIdentitySource.kt, AccessFacts.kt y ResolveAccess.kt; functions/src/access/commercialAccessService.ts y functions/test/commercialAccessHttp.emulator.ts. No hay nuevas dependencias, colecciones, Rules, endpoints ni componentes visuales.

### Driver, generaciones y publicacion
Un IdTokenListener por instancia de fuente compartida, no por suscriptor/pantalla. Hilt conserva driver/fuente/contexto y CoroutineScope de proceso; se crean al solicitarlos, sin conexion a pantallas en este incremento. La observacion termina al cancelar el scope: awaitClose retira listener y trabajo de refresh. Pruebas usan scope cancelable. No conserva referencias a Activity.
El driver asigna clave opaca por instancia de FirebaseUser (nunca token), UID, correo, verificacion y claims admin obtenidos del SDK. SharedAccessIdentitySource conserva su generacion de sesion propia (incluye reingreso del mismo UID) y revision de identidad/estado; credentialRevision distingue señales nuevas incluso sin cambio de perfil. Callbacks se serializan, conservan la ultima señal en rafagas y trabajos anteriores se cancelan. El token no entra en Domain ni en StateFlow publico.
La fuente publica identidad y comprueba/publica resultados bajo la misma exclusion; el driver valida ademas clave, epoch y usuario/perfil SDK vigentes. SharedAccessContext agrega ticket por trabajo y descarta respuestas/errores viejos incluso si el transporte no coopera con cancelacion. La comprobacion final y publicacion no tienen suspension intermedia. Esta garantia ordena observaciones aceptadas en el proceso; no es una transaccion entre Firebase Auth, Firestore y la UI ni deteccion instantanea de un cambio remoto no notificado.

### Contrato de hechos y resolucion
Un GET autenticado access/facts por revalidacion aceptada, mediante Retrofit existente. No consultas cliente a store_managers por correo para fabricar roles. Checked exige DTO completo: UID igual a identidad capturada, revision segura no negativa, exclusivity VERIFIED, propiedad del UID correcto, binding VERIFIED del UID/correo verificado, referencias completas y estados validos, sin locales duplicados/ajenos. No se utiliza el indice derivado como fuente independiente de roles.
401, 403, 409 (preparacion/reconciliacion/conflicto) y 503 son errores tipados; respuesta incompleta/deserializacion invalida no produce cliente. AUTHORIZATION_CONFLICT del endpoint autenticado se adapta a ConfirmedConflict(session), sin inventar listas de propiedad/asignaciones ni revision servidor ausente; ResolveAccess conserva solo consulta personal historica. No se ejecuta BIND automatico desde el contexto. Reconciliacion/preparacion requieren flujo autorizado existente; pendiente no concede responsabilidad.
Ajuste backend expresamente autorizado: facts() consulta stores.where(businessId == actor.uid) solo cuando su business_requests esta APPROVED, dentro de la transaccion existente, devolviendo unicamente IDs/estados. SUSPENDED conserva ownership/motivo sin listado. El propietario no obtiene bandeja recibida ni nuevas compras; responsable obtiene ambitos de locales efectivos, no privilegios globales. HU09/HU11 permanecen bajo su politica y Rules anteriores.

### Contexto y vigencia
AccessContextState expone origin (generacion/revision local), resolution, serverRevision opcional, problema tipado y phase: LOADING, SIGNED_OUT, RESOLVED, ERROR, CONFLICT o UNAVAILABLE. Hay que respetar phase: durante carga/error inicial no existe cierre de sesion confirmado ni ausencia comercial acreditada. Estado inicial no concede capacidades. Todos los resultados pasan por EvaluateAccessEvent.
La revision servidor corresponde al indice comercial devuelto por el coordinador, NO a una version global de todos los documentos/estados de locales. Se rechaza retroceso de revision dentro de la misma sesion; revisiones iguales pueden contener cambios legitimos de HU07/locales y no se consideran cache valida por igualdad.
refresh() invalida y cancela antes de suspender; solicita reload() y renovacion forzada del ID Token, y obtiene hechos nuevos. Se ofrece para regreso a primer plano, reconexion y cambios comerciales; no existe aqui wiring automatico de navegacion, ConnectivityManager ni señales push de revocacion. Los callbacks de token no garantizan detectar cambios remotos de perfil; se requiere refresh explicito. Sin señal de servidor no se promete revocacion instantanea, especialmente sin red.
Una lease tecnica en memoria de 60 segundos invalida capacidades por espera relativa de corrutinas; no hace consultas periodicas ni persiste autorizaciones. Vencimiento exige revalidacion explicita. No representa un plazo de autorizacion del servidor ni elimina la necesidad de refresh al retomar/reconectar; no debe interpretarse como garantía de tiempo real mientras el proceso/dispositivo esta suspendido. No se reutiliza estado Ready tras error, carga, timeout, refresh o cambio de identidad. Sin modificaciones a Room ni borrado de carritos.

### Entorno y pruebas
LOCAL requiere la configuracion demo existente y servicios loopback; reader verifica modo LOCAL y proyecto demo-chaski-hu15 mediante configuracion Hilt. CONECTADO/Release exponen contexto UNAVAILABLE sin consultar las rutas nuevas Cloud, sin fallback ni cliente ficticio. No se modifican URLs, Gradle, JSON personal, Firebase Cloud o checkoutApi.
Resultados sobre el codigo final: assembleDebug LOCAL y TypeScript correctos; 433 pruebas JVM en 49 suites (26 nuevas: 11 HTTP/mapper/permisos y 15 contexto; 407 regresiones), sin fallos ni omisiones. Functions: 100/100 (42 unitarias + 58 HTTP/Emulator: 26 coordinador, 9 Rules reales, 23 HU15). Las cuatro HTTP nuevas cubren propietario con varios locales y exclusion ajena, suspendido sin listado, conflicto confirmado y estado de local inconsistente.
Instrumentacion LOCAL en HU34_Local_20261028, adb reverse 9099/8089/5001: dos nuevas pruebas de AccessContextEmulatorTest (reingreso, verificacion/correo/reconciliacion y propietario/suspension/responsable/revocacion) y dos de regresion CommercialRepositoryEmulatorTest; cada clase se ejecuta por separado y sus XML se conservan en .artifacts/hu34-3a4/context-results.xml y commercial-regression-results.xml, ignorados. No confundir la configuracion multiclase del primer intento (solo ejecuto los dos nuevos casos segun XML) con cuatro pruebas ejecutadas.
Cobertura JVM: consumidor compartido/listener unico/cancelacion, cambio UID/generacion/correo/verificacion, respuesta y error viejos, dos revisiones desordenadas, credencial invalidada antes de publicacion, timeout, refresh y recuperacion, lease sin polling, revision regresiva y entorno no habilitado; contrato parcial/invalido, 401/403/409/503, pendiente/UID ajeno sin falso conflicto, propietario/responsable sin compras ni bandeja de propietario, historial personal y ambitos de local. No se afirma prueba visual, carga Cloud, deteccion automatica de red o revocacion instantanea remota.

### Dependencias y riesgos posteriores
P0 antes de Cloud: publicacion coordinada de rutas HU34 aun cerradas en Cloud, indice preparado y reconciliacion de identidad/credenciales antiguas; Rules no consulta Auth actual y este contexto NO elimina la ventana de tokens anteriores en clientes que acceden directamente a Firestore. Verificar/reparar esa politica con autorizacion especifica antes de desplegar; no se tocaron Rules. La API y Rules siguen siendo la autoridad de operaciones y HU16 debe reautorizar compras en servidor. No interpretar capacidades visuales como garantia permanente.
P1 para HU34-3B: inyectar una sola fuente, consumir phase/resolution y scopes; invocar refresh al retomar, recuperar conexion y tras mutaciones comerciales; suspender consultas UI revocadas y mantener historico personal independiente. Sin wiring de pantallas, navegacion o ViewModels comerciales en 3A.4. Protocolo de preparacion/reconciliacion sigue autorizado, nunca BIND/preparacion silenciosos ni transferencia de asignaciones por correo.
P2: medir tamaño/costo de facts en negocios con muchos locales; ahora hay una consulta adicional de locales solo para propietario aprobado y no hay polling. No introducir cache global de permisos por eficiencia.

Cierre local: commit f0e3307cebcd43ab043792032b67820e8872860d — feat(HU34 - access): integrar identidad y autorizaciones comerciales. 12 archivos de codigo/pruebas, docs excluido. Laboratorios detenidos y configuraciones ficticias temporales retiradas; JSON personal y HU16 conservan hashes. Sin push ni despliegue. No iniciar HU34-3B automaticamente.


## 18. HU34-3B.1 — decisiones aprobadas y propuesta Pencil (2026-10-29)

### DEC-HU34-26 — Navegación según experiencia
**APROBADA por el usuario.** Conservar MAIN y sus ámbitos compartidos. Cliente: Inicio, Buscar, Mis compras, Cuenta. Propietario: Mi negocio, Cuenta. Responsable: Mi local/Mis locales, Cuenta. Administrador: sección Administración dentro de Cuenta, adicional a su experiencia comercial. No crear una cuarta experiencia ni reconstruir innecesariamente ámbitos de Cart/Address; respetar el ámbito propio de Checkout.

### DEC-HU34-27 — Inicio de propietario y responsable
**APROBADA por el usuario.** Propietario aprobado: estado de negocio, Locales y responsables, Catálogo general, Horarios y Oferta/precios por local. Responsable: local(es) asignado(s), selector cuando corresponda, estado del local y accesos autorizados a horarios/disponibilidad y oferta/precios. Propietario suspendido: conserva experiencia e identidad propietaria, muestra estado y motivo, sin listado ni gestión de locales; conserva funciones personales autorizadas. Sin compras nuevas ni bandeja recibida para propietario. No atribuir edición general de negocio aprobado ni apelación implementada.

### DEC-HU34-28 — Administración Chaski
**APROBADA por el usuario.** Agrupar Solicitudes de negocios, Supervisión comercial y Registro de auditoría dentro de Cuenta, sección Administración Chaski. No crear dashboard administrativo, ni derivar propiedad/responsabilidad/bandeja recibida del claim admin. La sección requiere autorización administrativa comprobada y coexiste con la experiencia comercial vigente.

### Documento y alcance real del diseño
Conexión Pencil efectiva por MCP. Documento existente: archivo `.pen` local de Dante. Se añadió una sección HU34 a la derecha de los diseños existentes, desde x≈8777, y≈-3619. Buscar en Layers: HU34 / 00 Guía de revisión (qHmvY). No se creó ni renombró otro .pen ni se eliminaron diseños originales. Referencias históricas originales Home W87Ga y Account settings yMXFB conservadas; sus copias nuevas son X1XB8 y SFad5. Estas referencias NO son capturas actuales de Android; algunas imágenes heredadas dependen de recursos locales ausentes. La propuesta cliente usa un placeholder intencional del local, sin imagen rota ni datos de Firebase.
Frames de propuesta realmente creados:
- lakue: HU34 / Cliente · Inicio; Inicio/Buscar/Mis compras/Cuenta, dirección y carrito HU14.
- yF5v5: HU34 / Propietario aprobado · Mi negocio; cuatro accesos existentes, sin compras/bandeja.
- MRb4p: HU34 / Propietario suspendido · Mi negocio; motivo/restricción y Cuenta, sin locales/gestión/apelación.
- JlScn: HU34 / Responsable · uno; local autorizado, estado, horarios y oferta.
- MThyi: HU34 / Responsable · varios; selector compacto y variante desplegada junto al control; sin pantalla adicional.
- F2R22: HU34 / Cuenta · Administración Chaski; ejemplo propietario con autorización admin, perfil/seguridad y tres módulos admin. Documento de diseño muestra contenido completo desplazable.
- fcdZt: HU34 / 08 Estados compartidos; ocho variantes: Verificando permisos, Error temporal, Sin conexión confirmada, Conflicto, Reconciliación pendiente, Revocación, Cambio de cuenta y Contexto no disponible. Carga/cambio/contexto no disponible no muestran CTA operativo. Ningún error convierte automáticamente en cliente.
Ocho secciones de revisión: Referencia visual, Cliente, Propietario aprobado, Propietario suspendido, Responsable uno, Responsable varios, Administración en Cuenta y Estados compartidos. Guía y notas de correspondencia/prioridades (Zv33L) complementan el lienzo. No hay prototipo interactivo verificado: los recorridos se explican mediante anotaciones estáticas; el API disponible no documenta conexiones de prototipo.

### Componentes, estilo y diferencias
Reutilizados como instancias los botones HU04 wAE0u (principal) y e63IQ (secundario), con etiqueta/font de la propuesta. No se cambiaron sus componentes originales ni variables HU04. Se añadieron R2YspC (acceso comercial reutilizable), RtI4n (navegación comercial) y d8I9Q (panel de autorización con ocho variantes). Paleta comprobada contra Color.kt: rojo #D42323, verde #0C9F3E, texto #212121, admin #1565C0; fondos #FBFBFB/#FFFFFF, separadores #E0E0E0. Roboto representa SansSerif Android, sin modificar Type.kt. Material 3 plano, sin barra flotante iOS; espaciado 4/8/12/16/24/32, botones de 48 y filas de 76. Iconos Lucide de equivalencia semántica a Material, sin redibujar logos/ilustraciones.
Respecto de la UI actual: Cuenta se agrupa, inicio se adapta por experiencia, negocio/local activo queda explícito y las acciones se restringen al ámbito. Nombres, estados y motivos están identificados como ejemplos; no se deducen nombres/direcciones de AccessFacts. Los nombres reales se obtendrán mediante repositorios existentes, una vez autorizado el alcance.
HU16/17/19–21/28/35 permanecen como anotaciones futuras fuera de las acciones operativas. Mis compras conserva su lugar aprobado, pero el historial productivo sigue pendiente HU17. No hay botón de confirmación, pedido recibido, indicadores, edición general ni apelación aparentando funcionar.

### Correspondencia Pencil → Compose para futura implementación
| Frame | Componente actual | Acción futura | Prioridad |
|---|---|---|---|
| Cliente lakue | MainScreen, HomeScreen, ChaskiBottomNav, CartAccessButton | Adaptar accesos y etiquetas; reutilizar flujo cliente | P0/P1 |
| Propietario yF5v5 | BusinessStatusScreen y accesos ManageStores/ManageCatalog/StoreOperations/StoreProductConfiguration | Adaptar inicio, no duplicar repositorios/ViewModels | P1 |
| Suspendido MRb4p | BusinessStatusScreen | Reutilizar estado y reforzar restricciones visibles | P0/P1 |
| Responsable JlScn/MThyi | StoreOperationsScreen, StoreProductConfigurationScreen y su StoreSelector | Adaptar selección y ámbito; sin nueva pantalla de selección | P0/P1 |
| Cuenta F2R22 | AccountScreen, ProfileViewModel y destinos administrativos | Reagrupar según capacidades/claim independiente | P0/P1 |
| Estados fcdZt | StateContent, LoadingState, ErrorState, EmptyState | Adaptar composición específica de autorización | P0 |

### Revisión, prioridades y pendientes
P0: impedir acciones fuera de capacidad/ámbito; no mostrar contenido anterior en cambio de UID/local; no habilitar cliente por error ni administrar por claim fuera de alcance; no activar contexto HU34 Cloud sin publicación coordinada. La visibilidad no reemplaza autorización de servidor/Rules.
P1: tareas frecuentes a un toque, selector junto al contexto, estados legibles y Cuenta agrupada. P2: validar texto ampliado, contraste y pantallas pequeñas con Compose real; revisión visual no sustituye pruebas de accesibilidad Android.
Revisión mediante capturas reales MCP y bounds: se corrigieron iconos no disponibles y layout de selector; los CTA deshabilitados de estados se excluyen de renderizado. Las referencias históricas conservan sus limitaciones; no se audita todo el lienzo antiguo como parte de HU34.
Exportaciones reales: carpeta local de exportaciones (no versionada): yF5v5.png, MThyi.png y fcdZt.png. Son capturas exportadas de Pencil, no renders inventados.
Las tres decisiones anteriores están aprobadas; la distribución visual concreta, iconos, textos de estados, tamaños y agrupación final de Cuenta requieren revisión del usuario. No se modificaron Kotlin, navegación, Firebase, Rules, Gradle, HU16 ni .gitignore. No se ejecutaron builds/pruebas de aplicación porque no hubo cambios de código. Sin commit, push o despliegue. docs permanece ignorado.

## 19. HU34-3B.1 — ajustes visuales finales y DEC-HU34-29 (2026-10-29)

### DEC-HU34-29 — Preservar la interfaz actual del cliente
**APROBADA por el usuario.** Se conserva íntegramente la presentación Android del cliente: MainScreen, HomeScreen, navegación inferior y nombres actuales (Inicio, Buscar, Pedidos, Cuenta), búsqueda, catálogo, detalle, carrito, direcciones y Checkout. La propuesta de etiquetas de DEC-26 y la correspondencia anterior que sugería adaptar el cliente quedan sustituidas por esta decisión. Las futuras guardas técnicas no autorizan cambios visuales.
El frame lakue permanece sin cambios y no se implementará. Anotación externa: “REFERENCIA NO APROBADA PARA IMPLEMENTACIÓN. La experiencia visual actual del cliente se conserva por decisión del equipo.” Se comprobó igualdad de su representación antes/después. La guía y la correspondencia Pencil–Compose reflejan esta restricción.

### Frames conservados, ajustados y variantes
Se mantienen las bases yF5v5 (propietario aprobado), MRb4p (suspendido), JlScn (responsable uno), MThyi (responsable varios), F2R22 (Cuenta administrativa) y fcdZt (ocho estados). Sin eliminar diseños anteriores ni sustituir la experiencia del cliente.
- Propietario: accesos existentes, indicación de selección por local; variante sin locales ULgsK, catálogo disponible y mensaje sobre registrar primero un local.
- Suspendido: motivo ilustrativo identificado, alternativa sin motivo j5yaA, sin gestión/listado ni apelación; se oculta el CTA redundante a Cuenta.
- Responsable: asignación verificada, negocio aprobado, estado administrativo y estado operativo separados; atención derivada del horario identificada como ejemplo. ACTIVE no significa abierto.
- Varios locales: MThyi conserva selector desplegado; Nqb6P cerrado, H3hKoH local seleccionado, vs3y3 local sin acciones, d6Kz1 selección después de permisos actualizados. Durante revocación/revalidación se utiliza el estado restringido, sin mantener datos privados anteriores.
- Administración F2R22: Cuenta agrupada y desplazable, con autorización administrativa vigente; no dashboard ni bandeja recibida.
- fcdZt conserva ocho variantes. Conflicto requiere revisión autorizada; reintento solo vuelve a consultar. Reconciliación no vincula automáticamente. Revocación retira acceso previo. Error no concede cliente. Cambio de cuenta oculta datos anteriores. Sin conexión solo con evidencia de conectividad ausente.
- QfhvR: anotación externa de compras personales históricas futuras en Cuenta (también propietario/responsable), pendiente HU17. No pantalla ni botón funcional, ni reactivación de compras nuevas.

### Componentes, prioridades y correspondencia futura
Se reutilizan botones HU04 y componentes comerciales R2YspC, RtI4n y d8I9Q. uqwux muestra una cabecera reutilizable de edición por local; no crea un segundo flujo comercial.
P0 aplicado visualmente: cliente intacto; ninguna acción fuera de ámbito; separación de estados; revocación/cambio de cuenta sin datos anteriores; no atribuir al reintento resolución de conflictos.
P1: propietario sin locales, suspensión sin motivo, cinco estados de selector, identificación del local y eliminación del acceso redundante a Cuenta.
P2: muestra hR2ko de 320 dp con texto al 130 %, ajustes de altura y legibilidad; contraste calculado texto principal/blanco 16.10:1, secundario/blanco 6.19:1 y rojo/blanco 5.17:1. Revisión estructural final sin recortes visibles en bases, variantes, guía y notas (se excluyen subárboles deshabilitados). Esto no certifica accesibilidad Android ni sustituye pruebas de fuente dinámica, lector de pantalla y objetivos táctiles en dispositivo.
Correspondencia futura: cliente conserva todos sus componentes/etiquetas; propietario/suspendido reutilizan BusinessStatusScreen y destinos existentes; responsables reutilizan StoreOperationsScreen, StoreProductConfigurationScreen y StoreSelector; F2R22 adapta únicamente la sección administrativa autorizada de AccountScreen, sin rediseñar Cuenta del cliente; estados reutilizan composiciones actuales. No duplicar repositorios, ViewModels ni pantallas comerciales.

### Entrega y pendientes
Documento guardado: archivo `.pen` local de Dante, sección HU34 / 00 Guía de revisión y área de ajustes finales. Exportaciones PNG reales: carpeta local de exportaciones (no versionada)  (ULgsK, j5yaA, JlScn, MThyi, Nqb6P, H3hKoH, vs3y3, d6Kz1, hR2ko, F2R22, fcdZt, uqwux).
DEC-29 está aprobada; las variantes visuales finales y su traducción a Compose requieren revisión final del usuario. Pendientes: accesibilidad real en Android y wiring técnico autorizado de HU34-3B.2; no se promete revocación instantánea ni autorización por visibilidad. HU17/HU35 siguen pendientes.
Solo cambios visuales Pencil y este documento ignorado. Sin cambios Kotlin, Compose, navegación, ViewModels, repositorios, Functions, Rules, Gradle, HU16, configuraciones privadas o Firebase. Sin staging, commit, push ni despliegue. No se ejecutan pruebas de aplicación al no existir cambios de código.

## 20. HU34-3B.2A — navegación, sesiones y acciones protegidas (2026-10-29)

### Alcance y organización
Implementación exclusivamente LOCAL sobre rama ramaDante, base f0e3307cebcd43ab043792032b67820e8872860d. Sin nueva experiencia comercial de Pencil, sin HU16 y sin cambios de backend/Rules. La autorización definitiva sigue en servidor.
ResolveAccess continúa decidiendo capacidades; navegación únicamente las consume. Se incorporó RESUBMIT_BUSINESS y lectura de solicitud propia para conservar los flujos existentes sin reproducir la regla observado/rechazado en Presentation. StoreAuthorization es un contrato puro consumido por los dos ViewModels por local; CurrentStoreAuthorization deriva del SharedAccessContext existente, sin nuevo resolvedor, listener Auth ni fuente de sesión.
Los archivos de navegación se ubican en core/navigation existente. Domain permanece libre de Android/Firebase/Compose. No se añadieron carpetas de producción ni infraestructuras HTTP, tablas o repositorios.

### Matriz de destinos y acciones
| Destino/acción | Condición confirmada |
|---|---|
| Splash, onboarding y acceso/recuperación/verificación | Públicos; no conceden capacidades privadas |
| MAIN, Cuenta, perfil, direcciones y funciones personales existentes | Contexto Resolved e identidad vigente; sin convertir error/conflicto en cliente |
| Inicio/Buscar de compras, restaurantes/producto, carrito y Checkout | START_PURCHASE con Account del UID actual |
| Rutas demostrativas de compra/pedido existentes | START_PURCHASE; no acreditan historial productivo HU17 |
| Estado de negocio | READ_OWN_BUSINESS_STATUS o REQUEST_BUSINESS para entrada al registro |
| Registrar negocio | REQUEST_BUSINESS; no se concede a responsables/propietarios |
| Reenviar solicitud | RESUBMIT_BUSINESS, concedida por Domain únicamente a solicitud propia observada/rechazada |
| Gestión de locales/responsables | MANAGE_OWN_STORES sobre negocio propio; validaciones existentes del ViewModel conservadas |
| Catálogo general | MANAGE_BUSINESS sobre negocio propio |
| Horarios/oferta por local | OPERATE_HOURS_AND_OFFERS con BusinessId y StoreId exactos |
| Solicitudes/supervisión/auditoría administrativa | Identidad vigente con isAdmin verificado, independiente de propiedad/responsabilidad |
| Rutas no reconocidas/protegidas sin datos confirmados | Denegadas |
No se habilitan pedidos recibidos por propiedad o claim admin. El responsable no obtiene gestión del negocio ni nuevas compras. El propietario suspendido conserva estado/funciones personales, sin gestión/listado ni compras. La visualización comercial definitiva corresponde a 3B.2B.

### Sesiones, ciclo de vida y vigencia
Cada entrada privada se vincula a generación de SharedAccessIdentity y una época del host para impedir restauración de otro proceso; esta época no crea otra sesión Auth. Las acciones capturan AccessProvenance (UID/generación/revisión) y la verifican bajo la misma exclusión que valida credenciales y lease antes de despachar. No basta una comparación seguida de otra suspensión. Los eventos retenidos de A no se autorizan con los permisos posteriores de B, aunque ambos tengan la misma capacidad.
Los cambios de cuenta ocurridos en rutas públicas también invalidan la navegación privada pendiente, sin omitir el flujo público de teléfono. Guardas vuelven a comprobar destino, argumentos y ámbito al restaurar o usar Atrás. La confirmación global de carrito exige coincidencia de UID, generación del destino y MAIN; no se muestra estado de otra sesión mientras se limpia el back stack.
MAIN y sus ViewModels Cart/Address se reutilizan. Se crea MAIN solo si falta o si un cambio de identidad/restauración exige reemplazarlo. Los ámbitos privados conservan estado durante revalidación temporal; se liberan trabajos al detenerse el destino, al retirarlo del stack o al cambiar su autorización. Los selectores eliminan datos/selecciones anteriores antes de observar nuevos ámbitos. No se eliminan carritos, datos personales ni tablas Room.
Un observador del host refresca al retornar de segundo plano y ante transición de conectividad VALIDATED recuperada. Las salidas de operaciones comerciales relevantes fuerzan hechos nuevos; refrescos equivalentes pendientes se agrupan. No hay polling ni consultas por recomposición. La lease sigue siendo de 60 s, con elapsedRealtime Android y comprobación al ejecutar acciones; no depende solo de que el temporizador haya emitido. Reload/token tienen límite de 12 s, lectura de hechos conserva su límite y refresco de UI queda acotado a 25 s.
Carga, error, conflicto, reconciliación y contexto no disponible restringen acceso con componentes existentes. El error no es cliente ni equivale automáticamente a ausencia de Internet. La señal de red se usa para recuperación, no para afirmar que cualquier fallo HTTP es desconexión. No se promete revocación remota instantánea; APIs/Rules vuelven a autorizar operaciones.
LOCAL activa esta integración. CONECTADO/Release mantienen HU34 sin activar ni consultar sus endpoints Cloud aún no disponibles; esto no declara HU34 apta para Cloud. Gradle, URLs y JSON personales no se modifican.

### Inventario revisado (39 archivos; documentación ignorada adicional)
| Archivo | Responsabilidad |
|---|---|
| `app/src/androidTest/java/com/chaskifood/app/core/access/data/AccessContextEmulatorTest.kt` | Conserva expectativas/25 s; añade diagnóstico de fase/problema sin datos sensibles ante timeout. |
| `app/src/androidTest/java/com/chaskifood/app/core/navigation/AccessHostEmulatorTest.kt` | Grafo real con Hilt, Auth/Firestore/Functions Emulator; editores, suspensión, Checkout y scopes compartidos reales. |
| `app/src/androidTest/java/com/chaskifood/app/core/navigation/AccessNavigationTest.kt` | 14 pruebas de navegación Compose: acceso directo, Atrás, sesión, restauración, lease y eventos antiguos. |
| `app/src/main/AndroidManifest.xml` | Permiso normal ACCESS_NETWORK_STATE para el único observador del host. |
| `app/src/main/java/com/chaskifood/app/core/access/data/AccessDataModule.kt` | Reutiliza SharedAccessContext y provee reloj elapsedRealtime y autorización por local. |
| `app/src/main/java/com/chaskifood/app/core/access/data/CurrentStoreAuthorization.kt` | Adaptador de lectura del contexto único; no consulta servicios ni resuelve roles. |
| `app/src/main/java/com/chaskifood/app/core/access/data/FirebaseAccessIdentityDriver.kt` | Acota reload/token a 12 s; conserva listener único y descarte por época. |
| `app/src/main/java/com/chaskifood/app/core/access/data/SharedAccessContext.kt` | Valida vigencia monotónica y credencial al despachar; invalidación y publicación sincronizadas. |
| `app/src/main/java/com/chaskifood/app/core/access/domain/AccessResolution.kt` | Añade capacidad RESUBMIT_BUSINESS sin dependencias de plataforma. |
| `app/src/main/java/com/chaskifood/app/core/access/domain/ResolveAccess.kt` | Concede lectura de solicitud propia y reenvío observado/rechazado en Domain. |
| `app/src/main/java/com/chaskifood/app/core/access/domain/StoreAuthorization.kt` | Contrato puro, obligatorio y reutilizable para capacidades por negocio/local. |
| `app/src/main/java/com/chaskifood/app/core/navigation/AccessNavigationEvents.kt` | Único observador de ciclo de vida/red; limpieza de back stack y refresco por eventos. |
| `app/src/main/java/com/chaskifood/app/core/navigation/AccessNavigationGuard.kt` | Impide componer contenido privado sin autorización; delimita ViewModels, saved state y acciones. |
| `app/src/main/java/com/chaskifood/app/core/navigation/AccessNavigationPolicy.kt` | Mapea destinos a capacidades y ámbitos, reinicio controlado y procedencia compartida. |
| `app/src/main/java/com/chaskifood/app/core/navigation/AccessNavigationViewModel.kt` | Proyección de contexto/identidad, refresco agrupado o forzado y despacho ligado al origen. |
| `app/src/main/java/com/chaskifood/app/core/navigation/ChaskiNavHost.kt` | Aplica guarda a destinos registrados, conserva MAIN y protege acciones/confirmación compartida. |
| `app/src/main/java/com/chaskifood/app/core/navigation/MainTabNavigation.kt` | Reutiliza MAIN; prepara la entrada únicamente si una ruta directa carece de ella. |
| `app/src/main/java/com/chaskifood/app/feature/address/presentation/AddressScreen.kt` | Guarda técnica de eventos de AddressRoute/mapa; sin cambios en mapas, repositorio ni persistencia. |
| `app/src/main/java/com/chaskifood/app/feature/business/presentation/AdminBusinessReviewScreen.kt` | Guarda común en evaluación; conserva UI y lógica existente. |
| `app/src/main/java/com/chaskifood/app/feature/business/presentation/AdminBusinessSupervisionScreen.kt` | Guarda común en suspensión/reactivación general e individual. |
| `app/src/main/java/com/chaskifood/app/feature/business/presentation/ManageStoresScreen.kt` | Guarda común en guardado de locales y asignación de responsables. |
| `app/src/main/java/com/chaskifood/app/feature/business/presentation/RegisterBusinessScreen.kt` | Guarda común en carga/reenvío de solicitud; consume capacidad de Domain. |
| `app/src/main/java/com/chaskifood/app/feature/business/presentation/StoreOperationsScreen.kt` | Vincula acciones operativas al origen de la pantalla además del filtro del ViewModel. |
| `app/src/main/java/com/chaskifood/app/feature/business/presentation/StoreOperationsViewModel.kt` | Filtra ámbitos confirmados, cancela observación revocada y verifica acciones por local. |
| `app/src/main/java/com/chaskifood/app/feature/cart/presentation/CartScreen.kt` | Protege cantidad, eliminación y entrada a Checkout; contenido y dinero intactos. |
| `app/src/main/java/com/chaskifood/app/feature/catalog/presentation/ManageCatalogScreen.kt` | Guarda común en categoría/producto y activación; sin cambios de catálogo o Storage. |
| `app/src/main/java/com/chaskifood/app/feature/catalog/presentation/StoreProductConfigurationScreen.kt` | Protege selección y guardado con origen explícito; conserva UI. |
| `app/src/main/java/com/chaskifood/app/feature/catalog/presentation/StoreProductConfigurationViewModel.kt` | Filtra selector, elimina selección revocada y comprueba ámbito concreto antes de guardar. |
| `app/src/main/java/com/chaskifood/app/feature/checkout/presentation/RealCheckoutScreen.kt` | Guarda técnica en eventos/reanudación; no modifica precios, cotización ni lógica de Checkout. |
| `app/src/main/java/com/chaskifood/app/feature/main/presentation/MainScreen.kt` | Conserva cliente; bloquea Home/Buscar de compras para cuentas comerciales con componentes actuales. |
| `app/src/main/java/com/chaskifood/app/feature/profile/presentation/AccountScreen.kt` | Protege cierre de sesión frente a eventos antiguos; no modifica diseño ni etiquetas. |
| `app/src/main/java/com/chaskifood/app/feature/profile/presentation/ProfileSettingsScreen.kt` | Protege actualización personal frente a eventos de otra sesión; no modifica diseño. |
| `app/src/test/java/com/chaskifood/app/core/access/data/SharedAccessContextTest.kt` | Prueba lease sin temporizador, credencial invalidada e invalidación/recuperación de segundo plano. |
| `app/src/test/java/com/chaskifood/app/core/access/domain/OperationTestAuthorization.kt` | Doble explícito para aislar pruebas antiguas; nunca se registra en producción. |
| `app/src/test/java/com/chaskifood/app/core/access/domain/ResolveAccessTest.kt` | Comprueba lectura y reenvío por estado de solicitud y ámbito propio. |
| `app/src/test/java/com/chaskifood/app/core/navigation/AccessNavigationPolicyTest.kt` | Matriz de capacidades, scopes, admin, solicitudes, reconexión y diálogo compartido. |
| `app/src/test/java/com/chaskifood/app/core/navigation/AccessNavigationViewModelTest.kt` | Refrescos equivalentes/forzados, respuestas antiguas, timeout y eventos diferidos. |
| `app/src/test/java/com/chaskifood/app/feature/business/presentation/StoreOperationsViewModelTest.kt` | Conserva regresiones y añade filtrado de locales/revocación con aserciones reales. |
| `app/src/test/java/com/chaskifood/app/feature/catalog/presentation/StoreProductConfigurationViewModelTest.kt` | Conserva regresiones y prueba exclusión de locales/selección revocada. |

### Validación y evidencia
- assembleDebug LOCAL: BUILD SUCCESSFUL. 456 pruebas JVM en 51 suites: 0 fallos, errores u omisiones (23 nuevas frente a 433 de base).
- AVD HU34_Local_20261028, loopback mediante adb reverse 9099/8089/5001; Auth, Firestore y Functions Emulator con demo-chaski-hu15 y Node 22. Sin Firebase real ni HeiGIT real.
- 46 pruebas instrumentadas efectivas, 0 fallos/errores/omisiones en los informes finales: 14 nuevas de navegación, 1 nueva del host real; regresiones 3 MAIN, 11 Cart Compose, 4 Checkout Compose, 9 coordinador Room, 2 contexto real y 2 repositorios comerciales.
- El host real verifica propietario aprobado/suspendido, inyección de editores, bloqueo de compras y de locales, entrada cliente a Checkout/direcciones y conservación por identidad de las instancias Hilt CartViewModel/AddressViewModel del MAIN existente.
- Cobertura incluye scopes ajenos, revocación parcial/total, mismo UID con otra generación, cambio de UID/correo/verificación, respuestas viejas, eventos en cola autorizados para otra cuenta, back stack restaurado, error/reintento, monotonicidad y clic expirado antes de emisión del temporizador. La reconexión se comprueba con señal controlada/contador; no se afirma una prueba completa de radios físicos.
- XML por clase en .artifacts/hu34-3b2a/*-results.xml, ignorados. No confundir compilación androidTest con ejecución efectiva. No se modificaron expectativas para aprobar.
- Incidencias del proceso: una ejecución de contexto agotó 25 s; repeticiones posteriores aprobaron sin ampliar el límite. Se añadió diagnóstico de fase/problema y queda pendiente estabilizar/reproducir ese caso intermitente. La primera regresión comercial usó el host predeterminado 10.0.2.2 y falló por conectividad; aprobó usando su argumento existente hu34Host=127.0.0.1. Los fallos iniciales de harness/imports se corrigieron antes de las ejecuciones finales.
- No se midieron lecturas facturadas ni latencia general. Los contadores comprueban ausencia de nuevas consultas de hechos por recomposición/refrescos equivalentes; las revalidaciones forzadas se conservan por consistencia.

### Riesgos y siguientes pasos
Sin P0 LOCAL detectados en las verificaciones finales. Antes de Cloud siguen siendo condición obligatoria la publicación coordinada de endpoints HU34, preparación/reconciliación de índices/identidades y revisión del riesgo de credenciales anteriores ya registrado; no se modificaron Rules ni se habilita despliegue.
P1: investigar timeout intermitente de integración y ampliar pruebas de reconexión con red real cuando corresponda. Las autorizaciones de memoria no sustituyen comprobación de servidor ni detección instantánea de cambios remotos.
P2/3B.2B: implementar únicamente tras autorización las interfaces comerciales/paneles definitivos de Pencil; conservar DEC-HU34-29, etiquetas, componentes y estilo del cliente. No se presenta todavía una nueva experiencia comercial ni historial HU17.
La plantilla demo temporal y el secreto ficticio se retiraron tras verificar su integridad; el JSON personal de raíz no se sobrescribió. Se detuvieron los emuladores iniciados y se retiraron los reverses propios. Para repetir LOCAL se debe seleccionar la plantilla demo según README sin sobrescribir configuraciones personales. Node portátil se conserva fuera del proyecto. Sin código Functions, Rules, persistencia Cart/Address, precio/cotización, Firebase real o Pencil modificados. Sin push ni despliegue.

Cierre local HU34-3B.2A: commit cbfb6a2e69b3559fd884111af0b5ef58195a9bd6 — feat(HU34 - navigation): proteger rutas y sesiones según permisos. 39 archivos incluidos, árbol Git limpio; documentación ignorada. Sin push/despliegue. No iniciar HU34-3B.2B sin autorización.

## 21. HU34-3B.2B — Interfaces comerciales por permisos (2026-10-29)

Referencia de entrada: cbfb6a2e69b3559fd884111af0b5ef58195a9bd6, rama ramaDante. Implementación y verificación exclusivamente LOCAL; no habilita Cloud ni inicia 3B.3.

### Experiencias y correspondencia Pencil
- yF5v5 / ULgsK: Mi negocio aprobado, identidad real desde BusinessRepository, accesos a ManageStoresScreen (locales y responsables), ManageCatalogScreen, StoreOperationsScreen y StoreProductConfigurationScreen. Sin locales: mensaje explícito, registro por el flujo existente y catálogo conservado; no ofrecer operaciones ficticias. Un error de consulta no se representa como lista vacía.
- MRb4p: propietario suspendido conserva identidad y motivo real o texto alternativo. No se consulta ni muestra listado de locales, gestión, operaciones o compras nuevas. Cuenta está en la barra; sin botón redundante ni apelación HU35.
- JlScn / MThyi: responsable con uno/varios locales, título Mi local/Mis locales, selector compacto reutilizado, estados Activo/Inactivo/Suspendido separados de Abierto/Pausado/Cerrado y del horario. La asignación verificada no equivale a autorización operativa. Local inactivo puede seguir asignado y visible sin botones operativos. No se consulta el documento administrativo del negocio del responsable.
- F2R22: Cuenta agrupa los tres destinos existentes bajo Administración Chaski solo con identidad/claim vigente del contexto autorizado. Conserva funciones personales y salida; las funciones comerciales se abren desde la primera pestaña. No crea dashboard ni historial ficticio HU17.
- fcdZt: un panel con ocho variantes: verificación, error temporal, desconexión confirmada, conflicto, reconciliación, acceso revocado/no autorizado, cambio de cuenta y contexto no disponible. Un error HTTP por sí solo no afirma desconexión: el guard consulta ausencia de red activa en Android. Reintentar consulta, pero no promete resolver conflictos ni vincular cuentas automáticamente.
- lakue no se implementó ni modificó. Cliente confirmado mantiene Inicio, Buscar, Pedidos, Cuenta y sus layouts, colores, Home, búsqueda, carrito, direcciones y Checkout.

### Arquitectura y responsabilidad de archivos
Rutas Kotlin relativas a app/src/main/java/com/chaskifood/app/:
| Archivo | Responsabilidad |
| --- | --- |
| feature/business/presentation/CommercialHomeState.kt (nuevo) | Estados visuales inmutables y procedencia de sesión para negocio/locales; no resuelve permisos. |
| feature/business/presentation/CommercialHomeScreen.kt (nuevo) | Contenedor con observación de ciclo de vida y Content puro de experiencias aprobadas; despacho ligado al origen y capacidad exacta. |
| feature/business/presentation/BusinessViewModel.kt | Reutiliza BusinessRepository y StoreRepository existentes después de comprobar capacidades en SharedAccessContext; comparte la observación propia, filtra locales autorizados, descarta origen anterior y mantiene selección. |
| feature/business/domain/StoreModels.kt | Extrae el predicado puro isWithinOperatingHours sin alterar isAvailableForOrders; evita duplicar reglas de horario en Presentation. |
| ui/components/StoreSelector.kt (extraído) | Selector existente reutilizable; estados administrativos opcionales y Preview desplegado. Sin nueva pantalla de selección. |
| feature/main/presentation/MainScreen.kt | Mismo contenedor y controlador MAIN; cliente conserva cuatro pestañas y comercial usa dos, sin recrear Cart/Address. |
| feature/main/presentation/CommercialBottomNav.kt (nuevo) | Contenido puro de barra Mi negocio/Mi local/Mis locales y Cuenta. |
| feature/profile/presentation/AccountScreen.kt | Contenedor y AccountContent puro; variante comercial y sección administrativa comprobada. Presentación habitual de cliente no administrador conservada. |
| core/navigation/AuthorizationPanel.kt (nuevo) | Presentación reutilizable de restricciones y mapeo de estado existente; sin nuevos permisos o servicios. |
| core/navigation/AccessNavigationGuard.kt | Mantiene guarda antes de componer y selección por scope; muestra paneles restrictivos. |
| core/navigation/AccessNavigationPolicy.kt | Reconoce argumentos opcionales del editor y conserva comprobación por local/negocio. |
| core/navigation/AccessNavigationEvents.kt | Comprueba storeId solo para editores comerciales; los IDs de rutas de producto cliente no se interpretan como autorización comercial. |
| core/navigation/ChaskiNavHost.kt | Reutiliza destinos productivos, transmite storeId opcional y mantiene barra comercial en destinos personales; cliente conserva su barra original. |
| feature/business/presentation/StoreOperationsViewModel.kt | Limita lectura/operaciones al storeId de SavedStateHandle cuando procede, además de StoreAuthorization existente. |
| feature/catalog/presentation/StoreProductConfigurationViewModel.kt | Limita selección y contenido al local recibido por ruta; sin nueva lógica de precios. |
| feature/catalog/presentation/StoreProductConfigurationScreen.kt | Reutiliza selector extraído, observación con ciclo de vida y Preview del contenido existente. |

No se añadieron ViewModels comerciales paralelos, repositorios, UseCases delegadores, módulos Hilt, dependencias o carpetas de producción adicionales. Se aprovecha BusinessViewModel. Cada nuevo colector usa WhileSubscribed(0); cancelar consumidores detiene observaciones. Cambiar selección o el reloj local de horario no inicia nuevas consultas Firestore. El reloj actualiza presentación cada 30 segundos mientras hay consumidor, sin polling remoto. Carga, vacío y error se distinguen. Las respuestas/clics conservan AccessProvenance; misma UID con otra generación no hereda datos/acciones. Revocar selección elimina el local viejo y solo selecciona entre ámbitos actuales.

### Preview y revisión visual
Todos sin Hilt, red, Firebase o NavController real, con muestras ficticias explícitas y ChaskiTheme:
- CommercialHomeScreen.kt: OwnerPreview, OwnerEmptyPreview, OwnerSuspendedPreview, ManagerPreview, ManagerMultiplePreview (inactivo, ancho 320 y fuente 1.3).
- AccountScreen.kt: AccountClientPreview y AccountCommercialAdminPreview (320, fuente 1.3).
- CommercialBottomNav.kt: CommercialNavigationPreview.
- AuthorizationPanel.kt: AuthorizationConflictPreview y AuthorizationLoadingPreview.
- StoreSelector.kt: StoreSelectorExpandedPreview.
- StoreProductConfigurationScreen.kt: StoreProductConfigurationPreview.
Total: 12 funciones Preview compiladas. No se afirma render de Android Studio Preview: no se abrió ese visor. Excepción razonada: MainScreen/ChaskiNavHost contienen navegación y ámbito Hilt; se previsualizan su contenido comercial y barra puros, no un NavController artificial. BusinessStatusScreen no se rediseñó.
La prueba de capturas usa muestras en el AVD y no servicios privados; las capturas de Content Compose no equivalen a render de Preview. Evidencia ignorada: .artifacts/hu34-3b2b/.

### Verificación ejecutada
- assembleDebug LOCAL y assembleDebugAndroidTest: BUILD SUCCESSFUL; 12 Preview compilados.
- testDebugUnitTest: 469 pruebas en 52 suites, sin errores/fallos. Incluye 10 pruebas nuevas de CommercialHomeViewModel: aprobado/sin locales, suspendido sin consultas de locales, filtrado ajeno, selección/revocación, inactivo, relogin mismo UID, error vs vacío, respuestas/error tardíos, liberación de colectores, reloj sin consultas extra y separación de horario. Pruebas de ambos editores y política verifican identificador exacto y rechazo de otros locales.
- Instrumentación final en HU34_Local_20261028: OK (64 tests), con Auth/Firestore/Functions demo-chaski-hu15 y host 127.0.0.1 mediante adb reverse; sin servicios Cloud. Desglose: 18 Compose comerciales, 14 navegación protegida, 3 navegación MAIN, 1 host real Hilt/repositorios, 2 contexto de acceso Firebase, 2 repositorios comerciales, 11 Compose Cart, 4 Compose Checkout, 9 coordinador Cart/Room. Registro completo final-instrumentation.txt.
- El host real comprueba nombre de negocio leído desde Firebase ficticio, ausencia de Buscar comercial, clic a Horarios de locales y editor productivo; suspensión y rechazo de compras/gestión; posterior cambio a cliente y conservación de instancias Cart/Address al visitar Checkout y direcciones.
- Compose comercial cubre aprobado, vacío, suspendido con/sin motivo, uno/varios locales, selector, sin operación, revocación, cambio de cuenta, error, claim admin con/sin sección, ocho restricciones, ancho pequeño y fuente 1.3/1.5 con scroll.
- Incidencias corregidas antes del cierre: import de aserción miembro inexistente y salto de línea faltante en nueva prueba; etiqueta antigua Reintentar actualizada a Volver a comprobar sin eliminar la prueba de recuperación. Primera invocación Gradle solo ejecutó 16 pruebas comerciales (comprobado XML); no se contabilizó como regresión completa. Se instalaron APK y se ejecutaron explícitamente clases con runner ADB, 64 casos. Sin debilitar aserciones funcionales.

### Riesgos y alcance posterior
Sin P0 detectado en la ejecución LOCAL final. No se promete revocación instantánea: se mantiene lease/contexto/refresco/servidor de 3B.2A. Permisos en memoria y visibilidad no sustituyen autorización backend. El horario visual puede tardar hasta 30 segundos en actualizarse; no afecta la validación autoritativa de compra. P1 heredado: timeout intermitente anterior de integración de contexto, aunque esta ejecución pasó; no se incrementaron límites para esconderlo. P2: revisión humana en Android Studio y dispositivo físico de tipografía/tamaños extremos y estados con textos reales largos.
3B.3 queda pendiente de autorización; historial personal HU17, bandeja de pedidos y apelación HU35 no se implementaron. Antes de Cloud siguen pendientes las condiciones de endpoints HU34, preparación/reconciliación, Rules y credenciales ya documentadas; este incremento no publica nada.
Sin cambios en foodApi, backend, Rules, Firebase Cloud, persistencia Cart/Address, lógica de precio/cotización, Pencil, dependencias o .gitignore. Sin push, despliegue, cambios de rama, merge, rebase o reset. Este documento permanece ignorado y no entra al commit.
Cierre de evidencia visual: se separaron las capturas en tres composiciones iniciales independientes y se aplicaron los mismos insets seguros del host. Las capturas iniciales entre cambios de estado no eran evidencia estable (Surface parcial) y se sustituyeron sin modificar producción. Se inspeccionaron owner.png, suspended.png y manager.png en .artifacts/hu34-3b2b/. Propietario aprobado y responsable tienen título, tarjeta, acciones y dos pestañas legibles. La captura final de suspendido siguió mostrando un Surface parcial pese a superar las aserciones Compose; no se considera evidencia visual estable de ese estado y requiere revisión en Android Studio/dispositivo. No se deduce un defecto productivo únicamente de esa captura. El estilo coincide con la jerarquía comercial aprobada; no constituye validación en dispositivo físico. La última suite comercial ejecutada aprobó 20/20 casos (incluye las tres capturas aisladas), registro visual-compose-final.txt. En conjunto se verificaron 66 casos instrumentados distintos de la versión final: 46 de regresión/integración y 20 comerciales. La ejecución anterior de 64 casos también fue satisfactoria; no se suman repeticiones como pruebas distintas.
Commit local de cierre: 79fb406e2f78c0d244bd4776342749149161c66b — feat(HU34 - presentation): implementar interfaces comerciales según permisos. Incluye 23 archivos (7 nuevos, 16 modificados), sin docs ni artefactos/configuración privada. Árbol Git limpio. La plantilla Debug y el secreto ficticio propios fueron retirados tras comparar su integridad; JSON personal de raíz intacto. AVD, reverses y Auth/Firestore/Functions Emulator propios detenidos; puertos comprobados sin escucha. Sin push. P2 visual explícito: captura automatizada de suspendido no estable; no se afirma render Preview ni validación visual completa en dispositivo físico.
## 22. HU34-3B.3 — Validación integral y cierre LOCAL (2026-10-29)

Entrada: ramaDante, HEAD 79fb406e2f78c0d244bd4776342749149161c66b, árbol limpio. Se leyeron AGENTS, decisiones/criterios HU34 y pruebas previas. Pencil se consultó mediante MCP, solo lectura, incluidos yF5v5, ULgsK, MRb4p, j5yaA, JlScn, MThyi, F2R22 y fcdZt. No se implementa lakue ni se rediseña cliente.

**Resultado: HU34 funcionalmente CERRADA EN LOCAL. HU34 PENDIENTE DE ACTIVACIÓN CLOUD.**

### Matriz final de criterios
| Criterio | Resultado LOCAL y evidencia |
| --- | --- |
| Cliente conserva Inicio/Buscar/Pedidos/Cuenta y compras autorizadas | Conforme. Código productivo sin cambios en este incremento; MainTabNavigationTest, AccessHostEmulatorTest, CartScreenTest, RealCheckoutScreenTest y FirebaseCheckoutRepositoryEmulatorTest ejecutados; sin permisos comerciales fabricados. Registro/reenvío HU05 comprobados en operaciones HTTP existentes. |
| Propietario aprobado | Conforme. CommercialJourneyEmulatorTest recorre Mi negocio → Locales (1) → Responsables (0), vuelve y guarda horario desde pantalla real; verifica Firestore SERVER. Catálogo → alta de categoría desde formulario y consulta posterior en servidor demo. Sin compras/bandeja recibida. |
| Propietario sin locales | Conforme. Datos remotos reales ficticios → Aún no tienes locales; sin horarios artificiales; catálogo real accesible y retorno conservado. Captura owner-empty.png. |
| Propietario suspendido | Conforme. Identidad conservada, motivo y ausencia de motivo comprobados con datos del Emulator, sin listado/gestión/compras; capturas estables desde host real suspended.png y suspended-no-reason.png. |
| Responsable uno/varios | Conforme. Dos locales asignados por coordinador real; selector → segundo local → horario/estado operativo y oferta. Se verifica que solo el segundo cambia. Tras revocación parcial se muestra solo el primero (Mi local); tras total desaparece la experiencia/responsabilidad anterior. No obtiene catálogo de propietario ni compras mientras responsable. |
| Administración Chaski | Conforme. Usuario con claim vigente mantiene experiencia cliente, accede desde Cuenta a los tres destinos reales. No obtiene propiedad. Retiro de claim → refresco con nueva revisión → sección desaparece y ruta deja de autorizarse. |
| Navegación y aislamiento | Conforme. 14 pruebas de guardas y host real: sesión A/B, logout/relogin mismo UID, back/restauración, scopes ajenos, eventos antiguos, ciclo de vida, MAIN y Cart/Address compartidos. Contexto/identidad/dominio JVM cubren correo/verificación, conflictos, reconciliación y errores cerrados. |
| Lease y recuperación | Conforme en alcance controlado. Prueba nueva demuestra lease predeterminada a 59 999/60 000 ms con reloj monotónico y una sola lectura. Retorno de segundo plano, reintento y reconexión con señales controladas existentes ejecutados. No se afirma corte/recuperación de radio físico ni revocación remota instantánea. |
| Servidor y privacidad | Conforme. 36 casos HTTP/Rules reales del Emulator: coordinator, exclusividad, manipulación directa, index privado, auditorías, revocación con token anterior y HU05–HU11. Prueba nueva verifica matriz de direcciones propias/ajenas para cliente, responsable, propietario suspendido y administrador; admin no accede a direcciones de otro UID. |
| Regresiones Cart/Address/Checkout | Conforme. 11 Compose Cart, 9 coordinador Room, 4 Compose Checkout, 33 repositorio Checkout con emuladores, rutas Address/MAIN reales y JVM de Address. No se modifican persistencia, tarifa, cotización, ni creación de pedidos. |
| Visual y accesibilidad | Conforme con observaciones P2. Suspensión con/sin motivo estable en host real; aprobado, vacío, responsable, selector, Cuenta y panel inspeccionados. Pruebas adicionales a 320 dp/130 %; scroll de Cuenta/controles y fuente 150 % existentes aprobados. No se acredita auditoría WCAG completa ni dispositivo físico. |
| Preview | Las 12 funciones existentes revisadas y compilación Android vigente correcta. No se renderizó Android Studio Preview: se verificaron contenidos equivalentes en AVD sin introducir herramientas/dependencias. |

### Cambios y responsabilidad
Solo pruebas, sin cambios productivos:
- app/src/androidTest/java/com/chaskifood/app/core/navigation/CommercialJourneyEmulatorTest.kt: cinco recorridos nuevos, MainActivity/Hilt/NavHost reales, Firebase Auth/Firestore/Functions demo, lectura SERVER posterior a escrituras UI, cambios de capacidades y evidencia visual de suspendido/vacío. Actor/semillas explícitos, solo loopback y demo; tokens solo en memoria, nunca logs/archivos. No añade servicios o capas a la app.
- app/src/androidTest/java/com/chaskifood/app/feature/business/presentation/CommercialHomeScreenTest.kt: tres pruebas visuales nuevas para selector, Cuenta administrativa y panel a 320 dp/130 %, reutilizando Content existente.
- app/src/test/java/com/chaskifood/app/core/access/data/SharedAccessContextTest.kt: prueba adicional de límite predeterminado de 60 segundos, sin polling ni extensión de caché.
- functions/test/commercialRules.emulator.ts: una prueba nueva de privacidad personal por UID, incluso suspendido y administrador; solo Firestore Emulator.

### Pruebas finales realmente ejecutadas
| Grupo | Casos distintos finales | Resultado |
| --- | ---: | --- |
| Android JVM, 52 suites | 470 | 0 fallos, errores u omisiones |
| Instrumentación AVD | 107 | OK (107 tests) |
| Functions unitarias | 42 | 42 aprobadas, 0 omitidas |
| HTTP comercial + Firestore Rules | 36 | 36 aprobadas, 0 omitidas |
| Total batería final | 655 | Sin fallos |

107 Android = 5 recorridos comerciales nuevos + 23 Compose comerciales (20 anteriores y 3 nuevas capturas) + 14 navegación protegida + 3 MAIN + 1 host real + 2 contexto Firebase + 2 repositorios comerciales + 11 Compose Cart + 4 Compose Checkout + 9 coordinador Room + 33 integración Checkout. De los 655 casos finales, 10 son nuevos en 3B.3 (8 instrumentados, 1 JVM, 1 Rules); 645 son regresiones existentes. No se suman como nuevos los reintentos ni ejecuciones parciales. assembleDebug LOCAL, assembleDebugAndroidTest y compilación TypeScript correctos. git diff --check correcto.

Entorno: HU34_Local_20261028 con GPU swiftshader, Firebase demo-chaski-hu15; solo Auth 9099, Firestore 8089 y Functions 5001, loopback mediante adb reverse. Node 22 portátil existente. CHASKI_ROUTE_FIXTURE=enabled con secreto ficticio; ninguna llamada auténtica a HeiGIT ni a Firebase Cloud. Argumentos del runner: hu34Host=127.0.0.1 y hu15Host=127.0.0.1. Código/política Rules se leyó desde la configuración LOCAL actual; no se modificaron Rules.

### Evidencia y limitaciones visuales
.artifacts/hu34-3b3/ contiene jvm-final.txt, XML JVM en jvm/, build-final.txt, instrumentation-final-correct-host.txt, functions-unit.txt y rules-http-final.txt. Todo ignorado.
Se inspeccionan capturas del AVD: suspended.png, suspended-no-reason.png, owner-empty.png, owner-sample.png, manager-sample.png, selector-open-320-130.png, admin-320-130.png y conflict-320-130.png. Capturas sample son Content con muestras; suspendido/vacío proceden del host real y datos del Emulator. No confundirlas con render de Android Studio Preview. Las capturas desde el host real del suspendido se ven completas y legibles; no se requirió modificar su Composable. Algunas capturas auxiliares de Content aislado aún resultan parciales y no se usan como evidencia visual concluyente. Se añadieron owner-real.png, manager-many-real.png y manager-one-real.png desde el NavHost real, que sustituyen a las muestras parciales para la inspección. La causa exacta del artefacto de captura aislada no se da por demostrada; no se afirma que SwiftShader lo resuelva en todos los harness.
Los controles se identifican por texto/iconos, selector solo contiene autorizados y los formularios conservan scroll. El menú emergente ocupa la ventana del AVD, mientras el contenido de la prueba se limita a 320 dp; no se afirma cambio físico de resolución del dispositivo.
P2: encabezados secundarios usan el color tenue existente #9E9E9E sobre #FBFBFB (contraste aproximado 2.6:1); revisar su contraste como mejora acotada futura. Las acciones esenciales conservan texto legible y el color no es la única señal. No se alteró el cliente para cambiar el estilo. Pendiente revisión física/tecnologías de asistencia; no se acredita conformidad WCAG completa.

### Incidencias y clasificación
- No se encontró P0/P1 productivo reproducible que requiriese corrección; no se rebajaron aserciones ni desactivaron validaciones.
- El primer fixture de horario tenía un día en lugar de los siete exigidos; el guardado se deshabilitó correctamente. Se corrigió el fixture y se comprobó persistencia real de siete días. No se cambió la regla de negocio.
- Se reforzaron esperas por UID y nueva revisión, evitando aceptar el estado Resolved anterior como finalización de refresh.
- Una ejecución integral fue cancelada antes de completarse para corregir el nombre del argumento existente hu15Host de Checkout. No se contabiliza como aprobada. La ejecución final correcta aprobó 107 casos; no se ampliaron timeouts.
- Riesgo histórico de timeout intermitente del laboratorio de contexto: no reproducido en batería final; no se afirma resuelta su causa. Mantener los diagnósticos existentes si reaparece.
- P2: mejoras de contraste indicadas y validación en dispositivo físico/Android Studio Preview no realizadas. No bloquean los permisos ni recorridos LOCAL comprobados.

### Cloud — continúa pendiente y no autorizado en este incremento
Reconciliación de identidad y credenciales anteriores; preparación del índice comercial; compatibilidad de datos existentes; publicación coordinada Functions/Rules/Android; validación Cloud de pruebas; recuperación, monitoreo y comprobaciones posteriores. La autorización cliente en memoria no sustituye al servidor, no garantiza revocación remota instantánea ni habilita Cloud por compilar. No se tocan checkoutApi ni otros servicios existentes.

Cliente preservado: no se modificó código productivo, ni Home, Buscar, Pedidos, Cuenta, Cart, Address o Checkout. Sin cambios de arquitectura, dependencias, tarifas, endpoints, Rules, .gitignore o Pencil. No se comienza HU16, HU17, HU19–HU21, HU28 ni HU35. Sin push, integración de ramas ni despliegues.
Refuerzo final: journeys-transition-final.txt aprobó los cinco recorridos con las nuevas aserciones de cliente → propietario y cliente → responsable manteniendo el mismo UID/sesión. Tras retirada parcial se conserva solo la asignación autorizada; retirada total devuelve experiencia CLIENT únicamente después de hechos autoritativos verificados, no por un error de red. Las capturas reales owner-real.png, manager-many-real.png y manager-one-real.png se inspeccionan junto a las de suspensión. La batería integral previa de 107 casos sigue válida para regresiones sin cambios; los cinco recorridos reforzados se repitieron focalizadamente y no se suman otra vez al total distinto de 655. No hubo cambios productivos después de esa batería.
Precisión de cierre visual: journeys-pixelcopy-final.txt aprobó 5/5 recorridos usando PixelCopy de Window (sin dependencia nueva) en lugar de UiAutomation para las capturas reales. Se observaron capturas completas del suspendido con/sin motivo en las primeras corridas y se verificaron sus textos, jerarquía y ausencia de operaciones. Algunas capturas repetidas todavía muestran regiones parciales, incluso en otros estados y con PixelCopy; no se considera demostrada la causa ni una corrección universal del renderizador. Es una limitación P2 de la evidencia automatizada que debe contrastarse en Android Studio/dispositivo físico; no se atribuye por suposición a la pantalla. Las aserciones de UI, operaciones y permisos pasan. El cierre es funcional LOCAL, con esta observación visual explícita; no se acredita render Preview ni validación visual exhaustiva de cada frame. Las imágenes parciales quedan como diagnóstico, no como prueba de conformidad visual completa.
Commit local HU34-3B.3: bc71bcc638cb2859f8047b6b943b2026254bd044 — test(HU34 - access): validar recorridos comerciales y aislamiento local. Cuatro archivos de pruebas, sin código productivo ni docs en staging. Árbol Git limpio; rama ramaDante. Se retiraron plantilla Debug y secreto ficticio propios tras verificar integridad, sin sobrescribir JSON personal. AVD, reverses y emuladores propios detenidos; puertos sin escucha. Sin push ni cambios remotos. HU34 cerrada funcionalmente en LOCAL con las observaciones P2 indicadas; Cloud sigue pendiente. No iniciar HU16 sin autorización.
