# Plan de pruebas conjuntas E6 (Food ↔ Rider)

> Se ejecuta **solo en E6**, con Food y Rider conectados, después del GO de [PREPARACION_E6 §10](PREPARACION_E6.md). **No se ha ejecutado.**

## Prerrequisitos
- GO completo.
- Piloto `pilot:<storeId>` con un único local.
- APK nuevo en todos los dispositivos.
- Rider en su versión compatible.

## Roles
| Rol | Quién | Herramienta |
|---|---|---|
| Comprador | Cuenta de prueba | APK Food |
| Responsable del local | Cuenta asignada y verificada | APK Food |
| Administrador de Food | Cuenta con claim `admin` y correo verificado | APK Food |
| Repartidor | Cuenta de prueba de Rider | App Rider |
| Observador | El responsable del proyecto | Consola de Firebase en **solo lectura** |

## Datos
- **Solo datos ficticios:** productos de prueba del local del piloto, dirección ficticia y montos pequeños.
- Nada de teléfonos, documentos ni cuentas reales en la evidencia.

## Evidencia por caso
- Capturas de las dos apps sin datos personales.
- Id corto del pedido y hora.
- Estado observado en Firestore (`status`, `paymentStatus`, `collectionReview.status`) anotado a mano.
- Resultado: OK, FALLO o BLOQUEADO.

## Casos

| # | Caso | Pasos | Resultado esperado |
|---|---|---|---|
| 1 | Cobro exacto | Pedido → aceptar → listo → Rider lo ve y lo asigna → PIN correcto → recojo → entrega declarando el total | `COMPLETED`, `PAID`; comprador: «Pago registrado contra entrega»; panel +subtotal |
| 2 | Discrepancia | Como el 1, declarando un importe distinto | Pago `PENDING`; revisión `OPEN`; comprador: «Pago en revisión»; sin venta; Rider ve `DISCREPANCY` |
| 3 | Conciliación completa | Desde el 2 (declarado menor), el administrador confirma el total | `PAID`; Rider ve `RESOLVED/FULLY_COLLECTED`; una venta |
| 4 | Conciliación incompleta | Desde el 2, el administrador confirma un importe menor | Sigue `PENDING`; comprador: «Pago incompleto · saldo pendiente»; sin venta |
| 5 | Excedente | Se declara más que el total y el administrador confirma | `PAID`; «Excedente de S/X pendiente de devolución»; el excedente no suma ventas |
| 6 | Registro de devolución | Desde el 5, el administrador registra la devolución | «Excedente de S/X devuelto»; Rider ve `REFUNDED` |
| 7 | PIN correcto | El local introduce el PIN que ve el repartidor | `VERIFIED`; Rider puede marcar el recojo |
| 8 | Cinco PIN incorrectos | El local falla 5 veces | Bloqueado; Rider recibe `PICKUP_LOCKED`; el pedido sale del listado |
| 9 | Desbloqueo | El administrador desbloquea con un motivo | PIN nuevo solo en Rider; ciclo 2 |
| 10 | Liberación y reasignación | Rider libera y otro repartidor acepta (versión 2) | La verificación anterior se invalida; hay que volver a verificar |
| 11 | Entrega fallida | Rider cierra sin éxito y sin dinero | «Entrega no exitosa»; sin cobro. Con dinero: 422 y procedimiento manual |
| 12 | Red interrumpida | Cortar los datos al confirmar la conciliación o la verificación y reintentar | Se reintenta con la misma operación; sin duplicados |
| 13 | Idempotencia | Rider reenvía el mismo evento final | Mismo resultado; un solo cobro |
| 14 | Reducción del piloto | Con un pedido en reparto, volver a DISABLED | La entrega en curso termina y se concilia; no aparecen ofertas nuevas |

## Criterios de aceptación
- Casos 1–14 en OK, o con fallos documentados y aceptados por el responsable.
- Ningún PIN visible fuera del repartidor asignado.
- Ningún `PAID` por un importe incompleto.
- Las ventas coinciden con la suma de subtotales de los pedidos `PAID`.

## Criterios de suspensión (pausa operativa inmediata con DISABLED; si hay un riesgo de seguridad, B1 de PREPARACION_E6 §7)
- PIN expuesto.
- Evento aceptado de un llamador inesperado.
- `PAID` incorrecto.
- Duplicado de cobro.
- Pérdida de datos de un pedido.
- Errores 503 persistentes.
- Rider fuera del contrato.
