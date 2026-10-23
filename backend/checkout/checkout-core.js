const FEE_MINOR = 500;
const MAX_ITEMS = 20;

function minorUnits(price) {
  if (typeof price !== 'number' || !Number.isFinite(price) || price <= 0) return null;
  const cents = Math.round(price * 100);
  return Number.isSafeInteger(cents) && Math.abs(price * 100 - cents) < 1e-7 ? cents : null;
}

function distanceKm(first, second) {
  const radians = degrees => degrees * Math.PI / 180;
  const dLat = radians(second.lat - first.lat);
  const dLng = radians(second.lng - first.lng);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(radians(first.lat)) *
    Math.cos(radians(second.lat)) * Math.sin(dLng / 2) ** 2;
  return 6371.0088 * 2 * Math.asin(Math.sqrt(Math.min(1, Math.max(0, a))));
}

const DAYS = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
function minutes(time) {
  if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(time || '')) return null;
  const [hours, mins] = time.split(':').map(Number);
  return hours * 60 + mins;
}

function openNow(hours, now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/Lima', weekday: 'long', hour: '2-digit', minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(now);
  const value = name => parts.find(part => part.type === name)?.value;
  const day = String(value('weekday')).toUpperCase();
  const current = Number(value('hour')) * 60 + Number(value('minute'));
  const today = DAYS.indexOf(day);
  if (today < 0 || !Array.isArray(hours) || hours.length !== 7) return false;
  const covers = (entry, previous) => {
    if (!entry?.enabled) return false;
    const start = minutes(entry.openTime);
    const end = minutes(entry.closeTime);
    if (start === null || end === null || start === end) return false;
    return previous ? start > end && current < end :
      start < end ? current >= start && current < end : current >= start;
  };
  return covers(hours.find(h => h.dayOfWeek === DAYS[today]), false) ||
    covers(hours.find(h => h.dayOfWeek === DAYS[(today + 6) % 7]), true);
}

function validRequest(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) return false;
  if (!/^[A-Za-z0-9_-]{1,150}$/.test(body.storeId || '') ||
      !/^[A-Za-z0-9_-]{1,150}$/.test(body.addressId || '') ||
      body.paymentMethod !== 'CASH_ON_DELIVERY' ||
      !Array.isArray(body.items) || body.items.length < 1 || body.items.length > MAX_ITEMS) return false;
  const ids = new Set();
  for (const item of body.items) {
    if (!item || !/^[A-Za-z0-9_-]{1,150}$/.test(item.productId || '') ||
        !Number.isInteger(item.quantity) || item.quantity < 1 || item.quantity > 99 ||
        !Number.isSafeInteger(item.expectedPriceMinor) || item.expectedPriceMinor < 1 ||
        ids.has(item.productId)) return false;
    ids.add(item.productId);
  }
  return true;
}

function canReadStoreOrders(user, business, assignment, businessId, storeId) {
  if (!user || typeof user.uid !== 'string' || !user.uid || !business ||
      typeof business.ownerUid !== 'string' || !business.ownerUid) return false;
  if (business?.ownerUid === user.uid) return true;
  return user.email_verified === true && typeof user.email === 'string' &&
    assignment?.businessId === businessId &&
    assignment.email === user.email.trim().toLowerCase() &&
    Array.isArray(assignment.assignedStoreIds) && assignment.assignedStoreIds.includes(storeId);
}

module.exports = { FEE_MINOR, MAX_ITEMS, minorUnits, distanceKm, openNow, validRequest, canReadStoreOrders };
