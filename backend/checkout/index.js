const { onRequest } = require('firebase-functions/v2/https');
const { initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');
const { FEE_MINOR, minorUnits, distanceKm, openNow, validRequest, canReadStoreOrders } = require('./checkout-core');

initializeApp();
const db = getFirestore();

class CheckoutError extends Error {
  constructor(status, code, message, details = []) {
    super(message);
    Object.assign(this, { status, code, details });
  }
}

async function validateOrder(tx, uid, input) {
  const storeRef = db.collection('stores').doc(input.storeId);
  const addressRef = db.collection('users').doc(uid).collection('addresses').doc(input.addressId);
  const [storeDoc, addressDoc] = await Promise.all([tx.get(storeRef), tx.get(addressRef)]);
  if (!storeDoc.exists || !addressDoc.exists) throw new CheckoutError(409, 'UNAVAILABLE', 'El local o la dirección ya no están disponibles.');
  const store = storeDoc.data();
  const address = addressDoc.data();
  const businessDoc = await tx.get(db.collection('business_requests').doc(store.businessId || 'missing'));
  const business = businessDoc.data();
  if (business?.status !== 'APPROVED' || !business.ownerUid || store.status !== 'ACTIVE' ||
      store.operationalStatus !== 'OPEN' || !openNow(store.operatingHours)) {
    throw new CheckoutError(409, 'STORE_CLOSED', 'El local no está disponible en este momento.');
  }
  const point = address.location;
  if (!point || !Number.isFinite(point.lat) || !Number.isFinite(point.lng) ||
      !Number.isFinite(store.latitude) || !Number.isFinite(store.longitude) ||
      !Number.isFinite(store.deliveryRadiusKm ?? 5) || (store.deliveryRadiusKm ?? 5) <= 0 ||
      distanceKm(point, { lat: store.latitude, lng: store.longitude }) > (store.deliveryRadiusKm ?? 5)) {
    throw new CheckoutError(409, 'OUTSIDE_COVERAGE', 'La dirección queda fuera de la cobertura del local.');
  }

  const lines = await Promise.all(input.items.map(async item => {
    const [productDoc, configDoc] = await Promise.all([
      tx.get(db.collection('products').doc(item.productId)),
      tx.get(db.collection('store_product_configs').doc(`${input.storeId}_${item.productId}`)),
    ]);
    const product = productDoc.data();
    const config = configDoc.data();
    if (!product || !product.name || product.businessId !== store.businessId || !product.isActive ||
        !config || config.businessId !== store.businessId || config.storeId !== input.storeId ||
        config.productId !== item.productId || config.availability !== 'AVAILABLE') {
      return { productId: item.productId, issue: 'AGOTADO_O_NO_DISPONIBLE' };
    }
    const categoryDoc = await tx.get(db.collection('categories').doc(product.categoryId || 'missing'));
    const category = categoryDoc.data();
    if (!category || !category.isActive || category.businessId !== store.businessId) {
      return { productId: item.productId, issue: 'AGOTADO_O_NO_DISPONIBLE' };
    }
    const unitPriceMinor = minorUnits(config.price);
    if (unitPriceMinor === null) return { productId: item.productId, issue: 'PRECIO_INVALIDO' };
    return {
      productId: item.productId, name: product.name, quantity: item.quantity,
      unitPriceMinor, lineTotalMinor: unitPriceMinor * item.quantity,
      priceChanged: unitPriceMinor !== item.expectedPriceMinor,
    };
  }));
  const issues = lines.filter(line => line.issue);
  if (issues.length) throw new CheckoutError(409, 'PRODUCT_CHANGED', 'Algunos productos ya no están disponibles.', issues);
  const subtotalMinor = lines.reduce((sum, line) => sum + line.lineTotalMinor, 0);
  if (!Number.isSafeInteger(subtotalMinor) || !Number.isSafeInteger(subtotalMinor + FEE_MINOR)) {
    throw new CheckoutError(400, 'AMOUNT_OVERFLOW', 'El importe es demasiado alto.');
  }
  return {
    storeId: input.storeId, storeName: store.name, businessId: store.businessId,
    address: { addressText: address.addressText, label: address.label || '',
      reference: address.reference || '', instructions: address.instructions || '',
      lat: point.lat, lng: point.lng },
    items: lines, subtotalMinor, deliveryFeeMinor: FEE_MINOR,
    totalMinor: subtotalMinor + FEE_MINOR, paymentMethod: 'CASH_ON_DELIVERY',
    priceChanged: lines.some(line => line.priceChanged),
  };
}

exports.checkoutApi = onRequest({ region: 'southamerica-west1', cors: false, maxInstances: 10 }, async (req, res) => {
  try {
    const token = /^Bearer (.+)$/.exec(req.get('Authorization') || '')?.[1];
    if (!token) throw new CheckoutError(401, 'AUTH', 'Inicia sesión para continuar.');
    let user;
    try {
      user = await getAuth().verifyIdToken(token, true);
    } catch {
      throw new CheckoutError(401, 'AUTH', 'Tu sesión expiró. Inicia sesión nuevamente.');
    }
    const { uid } = user;
    const action = req.path.replace(/^\/+|\/+$/g, '');
    if (req.method === 'GET' && action === 'store-orders') {
      const storeId = req.query.storeId;
      if (typeof storeId !== 'string' || !/^[A-Za-z0-9_-]{1,150}$/.test(storeId)) {
        throw new CheckoutError(400, 'INVALID_REQUEST', 'Local inválido.');
      }
      const storeDoc = await db.collection('stores').doc(storeId).get();
      if (!storeDoc.exists) throw new CheckoutError(404, 'NOT_FOUND', 'Local no encontrado.');
      const businessId = storeDoc.get('businessId');
      if (typeof businessId !== 'string' || !businessId) {
        throw new CheckoutError(403, 'FORBIDDEN', 'Local no autorizado.');
      }
      const businessDoc = await db.collection('business_requests').doc(businessId).get();
      if (!businessDoc.exists) throw new CheckoutError(403, 'FORBIDDEN', 'Local no autorizado.');
      let assignmentData = null;
      if (businessDoc.get('ownerUid') !== uid && user.email_verified && typeof user.email === 'string') {
        const email = user.email.trim().toLowerCase();
        const assignment = await db.collection('store_managers').doc(`${businessId}_${email}`).get();
        assignmentData = assignment.data();
      }
      if (!canReadStoreOrders(user, businessDoc.data(), assignmentData, businessId, storeId)) {
        throw new CheckoutError(403, 'FORBIDDEN', 'No puedes consultar los pedidos de este local.');
      }
      const snapshot = await db.collection('orders').where('storeId', '==', storeId).get();
      const orders = snapshot.docs.map(doc => ({
        orderId: doc.id, status: doc.get('status'),
        createdAtMillis: doc.get('createdAt')?.toMillis() || 0,
        items: doc.get('items') || [], address: doc.get('addressSnapshot') || null,
        subtotalMinor: doc.get('subtotalMinor'), deliveryFeeMinor: doc.get('deliveryFeeMinor'),
        totalMinor: doc.get('totalMinor'), paymentMethod: doc.get('paymentMethod'),
      })).sort((a, b) => b.createdAtMillis - a.createdAtMillis).slice(0, 100);
      return res.json({ orders });
    }
    if (req.method !== 'POST') throw new CheckoutError(405, 'METHOD', 'Método no permitido.');
    const input = req.body;
    if (!validRequest(input)) throw new CheckoutError(400, 'INVALID_REQUEST', 'Revisa los datos de la compra.');
    if (action === 'quote') {
      const quote = await db.runTransaction(tx => validateOrder(tx, uid, input));
      return res.json(quote);
    }
    if (action !== 'orders') throw new CheckoutError(404, 'NOT_FOUND', 'Ruta desconocida.');
    if (!/^[0-9a-f-]{36}$/.test(input.requestId || '')) {
      throw new CheckoutError(400, 'INVALID_REQUEST', 'Identificador del pedido inválido.');
    }
    const orderRef = db.collection('orders').doc(input.requestId);
    const result = await db.runTransaction(async tx => {
      const existing = await tx.get(orderRef);
      if (existing.exists) {
        if (existing.get('customerUid') !== uid) throw new CheckoutError(403, 'FORBIDDEN', 'Pedido no autorizado.');
        return { orderId: orderRef.id, status: existing.get('status') };
      }
      const quote = await validateOrder(tx, uid, input);
      if (quote.priceChanged) throw new CheckoutError(409, 'PRICE_CHANGED', 'Los precios cambiaron. Revisa el nuevo total.');
      const timestamp = FieldValue.serverTimestamp();
      tx.create(orderRef, {
        customerUid: uid, businessId: quote.businessId, storeId: quote.storeId,
        storeName: quote.storeName, status: 'PENDING_CONFIRMATION',
        addressSnapshot: quote.address, items: quote.items.map(({ priceChanged, ...line }) => line),
        subtotalMinor: quote.subtotalMinor, deliveryFeeMinor: quote.deliveryFeeMinor,
        totalMinor: quote.totalMinor, currency: 'PEN', paymentMethod: quote.paymentMethod,
        createdAt: timestamp, updatedAt: timestamp,
      });
      return { orderId: orderRef.id, status: 'PENDING_CONFIRMATION' };
    });
    return res.status(201).json(result);
  } catch (error) {
    if (!(error instanceof CheckoutError)) console.error('checkoutApi failed', error);
    return res.status(error.status || 500).json({
      code: error.code || 'SERVER_ERROR', message: error.status ? error.message : 'No se pudo completar la compra.',
      details: error.details || [],
    });
  }
});
