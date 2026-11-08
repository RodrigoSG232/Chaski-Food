# API Chaski Food → Chaski Rider — v1

Estado: implementada en `ramaMax`, pendiente de despliegue y de autorizar la cuenta de servicio del backend Rider.

## Seguridad

`riderApi` es una Cloud Function v2 privada. Solo acepta invocaciones autorizadas por IAM. La app Android Rider no debe llamar directamente a esta API ni incorporar cuentas de servicio: su backend obtiene un token OIDC para la URL de `riderApi` y realiza la llamada servidor a servidor.

Encabezados:

```http
Authorization: Bearer <token OIDC de la cuenta de servicio Rider>
Content-Type: application/json
```

Food no crea ni administra repartidores. Rider conserva la autoridad sobre su selección, asignación y liberación.

## Consultar pedidos disponibles

```http
GET /integration/v1/orders?limit=20
```

Devuelve como máximo 50 pedidos `READY_FOR_PICKUP`, pagados mediante la simulación educativa y sin asignación vigente. Cada pedido contiene:

- cliente: identificador, nombre, teléfono y destino;
- local: identificador, nombre, teléfono, dirección y coordenadas;
- productos: identificador, nombre y cantidad, sin precios individuales;
- importes: subtotal, reparto, propina y total en céntimos de sol;
- pago: `PREPAID_SIMULATION / SIMULATED / PAID`;
- `pickupCode`: único código de cuatro dígitos generado al simular el pago;
- asignación, estado logístico y última ubicación cuando existan.

```json
{
  "contractVersion": 1,
  "orders": [{
    "schemaVersion": 1,
    "orderId": "<64 caracteres hexadecimales>",
    "status": "READY_FOR_PICKUP",
    "customer": {
      "id": "uid-cliente",
      "displayName": "Cliente",
      "phone": "+51999999999",
      "address": "Dirección seleccionada",
      "reference": "Referencia",
      "instructions": "Indicaciones",
      "latitude": -12.1,
      "longitude": -77.1
    },
    "merchant": {
      "id": "store-id",
      "name": "Local",
      "phone": "+51999999998",
      "address": "Dirección del local",
      "instructions": null,
      "latitude": -12.0,
      "longitude": -77.0
    },
    "items": [{"id": "product-id", "name": "Producto", "quantity": 2}],
    "amounts": {
      "subtotalMinor": 2500,
      "deliveryFeeMinor": 450,
      "tipMinor": 200,
      "totalMinor": 3150,
      "currency": "PEN"
    },
    "payment": {"flow": "PREPAID_SIMULATION", "method": "SIMULATED", "status": "PAID"},
    "pickupCode": "0427"
  }]
}
```

Para reconciliar un pedido conocido:

```http
GET /integration/v1/orders/{orderId}
```

Está disponible mientras el pedido se encuentre `READY_FOR_PICKUP`, `IN_DELIVERY` o `COMPLETED`.

## Informar eventos Rider

```http
POST /integration/v1/rider-events
```

Eventos admitidos, en orden creciente mediante `eventSequence`:

| `eventType` | `status` | Efecto en Food |
|---|---|---|
| `ASSIGNMENT_ACCEPTED` | `ACCEPTED` | Registra nueva asignación y repartidor. |
| `ARRIVED_AT_MERCHANT` | `AT_MERCHANT` | Registra llegada al local. |
| `ASSIGNMENT_RELEASED` | `SEARCHING_RIDER` | Libera antes del recojo; conserva pedido y código. |
| `PICKED_UP` | `ON_THE_WAY` | Cambia el pedido a `IN_DELIVERY`. |
| `ARRIVED_AT_CUSTOMER` | `AT_CUSTOMER` | Registra llegada al cliente. |
| `COMPLETED_SUCCESSFULLY` | `COMPLETED` | Cambia a `COMPLETED`, resultado `SUCCESS`. |
| `COMPLETED_UNSUCCESSFULLY` | `COMPLETED` | Cambia a `COMPLETED`, resultado `UNSUCCESSFUL`. |

Todos los campos del contrato son obligatorios; los que no correspondan se envían como `null`. Ejemplo de asignación:

```json
{
  "schemaVersion": 1,
  "eventId": "EV_RIDER_1001_1",
  "eventType": "ASSIGNMENT_ACCEPTED",
  "eventSequence": 1,
  "occurredAt": "2026-11-08T15:00:00.000Z",
  "orderId": "<orderId>",
  "assignment": {"id": "ASG_1001_1", "version": 1},
  "status": "ACCEPTED",
  "reason": null,
  "rider": {
    "id": "RIDER_88",
    "displayName": "Repartidor",
    "phone": "+51999999997",
    "vehicleType": "MOTORCYCLE",
    "vehiclePlate": "ABC-123"
  },
  "deliveryOutcome": null
}
```

Los siguientes eventos envían `rider: null`. Un evento repetido con el mismo `eventId` y contenido es idempotente; reutilizar el ID con otro contenido devuelve `409`. Una asignación nueva incrementa `assignment.version`. Una liberación no reinicia `eventSequence` ni cambia el código.

## Actualizar tracking

```http
PUT /integration/v1/orders/{orderId}/tracking
```

Solo acepta la asignación vigente cuando el pedido está `IN_DELIVERY`:

```json
{
  "assignmentId": "ASG_1001_1",
  "assignmentVersion": 1,
  "sequence": 19,
  "riderId": "RIDER_88",
  "latitude": -12.123,
  "longitude": -77.031,
  "accuracyMeters": 15,
  "recordedAt": "2026-11-08T15:20:00.000Z"
}
```

Una posición anterior no reemplaza la vigente. El seguimiento visible al cliente pertenece a HU23.

## Publicación

```bash
pnpm --dir functions exec firebase deploy \
  --only "functions:food-local:riderApi" \
  --project chaski-food-9adba \
  --non-interactive
```

Después del despliegue se debe otorgar `roles/run.invoker` exclusivamente a la cuenta de servicio que entregue el equipo Rider. Hasta recibir ese correo, la función debe permanecer privada.

