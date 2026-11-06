import { test } from "node:test";
import assert from "node:assert/strict";
import { deliveryFeeMinor, InvalidRouteDistance, metersToMillimeters, quoteTtlMs } from "../src/delivery/deliveryQuotePolicy";

test("tarifa proporcional de noventa céntimos por kilómetro", () => {
  assert.equal(deliveryFeeMinor(0), 0);
  assert.equal(deliveryFeeMinor(100), 9);
  assert.equal(deliveryFeeMinor(1_250), 113);
  assert.equal(metersToMillimeters(1_837.5), 1_837_500n);
  assert.equal(deliveryFeeMinor(1_837.5), 165);
});

test("empates y lados del umbral se redondean una vez al céntimo", () => {
  assert.equal(deliveryFeeMinor(5.555), 0);
  assert.equal(deliveryFeeMinor(5.556), 1);
  assert.equal(deliveryFeeMinor(16.667), 2);
  assert.equal(deliveryFeeMinor(16.666), 1);
});

test("distancias inválidas, no finitas y desbordadas se rechazan", () => {
  for (const value of [-1, NaN, Infinity, -Infinity, 2e17]) {
    assert.throws(() => deliveryFeeMinor(value), InvalidRouteDistance);
  }
});

test("vigencia por defecto cinco minutos y configuración acotada", () => {
  assert.equal(quoteTtlMs(undefined), 300_000);
  assert.equal(quoteTtlMs("120"), 120_000);
  for (const value of ["0", "59", "901", "abc", "1.5", "9999999999999999999"]) {
    assert.throws(() => quoteTtlMs(value), /INVALID_QUOTE_TTL/);
  }
});
