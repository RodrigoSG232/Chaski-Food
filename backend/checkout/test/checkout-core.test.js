const test = require('node:test');
const assert = require('node:assert/strict');
const { minorUnits, distanceKm, openNow, validRequest, canReadStoreOrders } = require('../checkout-core');

const input = { storeId: 'store-1', addressId: 'home-1', paymentMethod: 'CASH_ON_DELIVERY',
  items: [{ productId: 'dish-1', quantity: 2, expectedPriceMinor: 1250 }] };

test('prices must be exactly representable in cents', () => {
  assert.equal(minorUnits(12.5), 1250);
  assert.equal(minorUnits(12.345), null);
  assert.equal(minorUnits(Infinity), null);
});
test('reject duplicate, negative or untrusted cart input', () => {
  assert.equal(validRequest(input), true);
  assert.equal(validRequest({ ...input, items: [...input.items, ...input.items] }), false);
  assert.equal(validRequest({ ...input, items: [{ ...input.items[0], quantity: -1 }] }), false);
  assert.equal(validRequest({ ...input, paymentMethod: 'CARD' }), false);
});
test('coverage uses geographic distance and overnight opening hours', () => {
  assert.equal(distanceKm({ lat: -12.0464, lng: -77.0428 }, { lat: -12.0464, lng: -77.0428 }), 0);
  const hours = ['SUNDAY','MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY'].map(dayOfWeek =>
    ({ dayOfWeek, openTime: '18:00', closeTime: '02:00', enabled: dayOfWeek === 'THURSDAY' }));
  assert.equal(openNow(hours, new Date('2026-10-23T04:00:00Z')), true); // jueves 23:00 Lima
  assert.equal(openNow(hours, new Date('2026-10-23T06:30:00Z')), true); // viernes 01:30 Lima
  assert.equal(openNow(hours, new Date('2026-10-23T08:00:00Z')), false);
});
test('only the owner or a verified manager assigned to that store can read its orders', () => {
  const business = { ownerUid: 'owner' };
  const assignment = { businessId: 'business', email: 'manager@example.com', assignedStoreIds: ['store-a'] };
  assert.equal(canReadStoreOrders({ uid: 'owner' }, business, null, 'business', 'store-a'), true);
  assert.equal(canReadStoreOrders({ uid: 'manager', email: 'Manager@example.com', email_verified: true },
    business, assignment, 'business', 'store-a'), true);
  assert.equal(canReadStoreOrders({ uid: 'manager', email: 'manager@example.com', email_verified: false },
    business, assignment, 'business', 'store-a'), false);
  assert.equal(canReadStoreOrders({ uid: 'manager', email: 'manager@example.com', email_verified: true },
    business, assignment, 'business', 'store-b'), false);
  assert.equal(canReadStoreOrders({ uid: 'stranger', email: 'stranger@example.com', email_verified: true },
    business, assignment, 'business', 'store-a'), false);
});
