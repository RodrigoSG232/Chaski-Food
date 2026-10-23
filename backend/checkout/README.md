# Checkout y pedidos (HU15–HU16)

La app envía el carrito y el ID de la dirección a `checkoutApi` con el token de Firebase Authentication. La función consulta Firestore y valida negocio, local, horario de Lima, cobertura, productos, disponibilidad y precios. `POST /quote` devuelve subtotal, reparto y total en céntimos de PEN. `POST /orders` vuelve a validar dentro de una transacción y crea `orders/{requestId}` en estado `PENDING_CONFIRMATION`, con productos y dirección congelados. `GET /store-orders?storeId=...` muestra los pedidos del local al dueño o a un responsable asignado con correo verificado. Los pedidos se consultan desde su propio panel; no se crean avisos de pedidos en `store_notices`.

Por ahora la modalidad admitida es efectivo contra entrega y la tarifa de reparto es fija: S/ 5.00 (`FEE_MINOR` en `checkout-core.js`). Ni el total ni los precios enviados por Android se aceptan como fuente de verdad. `requestId` evita duplicar un pedido cuando se repite la misma solicitud.

Desde la raíz del repositorio:

```powershell
npm.cmd ci --prefix backend/checkout
npm.cmd test --prefix backend/checkout
firebase.cmd deploy --only functions:checkoutApi --project chaski-food-9adba --config firebase.checkout.json --force
```

El archivo `firebase.checkout.json` se versiona en la raíz porque la carpeta `firebase/` está excluida por `.gitignore`. El despliegue necesita que el proyecto Firebase tenga plan Blaze y permisos para Cloud Functions.
