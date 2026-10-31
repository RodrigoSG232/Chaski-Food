# HU16 — Confirmar pedido: decisiones y requisitos

> Estado de implementación y evidencia vigente: sección 17 (HU16-8). Las secciones 1–16 conservan el antecedente de decisiones/diseño.

Actualización: 2026-10-29 (America/Lima). Base verificada: ramaDante, `bc71bcc638cb2859f8047b6b943b2026254bd044`. Antecedente documental: 1e9d45b9960b71f9a86510c962949d72a1ebd416.
Preparación técnica definitiva, sin implementación. Las decisiones DEC01–23 están aprobadas; el diseño técnico de la sección 16 sigue PROPUESTO. No acredita creación de pedidos ni autoriza implementación/despliegue.

Vigencia: la sección 16 resuelve las alternativas históricas de las secciones 5–15: Room v3, colecciones DEC16, bandeja HTTP DEC19 y precios aumentados/disminuidos DEC10. Las anotaciones antiguas «pendiente», «no aprobado» o las rutas alternativas no reabren esos acuerdos. DEC01–06 se conservan íntegramente.
Estados: **APROBADO** = acuerdo explícito de Etapa 2; **EXISTENTE** = comprobado en código; **PROPUESTO** = diseño sin aprobar; **PENDIENTE** = detalle/verificación por resolver; **FUERA DE ALCANCE** = otra etapa/historia. Las recomendaciones previas no son automáticamente acuerdos. No se utiliza el mensaje retirado por el usuario.

## 1. Objetivo y alcance

**APROBADO.** Como cliente, quiero confirmar una compra validada para generar formalmente mi pedido y enviarlo al establecimiento correspondiente.
Backend principal foodApi. Confirmación autoritativa, recuperación, snapshots, carrito protegido, bandeja persistente, aviso interno y FCM de nuevo pedido. CASH_ON_DELIVERY y PEN; disponibilidad AVAILABLE/OUT_OF_STOCK. Sin inventario cuantitativo, reservas ni límites comerciales arbitrarios. Controles técnicos de tipos, tamaños razonables y desbordamiento por concretar.
Fuentes: [CSV](../contexto/Copia%20de%20Planificacion_Chaski_Food_CE.xlsm%20-%20PLANIFICACION%20%281%29.csv), HU16 registros lógicos 87–92; [caso original](../contexto/Copia%20de%20Caso_Chaski_Food.pdf), páginas 2–4; [contexto HU15](HU15_CONTEXTO_TECNICO_IA.md), [guía HU15](HU15_GUIA_RAPIDA_EQUIPO.md), [README](../README.md), [arquitectura](ARCHITECTURE.md), [AGENTS](../AGENTS.md), [HU34](HU34_DECISIONES_Y_REQUISITOS.md). Informe de Etapa 1 en la conversación, sin ruta de archivo inventada. ARCHITECTURE/ESTADO conservan base anterior a HU15: no son evidencia vigente de ausencia de Checkout ni se actualizan aquí.

## 2. CA01–CA06 originales

Se conserva el texto original del CSV:

| ID | Criterio original APROBADO |
|---|---|
| CA01 | El backend realizará una última revalidación antes de crear el pedido. |
| CA02 | El backend recalculará los importes y no confiará en el total enviado por Android. |
| CA03 | El pedido creado quedará PENDING_CONFIRMATION. |
| CA04 | Los OrderItem conservarán nombre, cantidad y precio históricos. |
| CA05 | La dirección se guardará como snapshot independiente de la dirección editable del cliente. |
| CA06 | El local será notificado del nuevo pedido. |

## 3. RA01–RA09 complementarios

| ID | Requisito APROBADO y consecuencia |
|---|---|
| RA01 | Misma intención/reintentos no duplican pedidos; no basta deshabilitar botón |
| RA02 | Recuperar resultado perdido con identificador original |
| RA03 | Cambios relevantes de compra requieren aceptación explícita renovada; aceptación anterior no acepta cualquier contenido posterior |
| RA04 | UID, local, productos/cantidades, dirección, importes y pedido corresponden a intención coherente |
| RA05 | Rechazar condiciones comerciales inválidas: negocio no aprobado/suspendido, local no habilitado/cerrado/pausado/fuera de horario, productos/categorías/configuraciones inválidos, agotados, dirección ajena/inválida/fuera de cobertura y cotización inválida |
| RA06 | Controlar solicitudes simultáneas y cambios concurrentes |
| RA07 | Conservar carrito ante error/incertidumbre; respuesta antigua no borra ediciones/reconstrucciones posteriores |
| RA08 | Pedido creado se conserva aunque falle aviso; recuperar envío sin duplicar compra |
| RA09 | Reinicio/logout/respuestas tardías se reconcilian por intención y UID, sin mostrar ni limpiar datos de otra cuenta |

**PENDIENTE.** Enumerar exactamente los cambios relevantes y su protocolo de aceptación, incluyendo precio aumentado/disminuido y cambios de dirección con iguales coordenadas.

## 4. Decisiones aprobadas

### DEC-HU16-01 — Cotización temporal persistida

**APROBADO.** foodApi calcula tarifa HeiGIT, persiste cotización temporal Firestore y entrega quoteId. Cinco minutos iniciales. Validar UID, local, origen, destino, tarifa, versión y vencimiento. Android no modifica importe autoritativo. Si sigue válida y no cambió contexto logístico, no repetir HeiGIT. Limpieza/TTL no reemplaza comprobación de expiresAt.
**PENDIENTE.** Colección, esquema, índices, huella logística, política de versión tarifaria y limpieza. Comprobante firmado fue alternativa no seleccionada; no reabrir la elección.

### DEC-HU16-02 — Intentos recuperables en Room

**APROBADO.** Persistir intención con ID estable antes de red. Recuperar tras reinicio. Carrito con revisión persistente; resultado antiguo no borra carrito modificado o reconstruido. Estudiar migración segura sin pérdida de datos ni fallback destructivo.
**PENDIENTE.** Entidades/campos/versión Room, consumo único y limpieza atómica, retención local. Igualdad de contenido sola no distingue reconstrucción idéntica posterior.

### DEC-HU16-03 — Idempotencia duradera

**APROBADO.** ID único por intención asociado al UID autenticado. Intentos exitosos conservan asociación mínima al pedido durante su vida. Repetición devuelve resultado original; ID igual con contenido incompatible se rechaza. Nueva compra legítima usa otro ID. Incertidumbre se recupera con ID original. Política de limpieza de fallidos definitivos se diseña separadamente.
Recuperar éxito previo no exige que su cotización original siga vigente ni que el negocio vuelva a habilitarse. Limpiar cotización no elimina asociación exitosa. No se aprueba deduplicación arbitraria entre intenciones diferentes.

### DEC-HU16-04 — Notificaciones persistentes con reintentos

**APROBADO.** Crear pedido y evento pendiente atómicamente. Procesador independiente envía FCM; fallos recuperables permiten reintentar. Bandeja recuperable sin push. FCM no es fuente de verdad ni garantiza una sola entrega. Solo responsables asignados autorizados; propietario excluido. Payload mínimo privado.
**PENDIENTE.** Trigger/procesador concreto, límites técnicos/backoff, registros de dispositivos y deduplicación. Documentarlo no autoriza recursos Cloud nuevos.

### DEC-HU16-05 — Condiciones para recibir pedidos

**APROBADO.** Al menos un responsable asignado, sin exigir conexión online ni activación previa. Conservar asignación por correo y condiciones comerciales/operativas. Sin responsables, bloquear nuevas confirmaciones. Responsables autorizados conservan lectura de pedidos existentes durante pausas/suspensiones. Reglas de lectura por implementar; acciones durante suspensión se precisarán en HU19.
Asignación puede existir antes de activación de cuenta; no añadir esa activación como condición para recibir. Quien consulte debe autenticarse/verificar correo según HU34. **PENDIENTE.** Predicado técnico de asignación válida/exclusiva y retirada concurrente con confirmación.

### DEC-HU16-06 — Cotización vinculada al UID

**APROBADO.** Pertenece al UID solicitante, no a sesión Firebase específica. Relogin del mismo UID no invalida por sí solo cotización vigente. Otro UID no puede usarla. Siguen plazo y comprobaciones de contexto.
No conserva automáticamente Ready ni aceptación anterior. **EXISTENTE.** HU15 invalida revisión por generación de sesión. **PENDIENTE.** Recuperar referencia persistida y revalidar para mismo UID sin publicar respuesta antigua ni repetir HeiGIT solo por relogin. No exigir auth_time o generación local como vínculo obligatorio de quoteId.

### DEC-HU16-07 — Arquitectura

**APROBADO.** Reutilizar foodApi para confirmar y recuperar pedidos; no crear otro backend.

### DEC-HU16-08 — Persistencia propia

**APROBADO.** food_orders, order_attempts, delivery_quotes y order_events; snapshots y esquema propio versionado, sin heredar automáticamente checkoutApi.

### DEC-HU16-09 — Idempotencia

**APROBADO.** intentId durable por UID, contenido inmutable, un pedido por intención y recuperación/concurrencia seguras.

### DEC-HU16-10 — Consentimiento

**APROBADO.** Cambios relevantes, incluidos aumentos y disminuciones de precio, exigen nueva revisión. Separar consentimiento comercial y validez logística.

### DEC-HU16-11 — Room

**APROBADO.** Migración no destructiva v2→v3, intención antes de red, revisión persistente por UID y protección frente a ABA.

### DEC-HU16-12 — Responsables

**APROBADO.** Al menos una asignación válida para recibir; reutilizar coordinación HU34 ante revocaciones, sin restricciones horarias nuevas ni activación/online obligatorios.

### DEC-HU16-13 — Bandeja

**APROBADO.** Pedido persistente consultable por responsable. FCM es aviso, no fuente de verdad. No implementar acciones HU19.

### DEC-HU16-14 — REST

**APROBADO.** POST /api/v1/orders y GET /api/v1/order-attempts/{intentId}, autenticados y recuperables.

### DEC-HU16-15 — Pedido histórico

**APROBADO.** Guardar identidad, comprador, negocio/local, productos, importes, pago, snapshots de recojo/entrega, instrucciones, contacto telefónico, cotización, estado y fechas. Verificar fuente real del celular sin inventar teléfono verificado; evidencia y compatibilidad en sección 16.

### DEC-HU16-16 — Estructura Firestore

**APROBADO.** food_orders/{orderId} con subcolección status_history; order_attempts/{uid}/intents/{intentId}; delivery_quotes/{quoteId}; order_events/{eventId}. Pedido, intento exitoso, historial inicial y evento atómicos.

### DEC-HU16-17 — Confirmación autoritativa

**APROBADO.** Transacción final revalida precios efectivos del local, permisos, responsables, dirección, cotización y horarios. Ninguna llamada HeiGIT/FCM dentro del callback reintentable.

### DEC-HU16-18 — Recuperación

**APROBADO.** Distinguir SUCCEEDED, REJECTED_FINAL e incertidumbre. Timeout/intento ausente no significan fracaso; resultados terminales durables y coherentes. Nueva revisión implica nueva intención, no un reenvío automático como nueva compra.

### DEC-HU16-19 — Lecturas HTTP

**APROBADO.** Autorización por UID o asignación vigente mediante foodApi; bandeja mínima GET /api/v1/stores/{storeId}/orders. Sin abrir documentos privados al cliente ni sincronización compleja.

### DEC-HU16-20 — Cotizaciones

**APROBADO.** Cinco minutos iniciales; UID, contexto logístico, tarifa y versión. Reutilizar si válida sin repetir HeiGIT; quoteId no es de un solo uso. Reloj servidor.

### DEC-HU16-21 — Avisos

**APROBADO.** NEW_ORDER persistente, procesador Functions independiente, reintentos acotados, dispositivos por UID, destinatarios reautorizados y payload mínimo. FCM simulado en LOCAL.

### DEC-HU16-22 — Android

**APROBADO.** Reutilizar Checkout/Retrofit/Hilt/Room; intención durable, recibo único, consumo transaccional condicionado a revisión del carrito, aislamiento de sesión y recuperación sin compras nuevas automáticas.

### DEC-HU16-23 — Calidad

**APROBADO.** Matriz CA01–06/RA01–09, seguridad, concurrencia, migraciones 1→2→3 y 2→3, snapshots, HU34, Emulator Suite y recorrido Android. Cierre LOCAL por evidencias, no por cantidad arbitraria de pruebas.

## 5. Modelo y ciclo de vida del pedido

**APROBADO a nivel de contrato; nombres/esquema físico PENDIENTES.**

| Información | Garantía |
|---|---|
| Pedido e intención | Asociación duradera por UID; colección aún no elegida |
| Comprador, negocio, storeId | Identidad del token y relaciones verificadas |
| Líneas históricas | ID producto, nombre, cantidad, precio unitario e importe de línea por valor |
| Dirección independiente | Texto, coordenadas, referencia e instrucciones; ID original solo trazabilidad |
| Importes | Subtotal, reparto y total exactos en céntimos; PEN |
| Pago | CASH_ON_DELIVERY, no implica cobro realizado |
| Cotización | quoteId y evidencia suficiente de distancia/tarifa/versión utilizada |
| Estado | PENDING_CONFIRMATION inicial |
| Fechas | Servidor y semántica temporal definida |
| Avisos | Evento asociado atómicamente, separado del éxito de compra |

Nuevo modelo de foodApi según requisitos propios; no copiar automáticamente checkoutApi. Verificar interferencias de documentos antiguos antes de usar colecciones reales no obliga a adoptar sus atributos ni autoriza borrar registros.
**APROBADO.** HU19 acepta directamente PENDING_CONFIRMATION → PREPARING. PDF página 2 menciona Confirmado intermedio; CSV HU19 y acuerdo actual establecen transición directa. No introducir CONFIRMED. Aceptación, rechazo, cancelación, preparación, recojo y Rider fuera de HU16.

## 6. Arquitectura prevista y evidencia actual

Rutas relativas verificadas desde raíz; líneas referidas al HEAD indicado:

| Estado | Evidencia / responsabilidad |
|---|---|
| EXISTENTE | functions/src/index.ts:14–57: foodApi elegibilidad/delivery-quotes; no creación/recuperación de pedidos |
| EXISTENTE | functions/src/auth/verifyBuyerToken.ts:4–14: token/revocación, devuelve UID |
| EXISTENTE | functions/src/eligibility/storeEligibilityService.ts:5–18: lectura consistente negocio/local; política administrativa, no horarios/productos |
| EXISTENTE | functions/src/delivery/deliveryQuoteService.ts:16–77: dirección propia, cobertura, rutas, tarifa/fechas/coordenadas; sin persistencia ni quoteId |
| EXISTENTE | functions/src/delivery/deliveryQuotePolicy.ts:1–38: fórmula y versión vigentes |
| EXISTENTE | app/src/main/java/com/chaskifood/app/feature/checkout/data/FirebaseCheckoutRepository.kt:79–209: revisión Android, sin crear pedido |
| EXISTENTE | app/src/main/java/com/chaskifood/app/feature/checkout/presentation/CheckoutViewModel.kt:149–201: contexto y expiración; sin comando durable |
| EXISTENTE | app/src/main/java/com/chaskifood/app/feature/checkout/data/remote/DeliveryQuoteApi.kt:13–33: DTO sin quoteId |
| EXISTENTE | app/src/main/java/com/chaskifood/app/feature/cart/domain/CartRepository.kt:41–50: sin consumo condicionado por pedido |
| EXISTENTE | app/src/main/java/com/chaskifood/app/feature/address/data/remote/AddressDocumentMapper.kt:16–49: datos completos de dirección; cotización actual solo lee ubicación |
| EXISTENTE | firestore.rules:724–725: deniega rutas no habilitadas; sin permisos productivos de pedidos en archivo local |
| APROBADO | Backend revalida oferta, estados/horarios/cobertura, dirección propia, responsables y cotización; recalcula y registra atómicamente |
| PROPUESTO | Separar solicitud, política pura, acceso transaccional, transporte y envío; ampliar foodApi e infraestructura Hilt/Retrofit existentes |

Precio de configuración local, no catálogo base: app/src/main/java/com/chaskifood/app/feature/cart/domain/ValidateProductForCart.kt:25–63. Reusar reglas conceptualmente con pruebas equivalentes Kotlin/TypeScript, no asumir ejecución de clases Android en Functions.
Lecturas antes de escrituras en transacción; revalidar en reintentos. No HeiGIT ni FCM dentro de callback repetible. Comparar compra revisada con datos actuales, recalcular subtotal/total usando reparto backend válido. Kotlin Long; TypeScript enteros seguros/arimética exacta, no aceptar pérdida silenciosa. Precisar validez temporal en borde de vencimiento.

## 7. Contratos de comunicación por definir

**PROPUESTO, no REST aprobado:** POST /api/v1/orders y GET /api/v1/order-attempts/{attemptId}, con Firebase ID Token. Clave estable en cabecera o cuerpo pendiente. Solicitud conceptual: local, dirección, productos/cantidades, quoteId, pago y contenido/versiones revisados. UID del token. Precios esperados sirven para comparar consentimiento, no son autoridad monetaria.
Éxito identifica intento/pedido, estado, importes y fechas. Definir creación frente a repetición, conflicto de contenido y revisión requerida. Diferenciar autenticación/permisos, bloqueo comercial, datos inválidos, caducidad, fallo recuperable y resultado incierto. Códigos HTTP, DTO, límites técnicos y errores pendientes.
Recuperación sin resultado todavía no prueba que la solicitud original no vaya a confirmar; conservar ID. No generar otro por timeout. Cambios relevantes requieren nueva aceptación y protocolo de intención por definir, sin reutilizar ID con payload incompatible.

## 8. Autorizaciones y privacidad

**APROBADO.** Comprador solo accede a sus pedidos. Responsable solo a locales asignados. Propietario no hereda bandeja por propiedad ni admin por claim; compras anteriores del propietario son acceso personal. Ver [HU34](HU34_DECISIONES_Y_REQUISITOS.md).
Conservar requireStoreOperator para HU09/HU11; controles específicos de pedidos. Admin SDK no está protegido por reglas cliente: endpoint/procesador deben autorizar. Reglas de lectura no deben bloquear pedidos anteriores al responsable vigente solo por suspensión. Correo verificado obligatorio para acceso; asignación puede preceder activación.
**APROBADO por DEC-HU34-04–07.** Antes de crear una compra nueva, foodApi verificará que el UID autenticado esté autorizado a actuar como cliente. Propietario (también suspendido) y responsable no pueden confirmar nuevas compras, incluso invocando directamente el endpoint. Ambos conservan consulta/seguimiento de compras personales anteriores. Exresponsable sin asignaciones ni otra autorización comercial retorna a cliente. La recuperación de una intención ya exitosa conserva DEC-HU16-03 y no se trata como nueva compra.
Permisos/asignaciones vigentes y exclusividad se verifican autoritativamente; prevenir colisiones y reautorizar ante revocación, sin retirar automáticamente autorizaciones preexistentes. El contexto Android reutilizable de DEC-HU34-06 no sustituye seguridad servidor.
**APROBADO.** Suspensión bloquea nuevas confirmaciones del negocio afectado; no elimina ni altera automáticamente pedidos creados. Responsable con asignación vigente conserva lectura de existentes conforme a DEC-HU16-05; propietario no obtiene bandeja. Aceptar/rechazar durante suspensión corresponde a HU19. Reactivar el negocio no activa locales: comprobar nuevamente status del negocio/local, operationalStatus, horarios y demás condiciones; APPROVED no implica ACTIVE ni OPEN.
Minimizar datos/logs y no exponer documentos administrativos al comprador. **PENDIENTE.** Firestore reactivo o HTTP para bandeja, índices, reglas, registro de dispositivos y cachés.

## 9. Persistencia y recuperación

**APROBADO.** Room guarda intención antes de red por UID, contexto y revisión del carrito. Timeout es incertidumbre: conservar carrito y recuperar mismo intento. Reinicio/relogin mismo UID permite reconciliar; otra cuenta no ve ni limpia. Cancelación de corrutina/logout no prueba rollback backend.
Buscar éxito previo y comprobar identidad/contenido antes de exigir otra validación de precio/caducidad. Pedido, asociación de intento y evento juntos. **PROPUESTO.** Limpieza Room transaccional condicionada a revisión y aplicación única por recibo; si cambió carrito, conservarlo. **PENDIENTE.** Entidades/migración y conducta ante edición con envío pendiente. No aprobar colas que creen compras nuevas sin aceptación vigente.

## 10. Notificaciones

**APROBADO.** Bandeja persistente, aviso foreground y push de nuevo pedido. Pedido por storeId, no persona responsable. Evento recuperable y procesador independiente; error de aviso no vuelve fallida una compra creada. Destinatarios reautorizados, sin propietario. Duplicados posibles; deduplicar presentación por evento/pedido.
**PENDIENTE.** Varios dispositivos, renovación/retirada de registros, logout offline, límites/backoff, canal y permiso Android 13+, fallos definitivos. Minimizar payload y reautorizar al abrir. Push previo puede llegar después de revocación; no garantizar retirada de información ya vista.
**PRUEBAS FUTURAS.** Laboratorio Auth/Firestore/Functions puede simular adaptador FCM, errores y reintentos. No hay FCM Emulator en el laboratorio: no acredita entrega real. Validación push requiere prueba posterior autorizada en dispositivo/entorno acordado. No dirigir pruebas demo a servicios reales para completar suite.

## 11. Dependencias y exclusiones

HU34 permisos/experiencias; HU04 dirección/snapshot; HU05–HU11 habilitación/asignaciones/oferta; HU14 carrito; HU15 revisión. Preservar modos LOCAL/CONECTADO, Release cerrado y secretos servidor; backend Node 22/pnpm 11.25.0, codebase food-local.
HU17 historial completo; HU19 aceptar/rechazar y marcar agotados; HU20–HU21 preparar/recoger; HU24 demás avisos; HU28 agregados: fuera de alcance salvo mínimos adelantados expresamente (bandeja/aviso nuevo pedido y preservación de compras anteriores). No decidir Rider, pagos digitales, inventario ni dashboard.
checkoutApi intacta, sin modificar/retirar ni consultar datos reales. No es backend elegido ni esquema definitorio para HU16. Comprobar interferencias históricas antes de usar colecciones reales no condiciona atributos nuevos de foodApi.

## 12. Matriz requisito → prueba esperada

Todo lo siguiente es **PROPUESTO / NO EJECUTADO en Etapa 2**.

| Requisito | Prueba esperada |
|---|---|
| CA01, RA05 | Cambiar negocio/local/horario/producto/categoría/configuración/dirección antes del commit: rechazo sin pedido |
| CA02, RA04 | Manipular total/precios, cantidades inválidas/overflow: cálculo backend exacto |
| CA03 | PENDING_CONFIRMATION sin CONFIRMED ni acciones HU19 |
| CA04 | Editar/borrar catálogo conserva histórico de líneas |
| CA05 | Editar/borrar dirección conserva snapshot; dirección ajena rechazada |
| CA06, RA08 | Fallo FCM conserva pedido/evento y bandeja; reintento no duplica compra |
| RA01, RA06 | Mismo UID/ID/contenido en paralelo crea uno; contenido incompatible falla |
| RA02, RA09 | Respuesta perdida/reinicio recupera mismo pedido incluso vencida cotización original |
| RA03 | Cambios relevantes invalidan aceptación; probar aumento/disminución según protocolo por cerrar |
| RA07 | Respuesta tardía no borra carrito editado o reconstruido idéntico; limpieza única tras reinicio |
| RA09 | Logout/login A o A→B no filtra ni declara rollback inexistente |
| DEC-HU16-01 | quoteId ajeno/alterado/vencido o logística distinta rechazado; vigente no repite HeiGIT; TTL no es autoridad |
| DEC-HU16-02 | Migración conserva app_config/carritos; intención existe antes de red y se recupera |
| DEC-HU16-03 | Asociación exitosa dura con pedido; limpiar cotización no permite duplicar |
| DEC-HU16-04 | Duplicados, reintentos, dispositivos, permiso denegado y revocación; ningún aviso al propietario |
| DEC-HU16-05 | Sin asignación bloquea; offline/no activado no bloquea por sí solo; lectura de anteriores durante suspensión |
| DEC-HU16-06 | Mismo UID reutiliza quoteId vigente tras relogin/contexto válido; otro UID no; Ready anterior no se conserva automáticamente |
| HU34 | Owner/admin sin privilegio de bandeja; correo no verificado rechazado; nuevo responsable consulta anteriores |
| DEC-HU34-04/06 | Asignación/aprobación concurrente, exclusividad y revocación; permisos vigentes y sin pérdida de ámbitos por actualización |
| DEC-HU34-05/07 | Propietario/responsable rechazados al crear por acceso directo; consultas personales anteriores permitidas; exresponsable autorizado como cliente |
| RA05, DEC-HU16-05 | Suspensión antes de confirmar impide nueva compra; pedidos existentes intactos y legibles por responsables vigentes, sin acceso propietario |
| RA05, reactivación | APPROVED con local INACTIVE/SUSPENDED/CLOSED/PAUSED sigue bloqueado; reactivación no activa/abre automáticamente |

Evidencia histórica HU15 en guías: 336 JVM, 31 unitarias Functions, 23 HTTP en integración aislada y validaciones anteriores en dispositivo. No ejecutadas aquí; no acreditan HU16 ni entrega FCM.

## 13. Riesgos

| Riesgo | Severidad | Tratamiento |
|---|---|---|
| Heredar propietario/correo no verificado | Bloqueante | Política específica y pruebas Android/reglas/backend |
| Cotización cliente manipulable/confirmación no transaccional | Bloqueante para HU16 | quoteId y revalidación aprobados por implementar |
| Duplicados por timeout/limpieza temprana | Bloqueante | Asociación duradera y recuperación |
| Compra directa por propietario/responsable | Bloqueante para HU16 | Aplicar DEC-HU34-07 en foodApi; historial propio conserva acceso |
| Confundir suspensión con pérdida de propiedad o bloqueo del histórico | Importante | Experiencia restringida sin listado/gestión; lectura específica para responsables vigentes |
| Revocación concurrente/offline | Importante | Reautorizar, retirar UI y cerrar política caché |
| Borrar carrito reconstruido | Importante | Revisión persistente y consumo único |
| Aviso no recuperable tras compra | Importante | Evento atómico/procesador |
| Interferencias de documentos/clientes antiguos | Importante antes de entorno real | Verificar colisiones sin copiar esquema ni eliminar |
| Romper MAIN/contexto HU15 | Importante | Regresiones navegación/sesión/expiración |
| Confundir Emulator con FCM real | Importante | Simulación y aceptación real posterior |

## 14. Decisiones y detalles técnicos pendientes

No reabrir DEC-HU16-01–06. Cerrar: rutas/colecciones/esquema versionado; REST/errores; huellas logística/contenido y aceptación; entidades/versión/migración Room; lectura/suscripción, reglas/índices; retención de fallidos; procesador/reintentos; dispositivos/caché offline; transición tarifaria y expiración al confirmar; exclusividad/asignación concurrente HU34. Lectura de existentes durante suspensión ya aprobada; acciones durante suspensión HU19.
Antes de HU34-3A especificar protocolo técnico de exclusividad/colisiones y contrato de observación/autorización. Experiencia del propietario suspendido y compras exclusivas de cliente ya aprobadas en DEC-HU34-05/07. HU35 es propuesta provisional futura; no diseñar contratos ni implementar apelaciones en HU34/HU16. Diseño visual posterior en Pencil. No iniciar implementación por este documento.
Ambos documentos están ignorados por /docs/: son locales y no viajan con push/pull. Compartir copias por canal acordado sin secretos; versionado futuro requiere autorización. No cambiar .gitignore ni forzar staging.

## 15. Antecedente: auditoría focalizada posterior a HU34 (2026-10-29)

Registro histórico: las alternativas que aquí figuran por aprobar quedaron resueltas por DEC07–23. Para implementar, seguir exclusivamente el diseño vigente de sección 16; no utilizar users/{uid}/order_attempts ni lectura directa Firestore de bandeja.

Referencia vigente: ramaDante, bc71bcc638cb2859f8047b6b943b2026254bd044, árbol limpio al iniciar. Esta sección actualiza el diagnóstico, no sustituye ni reabre CA, RA o DEC aprobados. Todo contrato físico/protocolo detallado aquí es PROPUESTO salvo indicación expresa. Sin implementación, migraciones, pruebas nuevas, ejecución de suites, consultas Cloud ni commit en esta auditoría.

### Fuentes contrastadas
CSV original: registros CSV lógicos 87–92, coinciden literalmente con CA01–CA06 de sección 2 (las líneas físicas difieren por celdas multilínea). PDF original: 4 páginas, leído completo; pág. 2 exige revalidación, snapshots, PENDING_CONFIRMATION y aviso; págs. 2–4 distinguen pedido y entrega Rider. El estado CONFIRMED del PDF no se incorpora: permanece aprobado el acuerdo HU19/CSV PENDING_CONFIRMATION → PREPARING. AGENTS, HU15_CONTEXTO_TECNICO_IA y decisiones HU34 contrastados con código; referencias antiguas de secciones 6/14 no son el HEAD vigente. HU34 se encuentra implementada y cerrada funcionalmente LOCAL, con activación Cloud pendiente.

### Cobertura actual
- CA01/02: PARCIAL como infraestructura, NO IMPLEMENTADOS para confirmación. HU15 Android revisa ofertas, horarios, cobertura y subtotal; foodApi solo elegibilidad administrativa y tarifa vial. No hay transacción de compra autoritativa.
- CA03/04/05/06: NO IMPLEMENTADOS. No existen creación de pedido PENDING_CONFIRMATION, OrderItem/snapshot histórico persistido, snapshot de dirección en pedido ni bandeja/FCM productivos de nuevo pedido. OrdersScreen vacío y otras pantallas demostrativas no acreditan estos criterios.
- RA01/02/06/07/08/09: APROBADOS, pendientes de intención durable, asociación intento/pedido/evento y recuperación. Patrón de recibo idempotente comercial existe, pero no es idempotencia de compra.
- RA03/04/05: PARCIALMENTE apoyados por HU14/HU15/HU34, pendientes de aceptación inmutable y validación final en servidor. El contador attempt de CheckoutViewModel es temporal, no UUID persistido de intención.
- DEC01: cotización temporal existe en respuesta HTTP, pero NO persistencia/quoteId. DEC02: Room v2 existe sin revisión/intentos. DEC03: patrón transaccional comercial existe, no ledger de pedidos. DEC04: NO outbox/procesador/FCM. DEC05: asignaciones PENDING/VERIFIED existen; NO requisito de al menos un responsable al confirmar. DEC06: UID/relación están verificados al cotizar, pero NO referencia durable reutilizable tras relogin. Las seis decisiones permanecen APROBADAS.

### Inventario vigente y recorrido
- RealCheckoutScreen.kt: estado Ready y texto de confirmación futura; no envía pedidos. CheckoutViewModel conserva pago CASH_ON_DELIVERY y booleano de aceptación contextual, contador de revisión, invalidación y caducidad monotónica.
- CheckoutModels.kt: CheckoutContext con UID/generación/cart/dirección; CheckoutQuote y cálculo exacto Long; CheckoutRepository solo observeContext/isContextCurrent/review. CheckoutContextSource compara fuentes locales y contexto observado, sin revisión persistente.
- FirebaseCheckoutRepository.kt y FirebaseProductOfferReader.kt: Source.SERVER, local inicial/final, producto/configuración por producto y categorías compartidas dentro de revisión. Precio desde store_product_configs, no precio base. Validaciones puras ValidateProductForCart, evaluateProductOffer, evaluateStoreEligibility y horarios nocturnos se reutilizan conceptualmente con pruebas equivalentes TypeScript; no ejecutar Kotlin Android en Functions.
- CartLocalCoordinator/CartDao: serialización Room de leer/aplicar/guardar; CartEntity solo ownerUid/businessId/storeId/currencyCode. Cart.equals compara valores, no historia. ChaskiDatabase v2 contiene app_config/carts/cart_items y DatabaseModule registra MIGRATION_1_2 para chaski_food.db. No intención ni consumo condicionado.
- AddressDocumentMapper: label/addressText/location.lat,lng/reference/instructions/createdAt/updatedAt; selección DataStore por UID y datos Firestore bajo users/{uid}/addresses/{id}. La dirección está disponible completa, pero deliveryQuoteService solo lee location para cotizar.
- CheckoutDataModule, DeliveryQuoteApi/HttpDeliveryQuoteReader, BuyerTokenSource y Retrofit existentes: reutilizar HTTP/Hilt/token y errores/cancelación. DeliveryQuote actual no tiene quoteId.
- SharedAccessIdentitySource/SharedAccessContext/CartSessionSource: procedencia y generación de sesión, no crear otro mecanismo global. ResolveAccess concede START_PURCHASE solo a CLIENT y READ_STORE_ORDERS a responsables por local incluso si no pueden operar. Propiedad pendiente/observada/rechazada no equivale a propietario comercial; admin sin otro rol conserva experiencia correspondiente.
- foodApi: functions/src/index.ts expone elegibilidad, cotización y access/* LOCAL. verifyBuyerToken valida token/revocación y devuelve UID; no decide rol comprador. verifyCommercialIdentity compara Auth actual/claims/correo y sirve como patrón de identidad vigente. ComercialAccessService coordina índices UID/correo, propiedad/asignaciones y recibos; sus consultas/transacciones deben componerse, no invocarse por HTTP dentro de otra transacción.
- deliveryQuoteService: 3 lecturas nominales (local/negocio/dirección), ruta fuera de transacción, respuesta con tarifa/coordenadas/fechas, sin escritura. Política vigente lima-driving-car-v1, 150 + 120/km, 5 minutos por defecto, bigint y entero seguro de salida. No cambiar fórmula.
- Firestore Rules local no define pedidos/cotizaciones/intentos/eventos; catch-all deniega. No hay FirebaseMessagingService, contrato de dispositivos u outbox productivos encontrados. Admin SDK disponible, pero el cliente Messaging y procesamiento requieren implementación.

### Hallazgos y escenarios priorizados
| Prioridad | Escenario / situación comprobada | Medida PROPUESTA y aprobación necesaria |
| --- | --- | --- |
| P0 | Producto/precio/local/dirección cambia tras Ready | Transacción final lee fuentes vigentes y compara consentimiento congelado; sin pedido si hay cambios relevantes. |
| P0 | Negocio no APPROVED, local INACTIVE/SUSPENDED/PAUSED/CLOSED o fuera de horario | Rechazo comercial; hora America/Lima y jornada nocturna equivalentes al dominio actual, reevaluadas en reintento transaccional. |
| P0 | Producto/categoría/configuración desaparecen, agotados o relaciones ajenas | Bloquear, sin recurrir a precio base; sin inventario/reservas cuantitativos. |
| P0 | Cantidad inválida, precio decimal incompatible o overflow | Tipos estrictos; conversión exacta decimal → céntimos; bigint para operar y rango de transporte/almacenamiento explícito, sin truncamiento. |
| P0 | Precio sube o baja, nombre visible o dirección/texto/instrucciones cambia aunque coordenadas no | Proponer revisión y aceptación nueva de cambios relevantes; no tratar booleano previo como autorización universal. Aprobar lista exacta. |
| P0 | Mismo ID en doble tap, solicitudes paralelas, timeout después de commit o respuesta perdida | Ledger durable por UID+intentId, payload canónico inmutable; mismo contenido recupera resultado, diferente rechaza. No generar nuevo ID por incertidumbre. |
| P0 | Reinicio, relogin mismo UID o cambio UID | Room persiste antes de HTTP. Recuperación autenticada por UID, sin vincular quoteId a generación local. La generación sigue protegiendo publicación UI. Otra cuenta no muestra ni limpia intentos anteriores. |
| P0 | Carrito cambia o se reconstruye idéntico tras envío | Revisión persistente y monotónica; aplicar recibo/consumo una vez en la misma transacción Room, conservando cabecera/revisión aun vacía. Igualdad o hash de contenido solo no bastan. |
| P0 | Propietario/responsable intenta POST directo; cuenta se vuelve comercial durante confirmación | Reutilizar hechos/índices HU34 dentro de transacción de creación. Sin fallback a CLIENT si error/conflicto. Prior success se recupera antes de exigir permiso de nueva compra. |
| P0 | Guardo recuperación con la misma política de Checkout | Incompatible: AccessNavigationPolicy.allows hoy exige Resolved; Checkout está protegido por START_PURCHASE. Proponer ruta/guarda mínima de recibo/recuperación personal por UID/READ_PERSONAL_PURCHASES, manteniendo compra fail-closed. No reescribir HU34 en esta etapa. |
| P0 | Se revoca último responsable durante confirmación | Leer asignaciones válidas en la misma transacción que pedido; revocaciones escriben esas filas e índices existentes. Probar serialización efectiva; no asumir que una comprobación previa fuera del commit basta. |
| P0 | Cotización ajena/alterada/vencida, coordenadas/local/dirección distintos | quoteId privado persistido y pertenencia/contenido/plazo en servidor. TTL no es validación. Éxito recuperado no exige quote original vigente. |
| P1 | Dirección borrada, ajena, inválida, radio reducido o coordenadas modificadas | Bloquear/revisar cobertura con fuentes actuales; texto cambiado puede requerir aceptación sin nueva ruta si huella logística idéntica. |
| P1 | Asignación pendiente, responsable offline o sin dispositivos | Incluir PENDING válido para recibir, no exigir activación/conectividad. VERIFIED con binding coherente para leer. LEGACY/reconciliación no se consideran permiso probado. Precisar predicado. |
| P1 | FCM falla o worker cae tras enviar | Pedido y evento permanecen; outbox con lease/reintentos y deduplicación visual por evento. Nunca declarar compra fallida ni volver a crearla. |
| P1 | Múltiples responsables/dispositivos, token rotado, logout offline o revocación | Resolver destinatarios actuales al enviar y reautorizar al abrir; payload mínimo sin dirección/productos. No garantizar retirada de aviso ya entregado. |
| P1 | Responsable consulta anteriores durante suspensión | Guarda de lectura diferente de requireStoreOperator: no exigir negocio APPROVED/local ACTIVE para histórico, sí asignación actual. Propietario/admin no heredan bandeja. |
| P1 | GET aún no encuentra intento, pero POST anterior sigue ejecutándose | Estado incierto/no encontrado no autoriza otra intención; conservar ID y consultar o reintentar el mismo payload. |
| P1 | Quote vence al borde del commit o cambia versión tarifaria | Definir instante de aceptación y política de versiones; no recalcular silenciosamente. Ahora no existe ese contrato. |
| P1 | Auth, Firestore, Room y FCM cambian concurrentemente | No existe transacción común entre sistemas: autenticar, serializar estado Firestore, reconciliar Room y notificar aparte. Logout/cancelación HTTP no prueban rollback. |
| P1 | Documentos de checkoutApi o reglas Cloud antiguas | No se consultó Cloud ni se verifica esquema real aquí. Aislar colecciones nuevas o discriminar versión/origen; no adoptar ni borrar legado automáticamente. |
| P1 | Cambios locales mientras envío está pendiente | Proponer una intención activa por carrito/contexto; permitir edición sin borrar ni reenviar otra compra silenciosamente. Resolver resultado original antes de nueva confirmación. Aprobar política exacta de UI/limpieza si cambia solo dirección. |
| P2 | Abuso de payload/cotización y consultas redundantes | Límites técnicos justificados, métricas y presupuesto; no inventar límite comercial de productos. Reutilizar quote vigente por huella logística y no recotizar solo por recomposición/relogin. |

Correo verificado: obligatorio para acceso operativo de responsables según HU34. El ResolveAccess actual no exige verificación a todo CLIENT para START_PURCHASE. No extender esa condición a compradores sin acuerdo explícito; verificar identidad vigente y aplicar política ya aprobada. Propiedad se decide por ownerUid, no correo de contacto. Un admin sin rol comercial no se bloquea de comprar solo por su claim.

### Arquitectura mínima y contratos propuestos (NO APROBADOS)
1. Ampliar foodApi; sin Express, otro servidor o base. Reutilizar auth, política de elegibilidad, geometría, moneda e índices comerciales. Evitar transacciones anidadas: helpers que reciban Transaction y lecturas/políticas puras. Ninguna llamada HeiGIT/FCM en callback repetible.
2. Cotización: persistir documento privado temporal con UID, local/negocio, dirección/huella logística, origen/destino, distancia, tarifa PEN, versión y issuedAt/expiresAt. Mantener 5 minutos iniciales. Proponer delivery_quotes; nombre pendiente. Revalidar antes de persistir si cambiaron coordenadas durante HeiGIT. quoteId vigente evita nueva ruta; no es autorización permanente de compra.
3. POST /api/v1/orders: Firebase token; intentId, storeId, addressId, quoteId, CASH_ON_DELIVERY, líneas productId/cantidad y datos esperados de consentimiento. UID y precios efectivos salen de servidor. Normalizar orden de líneas, rechazar duplicados, huella de payload estable; precios esperados sirven solo para detectar cambios.
4. GET /api/v1/order-attempts/{intentId}: dueño del token; devuelve estado/recibo original o resultado aún no observado. Proponer 201 creado, 200 repetición/recuperación, 409 intención incompatible/revisión/cotización/bloqueo comercial con códigos distintos, 400/422 datos, 401/403 identidad/permiso, 503 transitorio. Un timeout/503 no demuestra ausencia de pedido. 404 no prueba que el POST en vuelo no vaya a confirmar. No cambiar estas rutas como hecho implementado.
5. Guardar atómicamente pedido PENDING_CONFIRMATION, recibo duradero y evento NEW_ORDER. Proponer food_orders para aislar legado, users/{uid}/order_attempts para asociación y order_events para outbox; ninguna colección definitiva aprobada. Idempotencia puede usar ID de pedido determinista a partir de UID+intentId, reutilizando digest. No usar audit_logs como almacén de intención con dirección/items: admin puede leer esos logs.
6. Pedido versionado: customerUid/businessId/storeId, snapshots mínimos del local/líneas/dirección, subtotal/deliveryFee/totalMinor, PEN, CASH_ON_DELIVERY, quoteId/evidencia tarifaria, estado/fechas de servidor, intentId. Una definición Domain de pedido histórico, no convertir OrderSummary demostrativo en contrato productivo. Decidir array de items vs subcolección conforme a tamaño técnico/atomicidad, sin límites comerciales arbitrarios.
7. Ampliar CheckoutRepository con confirmación/recuperación y reutilizar CheckoutViewModel/HTTP/Hilt; no otro ViewModel global de sesión. Modelos de pedido reutilizables en feature/orders/domain cuando se implementen, porque bandeja y recibo los compartirán. Checkout/data/local se justifica para Entity/DAO de intentos; no crear mappers/UseCases que solo deleguen.
8. Room: migración manual propuesta 2→3, conservando app_config/carts/cart_items. Revisiones de carrito y tabla de intentos por UID con UUID estable, payload/hash congelados, revisión base, quoteId, estado de envío/resultado y aplicación de recibo. Confirmar previamente compatibilidad del esquema. No borrar cabecera/reiniciar contador al vaciar: impedir ABA. Registrar recibo y consumo condicionado atómicamente. Una respuesta antigua no borra ediciones posteriores.
9. Bandeja mínima de solo lectura por local autorizado; contrato Flow/Data detrás de repositorio del feature orders si se usa Firestore, sin filtros solo visuales. Reglas/índices nuevos; escrituras de pedido exclusivamente backend. Correo verificado/binding vigente, sin privilegio propietario/admin. Definir campos visibles: Rules no ocultan campos de un documento permitido; usar respuesta minimizada o proyección solo si la privacidad lo requiere.
10. Outbox: procesador independiente LOCAL con adaptador FCM sustituible, recuperación de eventos pendientes, lease/backoff y registro de dispositivos por UID. Triggers/reintentos Cloud y eventual barrido se seleccionan y autorizan aparte; no prometer reintentos ilimitados de plataforma. Añadir SDK Messaging Android y servicio/canal/permiso cuando se apruebe esa fase. Fallo/denegación del push no bloquea bandeja/pedido.

Huella logística PROPUESTA: UID, storeId, addressId y coordenadas de origen/destino. Huella de consentimiento incluye líneas/cantidades/precios revisados y dirección textual/referencia/instrucciones, pago y quoteId. Así una edición textual relevante obliga a revisar sin forzar HeiGIT si no cambió la geometría. Política exacta de campos/versiones pendiente.
Rechazo definitivo durable PROPUESTO: REVIEW_REQUIRED terminaliza la intención original; luego una nueva revisión aceptada crea otro ID. Si hay incertidumbre no se crea otro. Esto evita que un POST antiguo confirme después de presentar un rechazo. Aprobar retención y códigos de intentos fallidos por separado; éxito no caduca con el quote.
Moneda PROPUESTA: Kotlin Long, operaciones TypeScript bigint, minor units JSON limitados a enteros seguros compatibles con HU15; si se requiere todo el rango Long, acordar transporte decimal string. Es límite técnico de representación, no tope comercial. No sumar/redondear dinero con Number/Double.

### Decisiones por aprobar progresivamente
| Bloque | Prioridad/dependencia | Decisión pendiente / recomendación |
| --- | --- | --- |
| A | P0, antes de código durable | Nombres/versionado/aislamiento legado; REST de confirmar/recuperar; payload canónico e intención terminal vs incierta; límites técnicos monetarios. |
| B | P0, después de A | Lista de cambios que requieren aceptación (proponer aumento y disminución, cambios de identidad visible/dirección/pago); quote hash/versiones/vencimiento; no ligar quote a generación local. |
| C | P0, después de A/B | Revisión persistente y migración Room 2→3, consumo exactamente una vez y política de edición mientras hay envío pendiente; ruta/guarda personal de recuperación independiente de comprar. |
| D | P0/P1, antes de creación final | Predicado PENDING/VERIFIED que permite recibir sin activación, coordinación de última revocación y compra; lecturas de histórico por responsable actual durante suspensión. |
| E | P1, después de pedido/evento | Bandeja Firestore vs HTTP/campos visibles/índices, dispositivos y procesador de reintentos; validación FCM real y recursos Cloud requieren autorización posterior. |

No reabrir Food API, PEN/efectivo, quote persistido/5 min, Room durable, idempotencia de éxito, outbox, responsables sin activación o vínculo por UID. La necesidad de adaptar una guarda personal se somete a alcance específico; no modifica aquí HU34 ni elimina sus restricciones comerciales. El protocolo transaccional debe leerse con índices/filas actuales y probar revocaciones; solo considerar un contador adicional por local si aparece una necesidad demostrada, no crearlo por uniformidad.

### Plan de incrementos y pruebas (PROPUESTO)
1. Contratos y política pura de compra/aceptación/idempotencia: tipos, snapshot, dinero, horarios/zonas, casos equivalentes Kotlin/TS. Commit ilustrativo feat(HU16 - checkout): definir contratos de confirmación y recuperación.
2. Cotización temporal persistida y referencia Android compatible con HU15, reutilización segura de quote y pruebas de cambio durante ruta, vencimiento/versión/UID; sin pedido todavía. feat(HU16 - delivery): persistir cotizaciones temporales por cliente.
3. Persistencia de intención/revisión/consumo Room y migraciones con esquemas; tests de 1→2→3 y 2→3, carro vacío, ABA, crash/UID/recibo duplicado. feat(HU16 - checkout): persistir intenciones y revisión del carrito.
4. Confirmación/recuperación transaccional foodApi, producto/categoría/configuración/negocio/local/horario/dirección/quote/responsables/exclusividad; petición paralela, permisos cambiantes, rechazo terminal y respuesta perdida. feat(HU16 - orders): confirmar pedidos de forma idempotente.
5. UI de Confirmar/Enviando/Incierto/Recuperado/Confirmado, recibo mínimo y guarda personal, reutilizando MAIN/Cart/Address; tests Compose/instrumentados de doble tap, timeout tras commit, relogin/cambio UID y edición posterior. feat(HU16 - checkout): integrar confirmación y recuperación del pedido.
6. Bandeja mínima y outbox/dispositivos/aviso, adapter FCM simulado en LOCAL; tests de fallo tras enviar, reintento, revocación y varios dispositivos; no aceptación/rechazo HU19. feat(HU16 - notifications): incorporar bandeja y avisos recuperables.
7. Regresión integral LOCAL y actualización documental; sin commit vacío. La entrega FCM real queda como aceptación posterior autorizada, no se acredita con Emulator.

No ejecutar integración Rider, pagos digitales, cancelación HU18, aceptación/rechazo HU19, preparación HU20–HU21, entrega HU22, historial completo HU17 ni indicadores HU28. Conservar IDs, snapshots/estado y quoteId para esos consumidores sin implementar sus acciones. Pruebas existentes HU14/HU15/HU34 aportan base, no acreditan HU16: esta auditoría no ejecutó nuevas suites. El cierre HU34 anterior reportó 655 casos distintos; no se presentan como ejecución de esta etapa.

### Dependencias Cloud y límites de verificación
AccessNavigationViewModel.enabled depende de LOCAL y foodApi rechaza access/* en Cloud. Por tanto, cerrar HU16 LOCAL no la habilita públicamente: antes de Cloud se necesitan las tareas pendientes de HU34, índices/reconciliación/reglas/endpoints/Android coordinados. No desactivar guardas ni transformar 404/fallo en comprador para saltar ese bloqueo.
No se consultaron pedidos, reglas desplegadas o documentos históricos reales. El esquema real de checkoutApi y posibles colisiones siguen sin verificarse; aislamiento propuesto permite desarrollar sin adoptar atributos del legado. Verificación de compatibilidad real antes de despliegue se mantiene pendiente. No hay garantía de entrega FCM real en Auth/Firestore/Functions Emulator. No se cambiaron otras HU, código, ramas, dependencias, secretos, reglas, datos o configuraciones.
## 16. Preparación técnica definitiva — diseño PROPUESTO para implementar

### 16.1 Verificación y fuentes

Rama ramaDante, HEAD bc71bcc638cb2859f8047b6b943b2026254bd044; árbol rastreado limpio antes de esta edición. Documento ignorado por Git. Inspección selectiva de AGENTS, HU15/HU34/arquitectura, fuentes originales CSV/PDF ya contrastadas en la auditoría precedente y componentes actuales. CSV HU16 registros lógicos 87–92 conserva CA01–06; PDF páginas 2–4 aporta snapshots y logística. No se encontró backlog v0.6 entre los documentos locales de docs/contexto. No se consultó Cloud, credenciales ni datos personales. No se ejecutaron suites en esta preparación; pruebas históricas HU34/HU15 no acreditan HU16.

EXISTENTE: revisión de compra y tarifa, HTTP autenticado, generación de sesión, Room v2, dirección Firestore/selección DataStore, coordinación comercial HU34. NO IMPLEMENTADO: pedidos/ledger/outbox/bandeja productivos, quoteId persistido, revisión durable del carrito, confirmación o recuperación. Arquitectura general antigua se usa con cautela: sus referencias pre-HU15 no sustituyen código.

### 16.2 Contacto real y compatibilidad

- FirebaseAuthRepository.kt:81 registra email/password/displayName sin teléfono. En :163–178 y :224–240 PhoneAuth vincula credencial al usuario; toAuthUser en :304 obtiene FirebaseUser.phoneNumber.
- FirebaseUserProfileStore.kt:52–58 guarda users/{uid}: uid/email/displayName/createdAt/updatedAt, NO teléfono. PhoneAuthViewModel conserva el borrador en SavedStateHandle, no constituye una fuente histórica del pedido. ProfileSettingsScreen.kt:121 muestra teléfono de Auth en solo lectura; la etiqueta «Verificado» es UI existente, no un atributo de pedido.
- PROPUESTA mínima: backend lee getUser(uid).phoneNumber, normaliza E.164 y guarda buyerSnapshot.phone como contacto; nombre de users/{uid}.displayName, con fallback de nombre Auth si falta perfil, nunca un nombre ficticio. Validar cambios contra el contacto revisado. No guardar phoneVerified ni exigir SMS nuevo por HU16. El flujo PhoneAuth existe, pero no se comprobó que toda cuenta histórica tenga teléfono.
- Si falta contacto, respuesta CONTACT_REQUIRED y carrito intacto; dirigir al flujo existente de completar teléfono solo cuando sea adecuado, sin alterar registro ni inventar otro campo. El tratamiento UX de cuentas sin teléfono debe revisarse antes de conectar Confirmar: DEC15 no autoriza una migración automática de perfiles ni nueva obligación general de SMS. Falta de getUser por red es transitoria, no rechazo terminal. Auth y Firestore no comparten transacción: no prometer atomicidad del teléfono o revocación Auth con el commit Firestore.
- Local: BusinessStore contiene businessId/name/address/latitude/longitude/phone/status/operationalStatus/operatingHours/deliveryRadiusKm. No hay evidencia de instrucciones/referencia de recojo propias: snapshot nullable explícito, no inventarlas. Dirección cliente tiene label/addressText/location.lat,lng/reference/instructions/updatedAt. No copiar teléfono del local como comprador.

### 16.3 Tipos, esquema y relaciones

Todos los campos siguientes son PROPUESTOS, no colecciones existentes. schemaVersion=1 para documentos HU16; contractVersion=1 para HTTP; consentVersion=1 y logisticsVersion=1 para huellas. Reutilizar nombres HTTP de DeliveryQuote actuales: quotedAt/expiresAt/deliveryFeeMinor/distanceMeters/storeLocation/deliveryLocation, añadir quoteId. Timestamps Firestore internos; ISO-8601 UTC con precisión de milisegundo en JSON. Cantidades enteras positivas; IDs de segmento válidos, sin slash y UUID para intentId. Kotlin Long y TS bigint al operar dinero; JSON/Firestore Number solo enteros seguros 0..9007199254740991. Convertir decimal de precio efectivo de forma exacta (incluida notación exponencial); rechazar fracciones de céntimo/overflow, sin redondear productos silenciosamente. Conservar redondeo aprobado del reparto.

| Documento | Campos mínimos propuestos |
|---|---|
| food_orders/{orderId} | schemaVersion, orderId, customerUid, intentId, requestHash, businessId, storeId, status=PENDING_CONFIRMATION, paymentMethod=CASH_ON_DELIVERY, currency=PEN, buyerSnapshot{uid,displayName,phone}, businessSnapshot{id,name}, storeSnapshot{id,name,phone}, pickupSnapshot{addressText,location,reference?,instructions?}, deliverySnapshot{sourceAddressId,label,addressText,location,reference?,instructions?}, items[], subtotalMinor, deliveryFeeMinor, totalMinor, quoteSnapshot, createdAt, updatedAt |
| items[] (dentro del pedido) | productId, categoryId, name, quantity, unitPriceMinor, lineTotalMinor. businessId/storeId ya están en cabecera; sin imágenes/basePrice innecesarios. Array inmutable sujeto al tamaño técnico validado del documento; no inventar límite comercial. |
| quoteSnapshot | quoteId, distanceMeters, deliveryFeeMinor, currency, tariffVersion, quotedAt, expiresAt, storeLocation, deliveryLocation. Histórico independiente de limpieza del quote. |
| food_orders/{orderId}/status_history/initial | schemaVersion, fromStatus=null, toStatus=PENDING_CONFIRMATION, actorUid, occurredAt; ID fijo initial impide duplicado. Sin CONFIRMED. |
| order_attempts/{uid}/intents/{intentId} | schemaVersion, requestHash, consentHash, outcome=SUCCEEDED o REJECTED_FINAL, orderId? o rejection{code}, createdAt, completedAt. No duplicar toda dirección/productos aquí. Éxito se conserva durante vida del pedido; no aplicar TTL genérico. |
| delivery_quotes/{quoteId} | schemaVersion, customerUid, businessId, storeId, addressId, logisticsVersion, logisticsHash, storeLocation, deliveryLocation, distanceMeters, deliveryFeeMinor, currency, tariffVersion, quotedAt, expiresAt. No consumed/singleUse. |
| order_events/{eventId} | schemaVersion, type=NEW_ORDER, orderId, businessId, storeId, status=PENDING/PROCESSING/DELIVERED/RETRY/FAILED, attemptCount, nextAttemptAt, leaseUntil?, leaseOwner?, createdAt, completedAt?, lastErrorCode?, deliveryProgress. No duplicar contacto/dirección/productos. |

orderId determinista SHA-256 con prefijo/versionado de UID+intentId; eventId determinista del pedido+NEW_ORDER. Identificadores no sustituyen autorización. Nuevo intentId permite nueva compra real de contenido igual.
Índices PROPUESTOS: food_orders(storeId ASC, createdAt DESC, __name__ DESC), order_events(status ASC,nextAttemptAt ASC) para recuperación; confirmar necesidades con consultas Emulator antes de añadir firestore.indexes.json. Acceso a intent/quote por ruta no requiere índice compuesto. Deshabilitar indexación de snapshots/items/payloads grandes si no se consultan; configuración debe preservar índices previos. No borrar índices Cloud sin autorización.

### 16.4 Huellas y consentimiento

requestHash = SHA-256 de JSON canónico versionado, campos permitidos explícitos, claves ordenadas, líneas ordenadas por productId y duplicados rechazados. Strings normalizados según contratos reales (trim donde corresponde, no cambiar semántica de instrucciones); fechas UTC, importes enteros, null explícito, coordenadas finitas y representación canónica consistente (-0→0). Excluir token, generación local, tiempos de reintento, UI y revisión Room. Backend calcula huella; hash cliente no es autoridad.
consentHash se deriva de UID, local/negocio e identidad visible, líneas/nombres/cantidades/precios efectivos revisados, dirección completa relevante, contacto, método/moneda/importes y referencia quoteId. El POST lleva valores esperados congelados para comparación, no para decidir importes. Subida Y bajada de precio, cambio de nombre visible, pago/contacto/dirección/local/productos exige revisión nueva. Cambios cosméticos ajenos a compra no invalidan por rutina.
logisticsHash separado: UID/storeId/addressId, coordenadas de recojo/entrega y versión logística. Radio y estado se revalidan contra valores actuales; texto de dirección cambiado requiere consentimiento nuevo sin pedir ruta si geometría/quote siguen válidos. Cambio de coordenadas/local exige cotización nueva. Tarifa publicada fija no se recalcula en Android; versiones aceptadas explícitas en backend (inicialmente solo lima-driving-car-v1), sin actualización silenciosa.
Una intención ya enviada no cambia quoteId/payload. Si queda incierta, primero recuperar/reintentar mismo ID; después de rechazo terminal, nueva revisión genera otro ID. Edición local permitida conserva intento original y evita nuevo envío automático; MVP bloquea otra confirmación mientras exista incertidumbre del UID, para no inducir compras duplicadas.

### 16.5 REST propuesto

Authorization Bearer Firebase ID Token; UID exclusivamente de identidad comprobada; Cache-Control:no-store; cuerpo y errores sin datos administrativos privados ni logs personales.

- POST /api/v1/orders: {contractVersion:1,intentId,storeId,addressId,quoteId,paymentMethod,expected:{buyerContact,storeSnapshot,deliverySnapshot,items,subtotalMinor,deliveryFeeMinor,totalMinor,currency}}. expected es consentimiento revisado, NUNCA autoridad. Campos exactos del DTO se fijan con las pruebas del primer incremento; sin UID configurable. 201 primera creación, 200 replay de SUCCEEDED; respuesta {contractVersion,intentId,outcome:"SUCCEEDED",order:recibo histórico mínimo}. No duplicar otro modelo de pedido para recibo/bandeja.
- GET /api/v1/order-attempts/{intentId}: 200 SUCCEEDED con recibo original o REJECTED_FINAL con código. 404 ATTEMPT_NOT_OBSERVED con outcome UNKNOWN y retryable=true: no significa fracaso; conservar ID. No exigir precio/quote todavía vigentes ni permiso START_PURCHASE para recuperar éxito propio. Otra cuenta no accede, aunque conozca ID.
- GET /api/v1/stores/{storeId}/orders?limit=...&cursor=...: lista acotada ordenada createdAt/ID y nextCursor opaco validado; autorizado por asignación vigente y correo verificado, incluso suspensión del negocio/local. Sin filtros solo visuales. Datos mínimos para bandeja y contacto autorizado; no listado global ni acciones HU19.
- Errores {error:{code,retryable,intentId?,outcome?}}. 400 INVALID_REQUEST; 401 UNAUTHENTICATED (no terminaliza intento desconocido); 403 PURCHASE_FORBIDDEN/STORE_ACCESS_DENIED; 409 INTENT_PAYLOAD_MISMATCH o rechazo terminal REVIEW_REQUIRED/QUOTE_EXPIRED/BUSINESS_UNAVAILABLE/STORE_UNAVAILABLE/NO_RESPONSIBLE; 422 CONTACT_REQUIRED/INVALID_LOCATION/OUTSIDE_COVERAGE; 503 TEMPORARILY_UNAVAILABLE/DATA_INCONSISTENT. Un código HTTP solo no acredita rollback: Android solo acepta REJECTED_FINAL explícito y persistido para la intención. Error antes de autenticar, parsear o persistir rechazo no lleva resultado terminal. Timeout/5xx/proceso interrumpido → incertidumbre, consultar o reenviar mismo payload, sin reintento agresivo.

### 16.6 Protocolo de servidor y concurrencia

1. Validar tamaño/DTO/token y obtener identidad/contacto actual fuera de transacción. No exigir a todos los compradores email verificado: preservar política HU34; responsable lector sí. Error de identidad temporal cierra acceso, no fallback a CLIENT.
2. Dentro de UNA runTransaction leer intento por UID+intentId. Hash incompatible → conflicto sin alterar original. SUCCEEDED devuelve pedido original; REJECTED_FINAL devuelve rechazo original, antes de volver a exigir condiciones de compra nueva.
3. Para intención nueva, leer contexto comercial/índices UID+correo y asignaciones con helpers que reciban la Transaction existente; no llamar facts() por HTTP ni anidar transacciones. Aplicar exclusividad HU34 sin crear segundo sistema de roles.
4. Leer negocio/local, cada producto/categoría/configuración única, dirección propia, quote y asignaciones del local. Precio store_product_configs efectivo; sin fallback al catálogo. Verificar relaciones y estados/stock cualitativo. Horarios America/Lima incluyendo jornada nocturna. Releer reloj en cada reintento; quote válido en el instante de decisión transaccional, no prometer que la hora de commit físico y Auth sean atómicas. Presupuesto de ejecución/timeout y pruebas de borde evitan aceptar una decisión vieja.
5. Asignación receptora: PENDING coherente sin boundUid o VERIFIED coherente, del negocio y local correctos, no REVOKED/LEGACY/RECONCILIATION_REQUIRED. PENDING no es permiso de lectura ni destinatario FCM hasta verificar/bindear identidad. Leer filas e índices de exclusividad pertinentes; revocación HU34 escribe las mismas filas. Prueba concurrente obligatoria: compra serializada antes de revocación puede existir; compra serializada después sin otro responsable se bloquea. No añadir contador/servicio nuevo sin necesidad demostrada.
6. Comparar consentimiento con fuentes actuales, logística/UID/versión/expiración y cobertura; calcular subtotal/total exactos en servidor. Todas las lecturas ANTES de escribir. Rechazo comercial concluyente → escribir solo ledger REJECTED_FINAL, no pedido. Datos corruptos/fallos técnicos → error recuperable, no persistir falsamente rechazo comercial.
7. Éxito → create pedido + historial initial + ledger SUCCEEDED + evento NEW_ORDER en commit atómico. Usar IDs deterministas; no escribir pedido/carrito ajeno ni colecciones legado. Dos transacciones paralelas convergen al mismo recibo. No HeiGIT/FCM ni llamadas Auth dentro del callback.
8. Cotización: proveedor fuera de transacción. Antes de persistir, transacción corta comprueba de nuevo negocio/local/dirección/geometría/cobertura; descarta resultado obsoleto. Consulta de quote previo vigente por ID autenticado evita HeiGIT. Si vence/cambia versión admitida, REVIEW_REQUIRED; no precio cero ni usar 500 céntimos. TTL solo limpieza futura autorizada.

Firestore Rules mantiene denegadas escrituras/lecturas directas de colecciones privadas nuevas; Admin SDK las omite: cada método HTTP debe autorizar explícitamente. No abrir acceso genérico a admin/owner. No confiar en URL opaca. Pruebas Rules demuestran denegación cliente; pruebas HTTP demuestran autorización backend.

### 16.7 Room y Android

PROPUESTA: carts añade revision INTEGER NOT NULL DEFAULT 0. Migración 2→3 preserva app_config y carritos/ítems, añade checkout_intents con PK(ownerUid,intentId), payloadVersion/payloadJson/requestHash, baseCartRevision, quoteId, state, orderId?, receiptJson?, receiptApplied, timestamps. Estado local PREPARED/IN_FLIGHT/UNKNOWN/SUCCEEDED/REJECTED_FINAL no se confunde con ledger servidor. Generar esquema 3.json mediante Room; registrar MIGRATION_2_3 junto a 1_2; probar 1→2→3 y 2→3 con datos reales sintéticos.
Toda mutación efectiva del carrito incrementa revisión durable, incluso vaciar/reconstruir; no-op/rechazo no incrementa. No eliminar cabecera/reiniciar contador. Overflow se rechaza explícitamente. Captura transaccional local compara revisión/contexto antes de persistir intención; solo luego HTTP. Cambios externos de dirección/sesión se verifican con mecanismos existentes, no una transacción ficticia Room+DataStore+Firebase.
Al recibir SUCCEEDED: UNA transacción guarda recibo y, si receiptApplied=false y revisión actual=baseCartRevision, consume ítems del carrito original, incrementa revisión y conserva cabecera; marca aplicación una vez. Si difiere revisión, guarda recibo sin vaciar; no limpiar después por igualdad de contenido. Recuperar recibo repetido no borra carrito nuevo. Otro UID nunca muestra ni consume. Si respuesta llega tras logout, se puede reconciliar registro dueño original, pero no publicar como sesión nueva; revisar política del coordinador existente, sin ampliar accesos.
CheckoutRepository amplía confirm/recover; Order histórico común en feature/orders/domain, no UI demostrativa. SessionSource y contexto HU34 existentes. PaymentMethod pasa a contrato Domain compartido al conectar, preservando valor actual. Receipt/recovery personal requiere READ_PERSONAL_PURCHASES y UID, no START_PURCHASE; no abrir toda navegación cuando Conflict. Compose Confirmar/Enviando/Resultado incierto/Recuperar/Confirmado sin reglas ni red directas; no historial HU17.

### 16.8 Bandeja, dispositivos y outbox

PROPUESTA mínima: users/{uid}/devices/{installationId} gestionado mediante operación HTTP autenticada PUT/DELETE /api/v1/devices/{installationId}; token FCM, platform=ANDROID, updatedAt, enabled. ID por instalación; evitar token de una cuenta registrado simultáneamente en otra al transferir login. No exponer token en logs/respuestas de bandeja. Endpoint y estructura auxiliares necesarios para DEC21, no otro backend. Registro no otorga permiso de pedidos.
Procesador Functions separado onDocumentCreated(order_events/{eventId}) con claim/lease transaccional; FCM fuera de transacción; resultado y progreso por dispositivo durables. Propuesta inicial de backoff 1/5/15 minutos, máximo cinco intentos de envío; recuperación de lease vencido por barrido acotado (Cloud Scheduler o alternativa operativa aprobada al desplegar). Discriminar errores permanentes/transitorios y eliminar/deshabilitar token inválido. Reintentos de trigger solos no garantizan nextAttemptAt ni recuperación tras último fallo; probarlo antes de declarar procesador completo.
Reautorizar responsables y dispositivos antes de cada envío; no exigir APPROVED para leer pedido anterior, pero sí asignación/binding/email vigente. Sin destinatarios efectivos, evento queda pendiente de recuperación/estado explícito, pedido permanece. Límite de reintentos no borra evento. Payload {eventId,orderId,storeId,type}; sin teléfono/dirección/precios. Cliente deduplica visualmente por eventId; FCM puede duplicarse tras crash entre envío y registro, no prometer exactly-once. Android servicio/canal/permiso de notificación; rechazo de permiso no afecta bandeja. FCM LOCAL con adaptador simulado, no existe emulador FCM que acredite entrega real.

### 16.9 Incrementos ejecutables (PROPUESTOS)

Rutas Android de producción bajo app/src/main/java/com/chaskifood/app/; las nuevas rutas de esta tabla se proponen, no existen todavía. Crear feature/orders/domain para un único pedido histórico reutilizable; checkout/data/local para persistencia de intenciones; functions/src/orders y notifications separan negocio/transporte/entrega. No crear services/mappers/use cases vacíos solo por uniformidad.

| Incremento | Archivos/módulos concretos propuestos | Resultado y pruebas antes del commit |
|---|---|---|
| HU16-1 contratos y políticas puras | nuevos feature/orders/domain/OrderModels.kt; checkout/domain/OrderConfirmation.kt; functions/src/orders/orderModels.ts, orderRequest.ts, orderPolicy.ts; sus pruebas JVM/TS | Esquema/DTO v1, huella, dinero, cambios de consentimiento, replay/incompatibilidad/rechazo/incertidumbre. Fixtures comunes sintéticos Kotlin/TS. No Room/endpoints/UI todavía. Commit feat(HU16 - orders): definir contratos y reglas de confirmación. |
| HU16-2 cotización durable | delivery/deliveryQuoteService.ts y deliveryQuotePolicy.ts si requiere constantes; nuevo delivery/deliveryQuoteStore.ts; index.ts; Android DeliveryQuoteReader.kt, DeliveryQuoteApi.kt, HttpDeliveryQuoteReader.kt, CheckoutModels.kt y lectores fake afectados | quoteId persistido, reutilización autenticada, 5 min y fuente vigente tras ruta. Emulator: UID ajeno, cambio geometría, textual sin HeiGIT, expiración/versión. HU15 retrocompatible coordinando backend/Android; fixtures se amplían, no debilitan. Commit feat(HU16 - delivery): persistir y reutilizar cotizaciones. |
| HU16-3 intención Room | CartEntity.kt, CartDao.kt, CartLocalCoordinator.kt; ChaskiDatabase.kt, DatabaseModule.kt; nuevo migration/Migration2To3.kt, checkout/data/local/CheckoutIntentEntity.kt, CheckoutIntentDao.kt, CheckoutIntentStore.kt; schema 3.json | Preparar antes de red, revisión en todas las mutaciones, aplicar recibo condicional/único. Migraciones 1→2→3/2→3, ABA, crash, UID, respuesta duplicada, no-op. Commit feat(HU16 - checkout): persistir intenciones y proteger el carrito. |
| HU16-4 confirmación y recuperación backend | nuevos orders/orderService.ts, orderHttp.ts; access/commercialAccessService.ts extracción mínima de helpers transaccionales; index.ts; pruebas HTTP/Emulator y Rules de privacidad | 4 escrituras atómicas, terminales coherentes, precios/horarios/dirección/contacto/roles/managers, recuperación histórica. Paralelos/revocación/timeout tras commit; ninguna llamada externa en TX. Commit feat(HU16 - orders): confirmar y recuperar pedidos idempotentes. |
| HU16-5 Android confirmación/recibo | nuevos checkout/data/remote/OrderApi.kt, HttpOrderConfirmationClient.kt; CheckoutModels.kt, FirebaseCheckoutRepository.kt, CheckoutDataModule.kt, CheckoutViewModel.kt, RealCheckoutScreen.kt; guarda/ruta personal mínima AccessNavigationPolicy.kt/ChaskiNavHost.kt; recibo mínimo en orders/presentation | Confirmar, incertidumbre y recuperación tras reinicio/relogin, consumo Room condicionado, contacto ausente explícito. JVM/Compose/E2E: sesión A→logout→A/B, doble tap, carrito cambiado, quote caducado; navegación personal no concede compra comercial. Commit feat(HU16 - checkout): integrar confirmación y recuperación. |
| HU16-6 bandeja HTTP mínima | orders/orderHttp.ts, nuevo orders/storeOrdersReader.ts; firestore.indexes.json si requerido; Android orders/domain/StoreOrdersRepository.kt, orders/data/HttpStoreOrdersRepository.kt, orders/presentation/StoreOrdersViewModel.kt y pantalla mínima/Hilt/navegación HU34 | Responsable vigente lista por local con paginación; propietario/admin/ajeno no, histórico accesible durante suspensión. No acciones HU19. Commit feat(HU16 - orders): incorporar bandeja autorizada del local. |
| HU16-7 aviso recuperable | nuevos functions/src/notifications/orderEventProcessor.ts, deviceRegistry.ts, fcmSender.ts; index.ts; Android core/notifications/ChaskiMessagingService.kt y registro autenticado; AndroidManifest.xml, Gradle/catálogo solo dependencia Messaging necesaria | Dispositivos por UID, lease/retry/reautorización, fallo FCM no afecta compra/bandeja, crash tras envío y dedup. Adaptador FCM local; recursos Cloud requieren aprobación separada. Commit feat(HU16 - notifications): procesar avisos persistentes de pedidos. |
| HU16-8 cierre integral LOCAL | suites existentes/nuevas Android, HTTP/Rules, migración y docs autorizadas al cerrar | Matriz CA01–06 y RA01–09 con ejecución/evidencia exactas; regresiones HU04/HU14/HU15/HU34, compileDebug. Sin commit vacío ni contar compilación como ejecución. Entrega FCM Cloud y despliegue quedan separados. |

Antes de HU16-5 resolver tratamiento de cuenta sin teléfono con evidencia de Auth Emulator, sin nueva arquitectura de perfil. Antes de HU16-4 demostrar serialización de asignaciones con HU34; ajustar solo extracción transaccional necesaria. En HU16-2/4 definir métrica de lecturas por tipo/documento y latencias separadas; no reutilizar la cifra 2+2N+C como costo total de confirmar. Sin HeiGIT en confirmación con quote vigente.

### 16.10 Riesgos materiales y cierre

1. HU34 actualmente LOCAL: AccessNavigationViewModel habilita LOCAL y index.ts devuelve 404 para access/* en Cloud. Antes de exposición pública de HU16 se necesita activación coordinada/autorizada HU34, reconciliación/índices y validación Cloud; no saltar guardas ni asumir acceso CLIENT ante fallo.
2. Fuente telefónica Auth disponible solo cuando cuenta la tiene; pruebas y UX para ausencia son necesarias. No inventar campo users.phone ni phoneVerified. Contacto/cambios Auth no son atómicos con Firestore; documentar instante de captura y revalidar inmediatamente antes de transacción.
3. Transacciones Firestore no abarcan Auth, Room, FCM o HeiGIT. Idempotencia/recuperación/revisión/outbox resuelven consistencia entre sistemas; cancellation no equivale a rollback. Probar última revocación y bordes de vencimiento antes de cerrar.
4. Snapshot con teléfono/dirección requiere autorización backend y logs minimizados. Reglas catch-all no protegen automáticamente Admin SDK. Diseñar límites técnicos antes de superar tamaño/transacción; no introducir topes comerciales arbitrarios.
5. FCM real/reintentos programados requieren recursos/permisos Cloud todavía no autorizados. LOCAL prueba política/procesador simulado, no entrega en dispositivo ni comportamiento vivo del scheduler.

No es necesario inspeccionar colecciones checkoutApi para empezar: esquema propio DEC08/16; no adoptarlo, modificarlo o borrarlo. Compatibilidad operativa con despliegues y configuración compartida se verifica al autorizar Cloud, sin condicionar contratos a atributos del legado. No se implementa Rider, pago digital, aceptación/rechazo, preparación ni historial completo.

Recomendación: autorizar primero HU16-1, contratos y políticas puras con fixtures de compatibilidad Kotlin/TypeScript y pruebas de dinero/consentimiento/idempotencia; sin persistir ni crear pedidos aún. El orden posterior queda por dependencias en 16.9. Este documento es preparación: NO acredita implementación o pruebas HU16, ni autoriza commits/despliegues.


## 17. HU16-8 — Cierre funcional LOCAL (2026-10-29)

**Veredicto: HU16 CERRADA CON LIMITACIONES DOCUMENTADAS.** Validación funcional LOCAL; no equivale a aprobación de publicación.
Base auditada: `f2799c31004c6a9452d98b950d11c1123e8e1a0c`. Commit de pruebas de cierre: `40bb134ad5126afb9cbfeac9204c43e2380c2ec1`.
Esta sección actualiza la evidencia de implementación; no cambia DEC01–DEC23. Las secciones anteriores conservan el diseño/diagnóstico histórico y sus «pendientes» no describen por sí solos el código actual.

### Entorno, alcance y recorrido real

Proyecto demo-chaski-hu15; Auth 127.0.0.1:9099, Firestore :8089 y Functions :5001. Android LOCAL, host 127.0.0.1 mediante adb reverse; AVD HU34_Local_20261028, API 37. Node v22.23.4. Gradle 9.6.0: launcher JDK 21.0.11 y daemon Java 25 según gradle-daemon-jvm.properties. Debug usa una copia temporal de la plantilla demo, eliminada al terminar; no se sobrescribieron JSON personales. HeiGIT sustituido por rutas SIMULATED y secreto ficticio. Sin acceso a Cloud, claves reales, checkoutApi ni modificaciones CONECTADO/Release.

Se reutilizó orderAndroid.fixture.ts. En modo HU16_CLOSURE_ANDROID=enabled se ejecuta primero Android comprador, se inspeccionan los documentos reales de Emulator y se procesa ese MISMO order_event con OrderEventProcessor + SimulatedNoticeSender. El payload emitido se pasa, sin token, al receptor Android mediante un argumento base64url de instrumentación; Android valida su correspondencia con la bandeja HTTP, presenta aviso genérico y abre MainActivity/bandeja protegida. Se verifica una nueva consulta autorizada y revocación posterior. Esta inyección controlada sustituye exclusivamente el salto de entrega FCM: NO es una entrega FCM real.

En comprador: autenticación con Auth SDK real del Emulator; selección de local/producto y edición de cantidad mediante DefaultCartRepository real; dirección registrada Firestore preparada por fixture. La observación local de direcciones/selección usa TestCheckoutAddresses/TestCheckoutSelection controlables; no acredita navegación manual del catálogo ni del selector HU04. La revisión y confirmación sí se hicieron desde RealCheckoutScreen + CheckoutViewModel reales en AVD, con HTTP y SQLite de archivo reales. Un interceptor comprueba la intención IN_FLIGHT en Room ANTES del POST. Se muestra recibo, consume carrito una vez y reabre SQLite para comprobar SUCCEEDED y receiptApplied.

Dos ejecuciones del recorrido: normal y pérdida controlada de la respuesta DESPUÉS de un HTTP 201 real. En la segunda se comprueban UNKNOWN y carrito conservado, se pulsa «Consultar resultado» y GET recupera el mismo pedido; contador POST = 1. Ambas enlazan pedido, intento, historial, outbox, aviso simulado y bandeja. Se verificaron teléfono nacional del local 987654321, etiqueta vacía y teléfono de comprador null, todos ficticios.

### Resultados de la campaña

| Suite | Aprobadas | Fallidas finales | Omitidas | Evidencia local ignorada |
|---|---:|---:|---:|---|
| JVM Android, ejecución forzada | 529 | 0 | 0 | app/build/test-results/testDebugUnitTest/TEST-*.xml |
| Functions unitarias | 110 | 0 | 0 | app/build/hu16-closure-unit.log |
| HTTP/Rules/Firestore Emulator | 137 | 0 | 0 | app/build/hu16-closure-http.log |
| AVD Room/carrito/coordinación | 55 | 0 | 0 | app/build/hu16-closure-room.log |
| AVD Checkout/identidad/repositorios reales | 37 | 0 | 0 | app/build/hu16-closure-remote.log |
| AVD recorridos comerciales HU34 | 6 | 0 | 0 | app/build/hu16-closure-commercial.log |
| AVD Compose/navegación/regresiones | 72 | 3 | 0 | app/build/hu16-closure-compose.log |
| AVD recorrido normal enlazado | 3 | 0 | 0 | app/build/hu16-closure-journey.log |
| AVD mismo recorrido con respuesta 201 perdida | 3 | 0 | 0 | app/build/hu16-closure-timeout-journey.log |

Las dos últimas filas repiten tres tests con modos distintos; no son seis casos únicos. En AVD: 176 casos únicos, 173 aprobados y 3 fallidos externos; 179 ejecuciones válidas contando ambos modos. Los intentos previos invalidados por omitir hu15Host/hu34Host (10.0.2.2 no accesible en este AVD) no se suman como aprobados: se repitió la tanda con ambos argumentos 127.0.0.1. El primer intento UI del test nuevo también se corrigió para desplazar LazyColumn con performScrollToNode antes de buscar elementos no compuestos. No se debilitó ninguna aserción funcional.

TypeScript compilado; assembleDebug, assembleDebugAndroidTest y testDebugUnitTest ejecutados con BUILD SUCCESSFUL. Compilar androidTest no se contabiliza como ejecutarlo. Cloud no ejecutado ni incluido entre «omitidas» de estas suites.

### Matriz CA y RA

Los APROBADO siguientes se limitan al tipo de prueba indicado y a LOCAL.

| ID | Estado | Evidencia y garantía |
|---|---|---|
| CA01 | APROBADO | `functions/test/orderHttp.emulator.ts:137`; mutaciones comerciales antes del commit y revocación concurrente. HTTP/Firestore real LOCAL. |
| CA02 | APROBADO | `functions/test/orderHttp.emulator.ts:155`; precio efectivo del local y céntimos exactos, no total confiado al cliente. |
| CA03 | APROBADO | `functions/test/orderHttp.emulator.ts:86`; `app/src/androidTest/java/com/chaskifood/app/feature/checkout/data/CheckoutOrderHttpEmulatorTest.kt:45`; recibo y persistencia PENDING_CONFIRMATION. |
| CA04 | APROBADO | `functions/test/orderHttp.emulator.ts:207`; conserva líneas históricas tras editar/borrar fuentes. |
| CA05 | APROBADO | `functions/test/orderHttp.emulator.ts:207`; snapshot independiente de dirección; dirección ajena rechazada. |
| CA06 LOCAL | APROBADO | `functions/test/orderAndroid.fixture.ts:90` → `app/src/androidTest/java/com/chaskifood/app/core/notifications/OrderNotificationEmulatorTest.kt:32`; se inyecta en Android el payload realmente emitido por el procesador con transporte simulado. |
| CA06 entrega FCM | PENDIENTE DE CLOUD | No existe prueba de entrega real de FCM ni recepción en un dispositivo con token real. No confundir aceptación del transporte con recepción. |
| RA01 | APROBADO | `functions/test/orderHttp.emulator.ts:123`; `app/src/test/java/com/chaskifood/app/feature/checkout/presentation/CheckoutConfirmationViewModelTest.kt:49`; concurrencia backend y doble llamada ViewModel. |
| RA02 | APROBADO | `app/src/androidTest/java/com/chaskifood/app/feature/checkout/data/CheckoutOrderHttpEmulatorTest.kt:45` (hu16LoseResponse=enabled): SocketTimeoutException controlada DESPUÉS de HTTP 201 real; UNKNOWN en SQLite, GET recupera el mismo recibo, un solo POST. |
| RA03 | APROBADO | `app/src/test/java/com/chaskifood/app/feature/checkout/domain/OrderConfirmationTest.kt:45`; `app/src/test/java/com/chaskifood/app/feature/checkout/presentation/CheckoutViewModelTest.kt:69`; hashes compartidos y nueva aceptación de cambios. |
| RA04 | APROBADO | `functions/test/orderHttp.emulator.ts:130`; contenido/UID/local/coordenadas e importes incompatibles rechazados. |
| RA05 | APROBADO | `functions/test/orderHttp.emulator.ts:137`; `functions/test/orderHttp.emulator.ts:197`; escenarios negocio/local/oferta/dirección/cotización inválidos. |
| RA06 | APROBADO | `functions/test/orderHttp.emulator.ts:123`; `functions/test/orderHttp.emulator.ts:197`; `app/src/androidTest/java/com/chaskifood/app/feature/checkout/data/CheckoutOrderOperationsTest.kt:109`; Firestore serializa cambios y Room coordina envíos. |
| RA07 | APROBADO | `app/src/androidTest/java/com/chaskifood/app/feature/checkout/data/CheckoutOrderOperationsTest.kt:132`; `app/src/androidTest/java/com/chaskifood/app/feature/checkout/data/local/CheckoutIntentStoreTest.kt:97`; no se borra carrito editado, desconocido ni reconstruido idéntico. |
| RA08 | APROBADO | `functions/test/orderNotifications.emulator.ts:142`; `functions/test/orderNotifications.emulator.ts:97`; fallo de aviso preserva pedido, progreso y evidencia durable. |
| RA09 | APROBADO | `app/src/androidTest/java/com/chaskifood/app/feature/checkout/data/CheckoutOrderOperationsTest.kt:138`; `app/src/androidTest/java/com/chaskifood/app/feature/checkout/data/local/CheckoutIntentStoreTest.kt:148`; `app/src/test/java/com/chaskifood/app/feature/checkout/presentation/CheckoutConfirmationViewModelTest.kt:64`; persistencia, cambio de UID y recreación con dobles controlados/SQLite real. |

### Matriz DEC01–DEC23

| Decisión | Estado LOCAL | Evidencia y alcance |
|---|---|---|
| DEC01 | APROBADO | `functions/test/deliveryQuotePersistence.emulator.ts:28`; Cotización privada persistida y reutilizada sin segunda llamada al lector de rutas. |
| DEC02 | APROBADO | `app/src/androidTest/java/com/chaskifood/app/feature/checkout/data/local/CheckoutIntentStoreTest.kt:72`; Intención durable antes de HTTP; reapertura SQLite conserva payload original. |
| DEC03 | APROBADO | `functions/test/orderHttp.emulator.ts:115`; Asociación exitosa recuperable incluso cuando expiran la cotización o las condiciones anteriores. |
| DEC04 | APROBADO | `functions/test/orderNotifications.emulator.ts:106`; Outbox independiente y progreso por dispositivo; FCM simulado; redelivery Cloud no acreditada. |
| DEC05 | APROBADO | `functions/test/orderHttp.emulator.ts:194`; Asignación receptora no exige cuenta activada/online; lectura sí verifica identidad/binding. |
| DEC06 | APROBADO | `functions/test/deliveryQuotePersistence.emulator.ts:38`; Mismo UID redescubre quoteId, sin vincularlo a generación Android; Ready/consentimiento no se restaura. |
| DEC07 | APROBADO | `app/src/androidTest/java/com/chaskifood/app/feature/checkout/data/CheckoutOrderHttpEmulatorTest.kt:45`; Se reutiliza foodApi, Retrofit y la autenticación existentes; no segundo backend. |
| DEC08 | APROBADO | `functions/test/orderAndroid.fixture.ts:77`; Colecciones propias food_orders/order_attempts/delivery_quotes/order_events verificadas en Emulator. |
| DEC09 | APROBADO | `functions/test/orderHttp.emulator.ts:130`; ID estable, hash inmutable y conflicto ante otro contenido. |
| DEC10 | APROBADO | `app/src/test/java/com/chaskifood/app/feature/checkout/domain/OrderConfirmationTest.kt:45`; Vectores de consentimiento, precios aumentados/disminuidos y contexto comercial/logístico. |
| DEC11 | APROBADO | `app/src/androidTest/java/com/chaskifood/app/core/database/Migration2To3Test.kt:30`; Migración no destructiva y revisión persistente; ABA probado. |
| DEC12 | APROBADO | `functions/test/orderHttp.emulator.ts:197`; Al menos un responsable y coordinación HU34 en la transacción final. |
| DEC13 | APROBADO | `functions/test/storeOrdersHttp.emulator.ts:56`; Bandeja HTTP persistente independiente del push, sin acciones HU19. |
| DEC14 | APROBADO | `app/src/androidTest/java/com/chaskifood/app/feature/checkout/data/CheckoutOrderHttpEmulatorTest.kt:45`; POST /orders y GET /order-attempts/{intentId} reales contra Functions Emulator. |
| DEC15 | APROBADO | `functions/test/orderHttp.emulator.ts:227`; Snapshots preservan contacto nacional, etiqueta vacía y comprador sin teléfono. |
| DEC16 | APROBADO | `functions/test/orderHttp.emulator.ts:233`; Pedido, intento, historial inicial y evento atómicos; fallo de escritura no deja registros parciales. |
| DEC17 | APROBADO | `functions/test/orderHttp.emulator.ts:241`; Revalidación dentro de transacción/reintentos; sin HeiGIT ni FCM en callback repetible. |
| DEC18 | APROBADO | `app/src/androidTest/java/com/chaskifood/app/feature/checkout/data/CheckoutOrderHttpEmulatorTest.kt:45`; UNKNOWN no se transforma en rechazo; recuperación explícita conserva intentId y evita un segundo POST. |
| DEC19 | APROBADO | `functions/test/storeOrdersHttp.emulator.ts:143`; Auth y autorización por local/UID; Firestore privado denegado directamente. |
| DEC20 | APROBADO | `functions/test/deliveryQuotePersistence.emulator.ts:67`; Cinco minutos y borde exacto del servidor; TTL no decide validez. |
| DEC21 | APROBADO | `functions/test/orderNotifications.emulator.ts:151`; Dispositivos, renovación, transferencia y logout; revocación antes de reintentos; payload mínimo. |
| DEC22 | APROBADO | `app/src/androidTest/java/com/chaskifood/app/feature/checkout/data/CheckoutOrderHttpEmulatorTest.kt:45`; Integración existente Checkout/Room/Retrofit y aplicación única del recibo. |
| DEC23 | APROBADO | `app/src/androidTest/java/com/chaskifood/app/core/database/Migration2To3Test.kt:51`; Matriz y campaña ejecutadas. Tres fallos de pruebas externas se conservan explícitos; no se declara toda la regresión verde. |

### Regresiones y fallos preservados

| Área | Estado | Evidencia |
|---|---|---|
| HU04: validación, selección por UID y respuestas tardías | APROBADO | JVM FirebaseAddressRepositoryTest, AddressViewModelTest, DataStoreAddressSelectionStoreTest; commercialRules.emulator.ts protege direcciones; snapshot de compra y dirección ajena en orderHttp.emulator.ts. No acredita GPS/mapa manual. |
| HU14: políticas, importes, concurrencia y persistencia por UID | APROBADO | JVM CartPolicy/CartMoney/CartViewModel; AVD DefaultCartRepositoryTest, CartDaoTest, CartLocalCoordinatorTest y CartScreenTest. |
| HU15: revisión, precios, elegibilidad, expiración y cambio de contexto | APROBADO | JVM CheckoutViewModelTest y AVD FirebaseCheckoutRepositoryEmulatorTest/RealCheckoutScreenTest; HTTP de elegibilidad/cotización. |
| HU34: identidad, permisos, exclusividad y recorridos reales | APROBADO | HTTP commercialAccess/commercialRules; AVD AccessContextEmulatorTest, CommercialRepositoryEmulatorTest, AccessHostEmulatorTest y CommercialJourneyEmulatorTest. |
| Borrador de local: expectativa de edición/restauración | FALLIDO | DraftRestorationTest.editedStoreDraftSurvivesSavedInstanceStateRestoration, línea 48. Espera «Local borrador»; nodo retenido contiene « borradorLocal». El test inserta texto sin fijar posición de cursor. No es la intención de compra Room. |
| Borrador de producto: expectativa de edición/restauración | FALLIDO | DraftRestorationTest.productDraftSurvivesSavedInstanceStateRestoration, línea 64. Espera «Pollo al horno»; conserva « al hornoPollo». Misma expectativa de inserción/cursor. |
| Discover: texto de distancia | FALLIDO | AvailableStoresContentTest.muestraInformacionDelLocalYPermiteElegirlo, línea 54, exige «Criolla · 1,2 km»; componente usa String.format(es-PE). Requiere revisión específica del formato/expectativa en API 37; no se cambió para hacerlo pasar. |

Procedencia comprobada: DraftRestorationTest tiene último cambio 5cd6028 y AvailableStoresContentTest a1ea24d. git diff entre el padre de HU16-1 (7621494^) y el HEAD auditado no muestra cambios en esos tests ni en ManageStoresScreen, ManageCatalogScreen o Discover/presentation. Son componentes/tests anteriores y fuera de HU16; no se compiló un APK histórico para atribuirles una regresión binaria exacta. Se documentan como fallos observados de regresiones ajenas, no se los marca APROBADO ni se corrigen sin ampliar el alcance.

### Atomicidad, persistencia e integridad verificadas

- food_orders, order_attempts/{uid}/intents, status_history/initial y order_events se crean en el mismo commit; errores inyectados no dejan registros parciales. Las repeticiones compatibles recuperan el resultado, las incompatibles no reemplazan la intención.
- Room v3 y migraciones 1→2, 2→3 y cadena 1→2→3 conservan app_config, carritos y sus cantidades/importes; sin fallback destructivo. DatabaseModule registra ambas migraciones. Se probó reapertura, consumo único y protección ABA mediante revisión persistente.
- Firestore privado denegado a clientes directos; HTTP reautoriza UID/local y bindings HU34. Responsable asignado conserva lectura durante suspensión; propietario/admin no obtienen bandeja automáticamente.
- Índices versionados coherentes con consultas: delivery_quotes(logisticsHash,tariffVersion,quotedAt DESC) y food_orders(storeId,businessId,status,createdAt DESC,__name__ DESC). No se desplegaron: Emulator no acredita que estén construidos/activos en Cloud.
- Outbox registra lease, attemptCount, tiempos, backoff y progreso por dispositivo. No muta pedidos al fallar FCM. Redelivery del handler recupera lease abandonado en pruebas controladas; no se afirma redelivery automática real de Cloud.
- Dispositivos privados asociados al UID autenticado, renovación con versión por instalación, desvinculación y rechazo de registro atrasado; tokens inválidos retirados y autorización revalidada antes de cada envío/reintento. Payload sin comprador, teléfono, dirección, coordenadas, productos o instrucciones.

### Pendientes de Cloud y límites operativos

Todos los siguientes quedan PENDIENTE DE CLOUD, no bloquean las pruebas LOCAL pero impiden declarar HU16 publicada:

1. Activación coordinada HU34 + HU16: endpoints access/orders/attempts/inbox/devices están cerrados fuera de LOCAL y newOrderNotifier no procesa Cloud. Validar explícitamente guardas de runtime/target al habilitarlo; no basta desplegar esta rama.
2. Token FCM real: auto-init/aprovisionamiento deshabilitado; LOCAL utiliza tokens ficticios. Validar registro/renovación/desvinculación real, permiso Android 13+, background/Doze, app cerrada y logout offline en dispositivo autorizado. La bandeja funciona sin push; no se acredita recepción real aquí.
3. Functions v2 retry:true y lease no son un scheduler ni una garantía ilimitada. La redelivery real y su ventana operativa deben comprobarse al publicar; una interrupción fuera de la ventana requiere procedimiento de replay aprobado. EXHAUSTED/NO_RECIPIENTS conservan evidencia; NO_RECIPIENTS no implica aviso automático posterior al activarse un dispositivo.
4. FCM tiene entrega incierta y puede duplicarse si se interrumpe entre envío y persistencia del progreso. SENT/ACCEPTED no significa recibido. La revocación no puede retirar mensajes ya enviados; Android vuelve a validar sesión y consulta HTTP antes de detalles.
5. Verificar reglas Cloud sin sobrescribir trabajo ajeno, índices construidos, región southamerica-west1, runtime Node 22, permisos de servicio para Auth/Firestore/FCM/Eventarc, secreto backend y presupuesto/cuotas. No se inspeccionó Cloud ni se cambió facturación, secretos o checkoutApi.

### Cambios de este cierre y reproducción

Solo se modificaron tres archivos de pruebas existentes: CheckoutOrderHttpEmulatorTest (UI real, SQLite persistente, intención antes de red, pérdida de 201 y recuperación), OrderNotificationEmulatorTest (acepta y valida el payload capturado del procesador) y functions/test/orderAndroid.fixture.ts (enlace secuencial de las fases y comprobación de registros). No hubo cambio de lógica de producción, reglas, esquema Room, contratos, tarifas ni otras historias.

Usar los requisitos y emuladores del README, Node 22, pnpm 11.25.0 y proyecto demo. Con APKs LOCAL instalados y adb reverse 9099/8089/5001, ejecutar el fixture existente dentro de emulators:exec con HU16_INBOX_ANDROID=enabled, HU16_NOTIFICATIONS_ANDROID=enabled y HU16_CLOSURE_ANDROID=enabled. HU16_CLOSURE_DROP_RESPONSE=enabled activa únicamente la pérdida controlada del HTTP 201 en el test; desactivado verifica éxito normal. Usar CHASKI_ROUTE_FIXTURE=enabled y secreto ficticio; nunca una clave real. Las demás tandas Android necesitan -e hu15Host 127.0.0.1 y -e hu34Host 127.0.0.1. El fixture prepara/revoca POST_NOTIFICATIONS antes de la instrumentación para no matar el proceso durante la prueba.

Los logs app/build son ignorados y pueden desaparecer con clean; esta matriz conserva los resultados y las referencias a pruebas versionadas. Documentación local fuera de Git. Sin push ni despliegues.

Estado Git final: ramaDante, HEAD 40bb134ad5126afb9cbfeac9204c43e2380c2ec1; un commit de pruebas, árbol rastreado limpio. Solo este documento ignorado actualizado localmente. Emuladores y adb reverse iniciados por la campaña detenidos/retirados al finalizar; sin push ni despliegue.


### Prepublicación HU34 + HU16 — preparación local A (2026-10-29)

Base auditada: `40bb134ad5126afb9cbfeac9204c43e2380c2ec1`. Preparación local: exclusiones explícitas del paquete Functions, compilación limpia y verificación automática predeploy sin consultas Cloud; corrección de expectativas de cursor/restauración y formato es-PE en pruebas. Node 22.23.4 y pnpm 11.25.0: compilación TypeScript y 112/112 pruebas unitarias Functions aprobadas. Android LOCAL: assembleDebug y cuatro pruebas instrumentadas (dos DraftRestorationTest y dos AvailableStoresContentTest) aprobadas en HU34_Local_20261028/API 37. Se usó la plantilla demo mediante un init script temporal ignorado, sin sobrescribir los JSON personales. No se repitieron suites HTTP/Firestore/FCM; no se acredita nueva validación Cloud.

Pendiente, NO implementado en este incremento:
- Android CONECTADO: habilitar las guardas HU34 reales, consentimiento HU16, bandeja y endpoints de dispositivos coordinadamente; mantener Release cerrado hasta configurar y validar una distribución autorizada.
- foodApi: habilitar los endpoints restringidos a LOCAL con validación explícita de proyecto/target; verificar contratos, reglas e índices publicados y compatibilidad de datos mediante una futura inspección Cloud autorizada. No asumir que la versión publicada equivale al código local.
- FCM: conectar FirebaseNoticeSender al trigger, habilitar tokens reales/renovación/desvinculación segura, probar permisos/background/Doze y definir recuperación de eventos pendientes/agotados sin duplicar pedidos ni borrar progreso. No activar rutas simuladas en Cloud.
- checkoutApi: decisión de publicación futura: retirada definitiva y controlada una vez comprobado que foodApi lo reemplaza y no quedan consumidores dependientes. No borrar ahora funciones, colecciones ni datos históricos.

Los hooks locales no equivalen a preparación completa para despliegue. Proyecto Blaze confirmado por el usuario; configuración/IAM/versiones Cloud todavía pendientes de inspección autorizada. Sin push, despliegue ni acceso a secretos. Este documento continúa ignorado y no forma parte del commit.


### Prepublicación HU34 + HU16 — B1: preparación CONECTADO (2026-10-29)

Base `6ac909831fa9ce5d5c544db85c02f0dc9daefd27`, ramaDante. B1 habilita en código los endpoints comerciales, confirmación/recuperación y bandeja para runtime CLOUD válido de foodApi en chaski-food-9adba. Se conserva el mismo coordinador HU34, Auth vigente y servicios transaccionales HU16; no se cambiaron contratos REST, políticas comerciales, tarifas, reglas, índices, esquema Room v3 ni datos históricos. El handler HTTP separado permite probar política CLOUD con SDK exclusivamente demo. El runtime rechaza proyectos/targets no autorizados y mezcla con emuladores/fixtures.

Android comparte una validación explícita de modo/proyecto/URL en core/firebase: LOCAL solo demo/loopback; CONECTADO solo proyecto y URL autorizados. Las guardas comerciales se aplican siempre, incluso ante configuración indisponible. Se retiraron los caminos CONECTADO antiguos que omitían contexto HU34 o bindings UID/VERIFIED. Checkout conserva el consentimiento y vuelve a comprobar sesión/contexto después de leer el comprador; la recuperación sigue asociada a UID/intención y Room v3. Release permanece cerrado.

Evidencia ejecutada en esta preparación:
- Node 22.23.4/pnpm 11.25.0: TypeScript y 114/114 unitarias Functions aprobadas; verificador de paquete: 57 archivos, sin crear archivo de subida.
- 141/141 pruebas HTTP/Firestore Emulator aprobadas secuencialmente con laboratorio demo limpio. Incluyen handler CLOUD con adaptadores Auth/Firestore demo: propietario suspendido, local ajeno, credencial obsoleta/deshabilitada, privacidad, idempotencia y recuperación del comprador. No equivalen a ejecución Cloud.
- Android assembleDebug y 539/539 pruebas JVM aprobadas tanto LOCAL como CONECTADO. El APK CONECTADO no se instaló/ejecutó. Configuración y JSON personales preservados; para LOCAL se usó la plantilla demo mediante init script ignorado.
- 20/20 instrumentadas aprobadas en AVD HU34_Local_20261028/API37: coordinación de pedidos/Room 10, bandeja Compose 5, grafo HU34 1, repositorios comerciales 2, fixture real Android→Room→HTTP Emulator→confirmación idempotente→bandeja 2. Las pruebas que requieren loopback usaron APK LOCAL 127.0.0.1 y adb reverse.
- Las campañas iniciales detectaron interferencia entre suites concurrentes y revisiones de invitaciones fijas conservadas entre ejecuciones; se repitieron secuencialmente con datos demo limpios, sin debilitar esas pruebas. Las nuevas expectativas se ajustaron al contrato vigente: BUSINESS_UNAVAILABLE y no exigir verificación de correo a clientes sin asignaciones.

B2 NO implementado: notifier Cloud y registro de dispositivos Cloud permanecen cerrados; no se habilitaron tokens/envíos FCM reales. Continúa pendiente comprobar runtime/target del notifier, conectar su emisor real y recuperar eventos antiguos/agotados mediante procedimiento aprobado. No publicar la solución completa como validada hasta verificar Cloud, datos, reglas/índices y B2. La retirada futura de checkoutApi sigue condicionada a comprobar reemplazo/consumidores y autorización específica; nada histórico fue modificado. Sin acceso Cloud, despliegue, push ni cambios de secretos reales. Documentación local ignorada fuera del commit.

> Nota: el párrafo anterior describe el estado al cierre de B1; B2 se documenta a continuación.


### Prepublicación HU34 + HU16 — B2: integración FCM en código (2026-10-30)

Base `1ccb142096d0a6469ba4c95e5e8f00e2744d5db0`, ramaDante. Commits: `ac0ddfb` (B2.1 backend) y `b71f270` (B2.2 Android). Sin acceso Cloud, despliegue, push ni cambios de secretos, IAM, facturación, reglas, índices, Room o checkoutApi. Decisiones aplicadas: DEC-HU16-04/05/12/13/19/21 y HU34 §3–4/§7 sin reabrirlas. Acuerdos de esta fase con Dante: registro de dispositivo restringido en Android y backend; recepción Android solo con comprobaciones locales síncronas.

Cambios B2.1 (functions):
- `resolveRuntimeEnvironment(env, target)`: lista cerrada `foodApi | newOrderNotifier`; en CLOUD exige `FUNCTION_TARGET === target`. Resto de guardas sin cambios.
- `noticeSenderForRuntime`: CLOUD → `FirebaseNoticeSender` (fixture prohibido); LOCAL → simulado solo con `CHASKI_NOTIFICATIONS_FIXTURE=enabled`, si no inactivo. `newOrderNotifier` reconoce sin tocar el evento ante runtime inválido (no transitorio; el evento queda durable para replay) y conserva retry:true, región y límites.
- `/api/v1/devices` abierto en CLOUD. PUT: `verifyCommercialIdentity` + `requireAnyStoreOrderReader` (misma política que `requireStoreOrderReader`: propietario excluido, correo verificado, binding UID↔correo, ≥1 asignación VERIFIED) → 403 `DEVICE_NOT_ELIGIBLE`. DELETE: cualquier UID autenticado (`verifyBuyerToken`, verificador genérico no modificado). Registrar no concede permisos; cada envío reautoriza.

Cambios B2.2 (Android, `core/notifications`):
- Firebase Messaging efectivo 24.1.0 (BOM 33.10.0): `getToken()/deleteToken()`; no existen `register/unregister` en esta versión. Token string compatible con `DeviceRegistry`. `PushTokenSource` solo se usa en CONECTADO; auto-init sigue deshabilitado.
- `NoticePolicy` (puro): registra solo con estado RESOLVED del mismo UID y alguna concesión READ_STORE_ORDERS; muestra aviso solo si el UID persistido de Firebase Auth coincide con `recipientUid` y no hay denegación vigente, CONFLICT, SIGNED_OUT ni UNAVAILABLE. LOADING/ERROR (proceso frío) permiten solo el aviso genérico; la apertura reautoriza contra la bandeja HTTP.
- `DeviceRegistrar`: revisa la sesión tras cada suspensión, serializa alta/baja y bloquea altas tardías al iniciar logout. Relogin del mismo UID es otra sesión. Revocación resuelta → baja best-effort. `onNewToken` vuelve a registrar solo la sesión elegible.
- `onMessageReceived` ya no espera red ni `runBlocking`. Logout: DELETE + `deleteToken()` best-effort en CONECTADO, nunca bloquea.

Evidencia ejecutada (Node 22.23.4, pnpm 11.25.0, proyecto demo, laboratorio aislado `.artifacts/b2-lab` con secreto ficticio):
- Functions: build, 117/117 unitarias, `verify:package` 57 archivos sin archivo de subida, 143/143 HTTP/Firestore Emulator secuenciales (nuevas: elegibilidad de registro owner/buyer/admin/pendiente/revocado/suspendido y handler CLOUD con SDK demo). Un primer intento falló 142/143 por una aserción de la prueba nueva que contaba un dispositivo del admin compartido sembrado por otra prueba; se corrigió la aserción, no la lógica.
- Android: assembleDebug y 547/547 JVM en LOCAL y en CONECTADO (APK CONECTADO no instalado ni ejecutado).
- Instrumentación AVD HU34_Local_20261028/API 37, APK LOCAL 127.0.0.1: fixture integral `HU16_CLOSURE ... PASS` con pérdida controlada del 201 (CheckoutOrderHttpEmulatorTest, OrderNotificationEmulatorTest actualizada, StoreOrdersHttpEmulatorTest).
- Recreación real de proceso (`NotificationColdStartEmulatorTest`, solo Auth Emulator alcanzable): sesión → app en segundo plano → `am kill` (PID desaparece, `stopped=false`, no force-stop) → proceso nuevo restaura `currentUser` de forma síncrona, muestra el aviso propio, descarta destinatario ajeno y duplica; tras logout y nuevo kill, sin identidad y sin aviso.

Limitaciones y riesgos observados:
1. **Persistencia de sesión del SDK — MITIGADO** (commit `dc1b1e3`, autorizado por Dante): Firebase Auth persiste `signOut` de forma asíncrona; tras `signOut` inmediato + muerte del proceso, el siguiente restauraba al usuario anterior. `SignOutMarkerStore` guarda con `commit()` el UID antes de `beforeSignOut/signOut`; `ChaskiApplication` completa el cierre al arrancar, antes de consumidores de sesión y FCM; la marca se borra solo después de un inicio de sesión correcto (email, registro, Google, teléfono por código y automático; corrección `a6cc965`: la versión inicial la borraba antes del intento y un login fallido + muerte del proceso podía restaurar al usuario saliente). Un intento fallido conserva la marca; un borrado fallido solo cierra la nueva sesión en el siguiente arranque. Evidencia final sobre `a6cc965`: 551/551 JVM LOCAL y CONECTADO sin omitidas, recreación de proceso 4/4 y fixture integral `HU16_CLOSURE: PASS` repetido; evidencia previa sobre `dc1b1e3`: 550/550 JVM; en AVD, control con `signOut` sin espera: el almacén `[DEFAULT]` del SDK seguía conteniendo al usuario, la marca estaba presente y el proceso recreado arrancó sin identidad ni aviso. Residual: un inicio de sesión no persistido por muerte inmediata deja al usuario desconectado (falla cerrado).
2. FCM data-only no garantiza entrega ni llegada a `onMessageReceived` (app detenida por el usuario, Doze/OEM, cuotas, duplicados). SENT/ACCEPTED no es recepción. La bandeja HTTP sigue siendo la autoridad.
3. Proceso frío sin hechos frescos muestra el aviso genérico al UID persistido aunque una revocación reciente aún no se conozca localmente (aceptado por HU34 §7; el servidor reautorizó antes de enviar).
4. Logout offline: el binding del servidor y el token pueden sobrevivir; Android descarta por UID distinto y un login posterior de responsable transfiere el token con bindingVersion mayor. Un cliente posterior no lo transfiere (no registra), así que el binding obsoleto persiste hasta INVALID_TOKEN o revocación.
5. No hay recepción FCM real acreditada: LOCAL usa transporte simulado y tokens sintéticos.

Pendiente Cloud (requiere autorización explícita; no iniciado):
- Confirmar `FUNCTION_TARGET`/`K_SERVICE` reales del trigger v2, permisos Eventarc/Pub/Sub del service account, ubicación de Firestore compatible con `southamerica-west1`, API FCM v1 (confirmada habilitada por Dante) y credenciales del runtime.
- Comparar reglas/índices publicados; denegar `notification_devices` y `order_events` a clientes.
- Despliegue autorizado del codebase `food-local` y prueba en dispositivo con token real: permiso Android 13+, primer plano, segundo plano, app cerrada por el sistema, Doze, logout/relogin y rotación de token.
- **Procedimiento de replay PROPUESTO, no implementado**: operación administrativa autorizada que reencole eventos RETRY/EXHAUSTED/NO_RECIPIENTS antiguos sin duplicar pedidos ni borrar progreso por dispositivo (reutilizando OrderEventProcessor y su lease); NO_RECIPIENTS no se reenvía automáticamente al activar un dispositivo.
- checkoutApi intacto; su retirada sigue condicionada.

Estado Git: ramaDante, HEAD `a6cc965` (`ac0ddfb`, `b71f270`, `dc1b1e3`, `a6cc965`), árbol rastreado limpio; este documento sigue ignorado y fuera de los commits. Emuladores, AVD y adb reverse detenidos/retirados. Sin push.


### C0 — Inspección Cloud de solo lectura (2026-10-30)

Cuenta propietaria del proyecto (correo omitido); Firebase CLI 15.31.0, MCP oficial (`--only functions`, 27 denegaciones locales) y consola. Sin cambios en Cloud, IAM, reglas, índices ni datos.

| Área | Resultado |
|---|---|
| Functions | `foodApi`: codebase `food-local`, revisión única `foodapi-00001-nak` (≈26-oct), **anterior a B1/B2**, `FUNCTION_TARGET=foodApi`. `checkoutApi`: codebase por defecto, sin uso desde el 23-oct; el equipo indica que no se usa (retirada pendiente de autorización específica). Sin `newOrderNotifier`. |
| Runtime | Ambas con la cuenta de servicio predeterminada de Compute (Editor: suficiente, excesivo). |
| Firestore | `(default)` en `southamerica-west1`; delete protection y PITR desactivados. 0 índices compuestos publicados (faltan los 2 de HU16, validados contra el código). |
| Reglas | Lo publicado es anterior a HU34 y contiene `users/{uid}` y `store_notices`, ausentes del repo. |
| IAM / APIs | 9 APIs necesarias habilitadas; agentes Eventarc/Pub/Sub sin roles (el primer despliegue de un trigger los asignará: cambio IAM implícito). |
| FCM / Eventarc | FCM v1 habilitada, heredada inhabilitada, Sender ID correcto; 0 activadores. |
| Costos | Presupuesto S/ 25/mes con alertas 50/90/100 % (solo alertas, gasto real); gasto ≈ S/ 0. |
| No verificado | Recuentos de datos Firestore (MCP sin agregaciones; COUNT en consola no confirmado). |

### Fusión local de reglas (commit `49d940e`)

Se añadieron literalmente `users/{uid}` y `store_notices` (con sus helpers, sin depender de helpers HU34) al `firestore.rules` HU34, sin retirar restricciones (+56 líneas). `store_notices` procede de `4ce5618`, revertido en el repo; su necesidad queda pendiente de confirmar con Rodrigo. Hallazgo: sin `users/{uid}`, `FirebaseUserProfileStore.ensure` (usado en todos los inicios de sesión) era denegado también en LOCAL; las pruebas previas no lo detectaron porque autenticaban sin el repositorio.

Evidencia: 143/143 HTTP/Firestore Emulator con las reglas fusionadas; `FirestoreRulesClientEmulatorTest` (SDK cliente Android, AVD API 37, Auth+Firestore Emulator) 4/4: perfil real creado/actualizado y casos denegados (perfil ajeno, campo extra, email ajeno, uid/email inmutables, borrado, timestamps de cliente), direcciones solo propietario y sin protocolo, `store_notices` (lectura propia, solo `read=true`, sin borrar, sin creación por no-admin, sin lectura ajena), HU34/HU16 (sin escritura cliente en `business_requests`/`store_managers`, colecciones privadas denegadas). Control: el mismo test de perfil con las reglas previas falla con PERMISSION_DENIED. No se recorrió la UI de login; se probó el repositorio real que esta invoca.

### Compatibilidad entre versiones y ventanas (análisis; orden NO definitivo)

| Combinación | Efecto |
|---|---|
| Reglas nuevas + foodApi antigua (actual) | **Rotura**: registro/reenvío de negocios y gestión de responsables pasan a foodApi, cuyos endpoints `/access/*` están cerrados en la revisión publicada. |
| Reglas nuevas + Android antiguo | **Rotura** de las mismas funciones (escrituras directas denegadas) y de responsables sin vínculo VERIFIED (`managerOf`/`managesBusiness`). |
| Reglas antiguas + foodApi nueva + Android nuevo | Funciona (Admin SDK ignora reglas), pero es una **ventana con permisos anteriores**: clientes antiguos o manipulados pueden escribir `business_requests`/`store_managers` directamente, saltándose el coordinador HU34 y desincronizando `commercial_access_index` (provocaría 409/503 `INDEX_INCONSISTENT`/`RECONCILIATION_REQUIRED`). |
| foodApi nueva sin preparación de datos | Operaciones comerciales en `PREPARATION_REQUIRED` hasta crear el índice comercial de los datos existentes (DEC-HU34-24). |

Propuesta provisional: índices → foodApi B1+B2 y notifier → preparación de datos HU34 → distribución del APK CONECTADO → reglas fusionadas, con la ventana de permisos anteriores lo más corta posible y congelando cambios comerciales manuales durante ella. Requiere confirmar: consumidores de versiones antiguas del APK, volumen de datos a preparar y procedimiento de reconciliación.

Estado Git: ramaDante, HEAD `49d940e`, árbol rastreado limpio; documento ignorado. Sin Cloud, despliegues ni push.


### Fase A completada (LOCAL) y fase B — inspección de datos HU34 (2026-10-30)

Fase A: `61da880` (H1 cliente implícito), `80c7bd6` (H2 sin cuentas solo-teléfono), `00c2572` (SMS ligado al UID solicitante), `c224aa7` (fallo de marca de logout observable), `a155d19` (exclusiones Auto Backup; Room se conserva), A4 dependencias Node propias (sin commit; worktree Codex intacto, verificado contra línea base).

Fase B, 2026-10-30, proyecto `chaski-food-9adba`, cuenta del proyecto, consola Firestore Studio → Compilador de consultas con agregación COUNT, sin límite y sin abrir documentos (solo lectura):

| Colección | Recuento | Detalle |
|---|---|---|
| business_requests | 5 | APPROVED 5; PENDING_REVIEW/OBSERVED/REJECTED/SUSPENDED 0 (suma = total) |
| store_managers | 6 | bindingState PENDING/VERIFIED/REVOKED/RECONCILIATION_REQUIRED 0; `bindingState != null` 0; `boundUid != null` 0 |
| commercial_access_index | 0 | |
| order_events | 0 | |
| food_orders | 0 | |
| notification_devices | 0 | |

Colecciones raíz observadas: audit_logs, business_requests, categories, orders, products, store_appeals, store_managers, store_notices, store_product_configs, stores, users.

Precisiones acordadas:
1. Cinco solicitudes APPROVED no prueban cinco propietarios distintos ni su consistencia (`id == ownerUid` no contable sin leer documentos). La fase E valida las relaciones mínimas antes de PREPARE.
2. Las seis filas de store_managers son heredadas, sin vinculación HU34 comprobada. No se clasifican como corruptas ni como responsables operativos (el código las trataría como LEGACY: sin bandeja; sus locales `NO_RESPONSIBLE`).
3. Antes de pruebas reales, PREPARE y ASSIGN deben dejar al menos un responsable autorizado por local participante; no se autorizan pedidos sin ello.
4. Los recuentos son una fotografía temporal; antes de operaciones posteriores se repiten solo los imprescindibles.
5. checkoutApi, `orders`, `store_notices` y datos históricos se conservan sin modificar.

### Fase C — índices Firestore desplegados (2026-10-30, autorizado por Dante)

Comando: `firebase deploy --only firestore:indexes --project chaski-food-9adba --non-interactive` (CLI 15.31.0 propio del repo, sin `--force`). Resultado: «deployed indexes in firestore.indexes.json successfully for (default) database». El CLI compiló las reglas solo como comprobación (aviso no bloqueante: `businessData` sin uso) y no las desplegó.

Verificación posterior: `firestore:indexes` devuelve 2 índices y 0 fieldOverrides. `food_orders` coincide exactamente; en `delivery_quotes` Firestore muestra `__name__ DESC` implícito al final (normalización equivalente, no es una diferencia funcional). Las reglas publicadas siguen siendo las anteriores a HU34 (sin cambios); Functions sigue con `checkoutApi` y `foodApi`. Dante confirmó en la consola que ambos índices están Habilitados (READY). Fase C cerrada. La reversión ya no es inocua una vez desplegada la foodApi nueva. Delete protection no se activó.

Limitaciones: subcolecciones (p. ej. `order_attempts/{uid}/intents`) no contadas; cuentas/verificación de correo de los responsables heredados desconocidas (las resolverá ASSIGN); sin acceso a contenido de documentos. Consecuencias: replay del outbox no bloquea (0 eventos); fases D y E deben ejecutarse en la misma sesión para acortar la ventana sin responsables válidos.

### Cierre de publicación L1 → F (2026-10-30/31, alternativa A, autorizada por fases por Dante)

Se eligió limpiar los datos comerciales ficticios y recrearlos con los flujos reales, en lugar de reconciliarlos con PREPARE. No se tocaron Auth, `users/**` ni Storage. Inventario y operaciones con scripts de solo lectura o borrado por ruta exacta (scratchpad; sin datos personales en la salida).

| Fase | Resultado verificado |
|---|---|
| I1–I5 | 5 `business_requests` APPROVED (ID = `ownerUid`), 6 `store_managers` sin binding, 0 huérfanos y sin subcolecciones. Claim admin confirmado. Negocio reutilizado: **B1 CevicheVictory** (B2 no tenía cuenta accesible). |
| L1 | 11 documentos borrados uno a uno (6 `store_managers`, 5 `business_requests`), con verificación previa del conjunto. Catálogo intacto. |
| D1–D3 | Despliegue acotado a `functions:food-local:foodApi,…:newOrderNotifier` con `--force` solo por el aviso de `retry`. El primer intento creó `foodApi` pero falló el notifier por la propagación del Eventarc Service Agent; el reintento lo creó. `foodapi-00002-naj` y `newordernotifier-00001-huy` ACTIVE; 1 trigger `document.created` en `order_events/{eventId}`; roles IAM: Pub/Sub SA `iam.serviceAccountTokenCreator`, Compute SA `run.invoker` + `eventarc.eventReceiver`, Eventarc SA `eventarc.serviceAgent`; sin token → 401. `checkoutApi` intacta. |
| E1 | El comprador entra como CLIENT revisión 0. REGISTER → `PENDING_REVIEW` con el mismo UID → APPROVE (admin desde la app) → los 2 locales y el catálogo de B1 reaparecen. |
| E2 | ASSIGN → PENDING; BIND → **VERIFIED** con un correo real verificado (el correo ficticio fue rechazado correctamente con 403 `EMAIL_VERIFICATION_REQUIRED`). Cambiar el correo de la asignación revocó la fila anterior. Dispositivo registrado (`notification_devices` = 1). |
| G | Reglas publicadas `a3351aec…` (25-oct) respaldadas en `.artifacts/g-rules-backup/` (con `firebase.json` de reversión). Diff: 4 cambios funcionales (sin escritura de cliente en `business_requests`/`store_managers`, índice privado, responsable exige binding VERIFIED). Emulador 153/153. `firebase deploy --only firestore:rules` → ruleset `7284b7d1…`, idéntico byte a byte al local. `projects.test` 17/17. Dante validó los 4 roles en el APK. W1 cerrada. |
| F | Comprador en el AVD Pixel_7 y responsable en el teléfono físico. `POST /orders` → 201; `food_orders` = 1 (PENDING_CONFIRMATION, CASH_ON_DELIVERY, PEN); `order_events` = 1 **SENT** al primer intento; bandeja `GET /stores/{id}/orders` → 200; Dante confirmó el aviso y la bandeja. |

Defectos encontrados y corregidos durante E1–E2 (cada uno en commit propio, con pruebas JVM; androidTest compila pero no se ejecutó): `fb21b61` renovación de la lease de acceso antes de caducar; `edafbe7` revalidación silenciosa al navegar; `ef10957` token en caché en lecturas administrativas; `aeb998c` BIND automático de invitaciones PENDING y «Cerrar sesión» en paneles restrictivos; `0685d67` campo `platform` en el registro de dispositivos (antes 400).

Decisiones confirmadas por Dante: **`checkoutApi` se retira** (sin consumidores; `orders` es su legado).

### L2 y retirada de `checkoutApi` (2026-10-31, autorizado por Dante)

Origen del legado: `store_notices` (avisos de suspensión/reactivación de locales), `store_appeals` (apelaciones) y `orders` (pedidos de `checkoutApi`, `backend/checkout`) provienen de `4ce5618` (23-oct), revertido en `39b1a13`. El código vigente (Android `main`, foodApi) no los usa; solo persiste el bloque `store_notices` en `firestore.rules` y su prueba de reglas. Los 5 documentos (3 avisos, 1 apelación, 1 pedido) eran de 22–23-oct y referenciaban únicamente negocios/locales descartados; la auditoría que citaban permanece en `audit_logs`. Se clasificaron como datos ficticios de una funcionalidad descartada.

Ejecución (scripts con ensayo previo, recuentos esperados, referencias cruzadas y subcolecciones verificadas; borrado documento a documento; respaldo JSON local en `.artifacts/l2-backup/`, ignorado):

- Firestore: 7 `stores`, 4 `categories`, 10 `products`, 12 `store_product_configs`, 3 `store_notices`, 1 `store_appeals`, 1 `orders` (38 documentos).
- Storage: 7 objetos en `businesses/<id>/` de los 2 negocios con archivos (B2: 4, B5: 3); las 2 imágenes de B4 eran data URI incrustadas en Firestore. `businesses/` queda vacío (CevicheVictory no tiene imágenes).
- `firebase functions:delete checkoutApi --region southamerica-west1 --project chaski-food-9adba --force` → «Successful delete operation». Sin consumidores: Android y foodApi apuntan a `foodApi`; `backend/checkout` ya no existe en el repo.

Verificación posterior (solo lectura): solo queda el catálogo de CevicheVictory (1 negocio APPROVED, 2 locales, 1 categoría, 2 productos, 4 configuraciones, 0 huérfanos); `users` = 8 sin cambios; responsable VERIFIED, pedido de F (PENDING_CONFIRMATION) y su evento SENT conservados; dispositivo habilitado. Functions: `foodApi` (`foodapi-00002-naj`) y `newOrderNotifier` ACTIVE, 1 trigger, roles IAM presentes, secreto ENABLED, sin token → 401. Reglas publicadas idénticas al local (`7284b7d1…`); 2 índices. Se conservan `audit_logs` (57), `delivery_quotes` (2) y `commercial_access_index` (6). No se tocó Authentication.

Mejoras para próximas historias (no implementadas): retirar un responsable desde la UI (REVOKE ya existe en foodApi); reenviar el correo de verificación desde la app; quitar del menú del cliente «Productos por local», «Operación de locales» y «Menú y catálogo»; mensaje explícito en checkout cuando la cuenta no tiene nombre (hoy `CheckoutBuyerSnapshotSource` lanza un error genérico); aviso más insistente o recordatorio al responsable (FCM `ttl` 5 min; `NO_RECIPIENTS` es terminal y la bandeja es la fuente de verdad), a definir en HU19 o en una HU de notificaciones.
