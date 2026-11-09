# HU28 — Dashboard comercial

## Consulta

`GET /api/v1/commercial/dashboard?businessId={id}&days=7|30|90[&storeId={id}]` con Firebase ID token. `foodApi` vuelve a verificar la identidad y el índice comercial HU34. El propietario de un negocio aprobado o suspendido puede leer solo ese negocio. Un responsable con asignación VERIFIED puede leer los locales autorizados de ese negocio. El claim de administrador no agrega acceso.

Respuesta `contractVersion: 1`: `businessId`, `days`, `storeId`, `generatedAtEpochMillis`, `timeZone: America/Lima`, `currency: PEN`, `stores` autorizados, `orders`, `salesMinor`, `accepted`, `rejected`, `acceptanceRate` (nulo sin decisiones) y `series` diaria (`date`, `orders`, `salesMinor`). No incluye detalles, direcciones ni datos de compradores.

## Definiciones

- **Pedidos:** creados entre el inicio del primer día de Lima y el instante de consulta, en los últimos 7/30/90 días calendario.
- **Ventas de productos:** suma de `subtotalMinor` de pedidos con `COMPLETED` y resultado Rider `SUCCESS`, por fecha de `completedAt`. Excluye reparto y propina. El pago actual es simulado.
- **Aceptación:** aceptados / (aceptados + rechazados), considerando pedidos creados en la ventana. Los pendientes y cancelados no integran el denominador.
- **Local:** sin filtro, propietario ve todos sus locales y responsable solo los asignados. `storeId` ajeno se rechaza con 403.

Las dos consultas se ejecutan con un máximo de 3000 documentos cada una. Si se supera, `DASHBOARD_LIMIT_EXCEEDED` (503) evita cifras parciales. Datos inconsistentes también producen error y no un subtotal incompleto.

## Estado de entrega

Código Android y Functions probado en LOCAL. Antes de probar CONECTADO se deben publicar `foodApi` y los índices nuevos de `firestore.indexes.json` con autorización del equipo. No se han desplegado estos cambios. No requiere una API nueva de Chaski Rider: utiliza el evento de entrega exitosa y `completedAt` que Food ya registra. Sin entregas exitosas registradas, ventas se muestra en cero.
