# Estado de implementación de Chaski Food

Última revisión: **2026-10-30 (America/Lima)**. Rama de referencia: `ramaDante`. Commit: `0685d67` (publicado en `origin/ramaDante`; `main` no incluye todavía HU15–HU16/HU34).

Esta revisión registra la publicación en `chaski-food-9adba` y su validación con el APK CONECTADO (detalle por fases en `HU16_DECISIONES_Y_REQUISITOS.md`, «Cierre de publicación L1 → F»). Los resultados de revisiones anteriores se identifican como históricos. Arquitectura y contratos: [ARCHITECTURE](ARCHITECTURE.md). Requisitos, prioridades y decisiones: [PLAN_PROYECTO](PLAN_PROYECTO.md).

## 1. Estado por funcionalidad

«Implementado» indica código conectado; «verificado» requiere evidencia de ejecución; «aceptado» requiere comprobación de criterios. No se declara una HU completa únicamente por una pantalla, commit o clase de pruebas.

| Área / historias | Código integrado y evidencia disponible | Pendiente o límite |
|---|---|---|
| Base / EN01–EN02 | Feature-first, MVVM, Hilt, Compose/Material 3, corrutinas/Flow y tema compartido. | No implica uniformidad perfecta de capas/tema ni cierre de todos los criterios académicos. |
| Identidad / HU01–HU03 | Correo, Google, recuperación, teléfono y perfil; main incorpora FirebaseUserProfileStore y ajustes de registro/perfil/Splash. | Comprobar configuración de proveedores, permisos del documento users/{uid} y recorrido real de los cambios incorporados desde main. No certificar aquí todos sus CA. |
| Direcciones / HU04 | CRUD propio, predeterminada Firestore, selección DataStore por UID, mapa, permisos/ubicación y geocodificación. | Snapshot histórico todavía depende de HU16/HU17. La aceptación de HU14 no cierra por sí sola todos los CA de HU04. |
| Negocios/locales / HU05–HU09 | Solicitudes, evaluación, responsables, horarios, operación y supervisión con auditoría. HU07 incluye suspensión general y advertencias posteriores. | Mantiene límite de ocho locales activos. Validación remota actual, casos históricos y adaptador Kotlin contra Emulator no quedan acreditados por esta inspección. |
| Catálogo / HU10–HU11 | Categorías/productos, imágenes y configuración de oferta por local; Storage/Coil integrados. | Reglas y recursos remotos deben comprobarse en el entorno de prueba correspondiente. |
| Descubrimiento / HU12–HU13 | Locales por dirección/horario/cobertura, búsqueda y detalle de oferta conectados. | Coexisten componentes de demostración; presentación de ofertas no reserva precio ni disponibilidad. |
| Carrito / HU14 | **Implementado, probado, integrado y aceptado manualmente por Dante.** Domain, validación remota, Room por UID, concurrencia/sesión, Compose, detalle/navegación y pulido UX. | No incluye checkout, pedido, pagos ni sincronización del carrito entre dispositivos; límites específicos en §3. |
| Checkout / HU15 | Integrado en ramaDante: revisión de compra, elegibilidad y cotización de reparto vía `foodApi` (merge `1e9d45b`). Validado en Cloud dentro del recorrido F. | La cuenta sin nombre en Auth produce un error genérico en «Revisar compra» (mejora pendiente). |
| Confirmación / HU16 | **Publicado y validado en Cloud (31-oct):** pedido transaccional e idempotente, evento outbox, `newOrderNotifier` con FCM y bandeja del responsable. F: `POST /orders` 201, evento SENT, bandeja 200, aviso recibido. | Aceptar/rechazar pedidos es HU19 (el pedido queda `PENDING_CONFIRMATION`). Aviso: `ttl` FCM 5 min; sin dispositivo el evento termina `NO_RECIPIENTS`. |
| Accesos comerciales / HU34 | **Publicado y validado en Cloud:** coordinador en `foodApi` (REGISTER/APPROVE/ASSIGN/BIND/REVOKE), índice privado, reglas fusionadas (ruleset `7284b7d1…`), cliente implícito, BIND automático y paneles restrictivos con cierre de sesión. | Sin UI para retirar responsable ni reenviar verificación de correo. El APK antiguo pierde funciones comerciales con las reglas nuevas. |
| Pedidos / HU17 | La pestaña Pedidos presenta estado vacío; no hay historial productivo. | Persistencia/consulta por UID y detalle histórico dependientes de HU16. |
| HU18–HU33 y EN04 | Existen algunas pantallas de ejemplo e infraestructura reutilizable. | No hay evidencia en esta revisión para declararlas implementadas/aceptadas. Room en Cart no cierra offline/sincronización; seguimiento de muestra no demuestra Rider. |

## 2. Actualizaciones oficiales ya incorporadas

HU14 llega hasta el pulido `deeecbf` y está incluida en main. También están integradas las mejoras de imágenes y presentación de locales/ofertas anteriores.

El cambio oficial `eeedfb4` incorpora persistencia del perfil en Firestore, ajustes de registro, cuenta/Splash y flujos de usuario/negocio; además excluye documentos locales de Git. El commit actual revierte el cambio `4ce5618` («Implementar checkout, apelaciones y mejoras de interfaz»): su presencia en el historial no significa que esas funcionalidades estén vigentes. Se documenta el árbol actual, no la suma de mensajes de commits.

La actualización por fast-forward conservó HU14 y no integró el commit exclusivo de Rodrigo. Esta consolidación solo cambia documentación local; no modifica esa situación.

## 3. Límites y comprobaciones pendientes

- **HU14:** importes provisionales, sin reserva de precio/stock; agregar, aumentar y confirmar cambio requieren validación remota. Reducción/eliminación y lectura usan Room. El token de confirmación vive en memoria; el carrito confirmado persiste por UID. No se implementó incorporación rápida desde tarjetas ni checkout.
- **Integración de datos:** las pruebas UI aisladas no ejecutan necesariamente Firebase y Room reales de extremo a extremo. El lector Android real de ofertas contra Firebase Emulator y la ejecución conjunta instrumentada pendiente de 3B no se consideran aprobados por los resultados posteriores de Compose.
- **HU07:** ocho locales activos como máximo por suspensión general; reactivar negocio no reactiva locales. Distinguir transacción rechazada, confirmada y confirmada con advertencia de verificación. Quedan por acreditar adaptador Kotlin real contra Emulator y negocios históricos suspendidos con locales ACTIVE/versiones administrativas antiguas.
- **Perfiles y reglas:** resuelto en `49d940e` (perfil `users/{uid}` y `store_notices` conservados en las reglas HU34) y publicado el 31-oct; perfil y direcciones validados con el APK CONECTADO.
- **Cloud (31-oct):** Functions `foodApi` (`foodapi-00002-naj`) y `newOrderNotifier`; **`checkoutApi` eliminada**. Datos comerciales: solo CevicheVictory (2 locales, 1 categoría, 2 productos, 4 configuraciones); limpieza L2 ejecutada (38 documentos y 7 imágenes de negocios descartados, incluido el legado `orders`/`store_notices`/`store_appeals` de `4ce5618`). Respaldos locales: reglas anteriores en `.artifacts/g-rules-backup/` y documentos borrados en `.artifacts/l2-backup/` (ignorados por Git). El bloque `store_notices` sigue en `firestore.rules` sin consumidores; retirarlo requiere un cambio de reglas aparte.
- **Mejoras anotadas para próximas historias:** retirar responsable desde la UI; reenviar verificación de correo; quitar opciones comerciales del menú del cliente; mensaje claro en checkout sin nombre; aviso más insistente al responsable (HU19 o HU de notificaciones).
- **Direcciones/pedidos:** la copia histórica por valor está acordada, pero no implementada como parte de un pedido real. No confundir editar la dirección actual con alterar pedidos pasados.
- **Entorno:** Auth, Firestore, Storage, mapa y geocodificación dependen de configuración/disponibilidad externa. No se copiaron secretos ni se comprobó producción durante esta consolidación.

## 4. Evidencia relevante conservada

| Alcance y referencia | Resultado previamente reportado | Límite de la evidencia |
|---|---|---|
| HU14, cierre UX del 2026-10-12 incorporado en `deeecbf` | assembleDebug y compilación androidTest satisfactorios; 287 pruebas JVM, 38 suites, sin fallos/omisiones. Pixel_7: CartScreenTest 10, OrdersScreenTest 1 y MainTabNavigationTest 3 correctas. | Resultado histórico de esa entrega; no es una ejecución sobre el HEAD actual. |
| Integración HU14 5B, `6bf75ec` | ProductDetailPurchaseTest 4 y OfferScreensTest 3 correctas; navegación/carrito también comprobados entonces. | UI con su entorno de pruebas; no acredita despliegue ni backend completo. |
| Room HU14 fase 2, `7d7be0d` | Se reportaron 5 pruebas DAO y 2 de migración instrumentadas correctas. | Validan persistencia local/migración bajo esos casos; no todas las instalaciones existentes. |
| Coordinación HU14 3A, `697e51d` | Se reportaron 9 pruebas instrumentadas del coordinador, incluyendo 50 incorporaciones simultáneas que terminaron en cantidad 51 desde una inicial. | No equivale a certificación de todos los escenarios multiproceso o remotos. |
| HU07, corrección incorporada en `9abdaf8` | Se reportaron assembleDebug, 259 JVM y 14 casos de Firebase Emulator satisfactorios en la preparación. | Los casos Emulator validaban reglas/protocolo; no ejecutaban el adaptador Kotlin productivo. |
| Aceptación HU14 | Dante confirmó manualmente funcionalidad, persistencia Room y últimos ajustes visuales antes de autorizar su push. | Aceptación del alcance HU14; no de HU15–HU17 ni de todos los cambios posteriores de main. |

Las fuentes de estos resultados son los informes previos y la documentación original preservada; no se regeneraron reportes. Las clases de prueba existentes permiten preparar nuevas ejecuciones, pero su mera existencia no prueba éxito. Los diagnósticos antiguos de septiembre y sus cifras no se presentan como el resultado vigente; para una regresión nueva se debe ejecutar la comprobación pertinente sobre el commit actual.

## 5. Deudas documentales y laboratorio HU04

`HU04_PLAN.md` permanece **temporalmente** como antecedente técnico. Sus contratos vigentes fueron consolidados en arquitectura y su dependencia con pedidos está en el plan. Sus fechas, comandos y resultados históricos no describen automáticamente el estado actual.

La prueba de `../.artifacts/hu04-firestore-emulator/rules.test.mjs`, en el caso «La copia de reglas coincide exactamente con el borrador documentado», lee `../../docs/HU04_PLAN.md` y busca el bloque literal de `firestore.draft.rules`. Forma parte de `npm test` del laboratorio.

**Deuda concreta:** desacoplar esa comprobación del documento o acordar el archivo reproducible del laboratorio. Hasta entonces no retirar HU04 ni alterar su bloque de reglas. Esta tarea no modifica pruebas, configuración ni archivos del laboratorio, y no ejecuta el emulador. No usar el borrador histórico como reglas publicables.

## 6. Documentación local, archivo y colaboración

Los documentos generales activos son [ARCHITECTURE](ARCHITECTURE.md), [PLAN_PROYECTO](PLAN_PROYECTO.md) y este estado; [AGENTS](../AGENTS.md) conserva las instrucciones de trabajo. Los originales CSV/PDF continúan intactos en contexto y se enlazan desde el plan.

Archivo documental externo de esta consolidación:
`<carpeta local DSM_PROYECTO>/Archivo_documental_Chaski-Food/2026-10-25_consolidacion_1b1deaf5/`.

- `historicos/`: Contexto y Traspaso previos.
- `academico/`: tres manuales DOCX y un PDF verificado por manual, sin cambios de contenido.
- `manifest.json`: procedencia y SHA-256 de las ocho copias.
- El respaldo permanente previo permanece intacto en `<carpeta local DSM_PROYECTO>/Respaldos_Chaski-Food/pre-main_2026-10-25_deeecbf_23eea34c/`; incluye también las exportaciones redundantes.

`docs/`, `contexto/` y `.artifacts/` están ignorados; AGENTS y algunos documentos también tienen exclusiones locales. Un Git limpio no demuestra ausencia de cambios documentales ni los comparte con el equipo. No se modificaron exclusiones ni se agregó nada al índice.

**Pendiente de acuerdo:** versionar los tres documentos esenciales ya depurados o mantener una ubicación compartida única. Mientras sigan locales, coordinar su distribución explícitamente. No forzar su inclusión ni publicar fuentes privadas. Actualizar esta fotografía al cambiar el código o disponer de nueva evidencia, sin añadir informes completos de cada fase.
