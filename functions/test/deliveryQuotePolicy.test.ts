import { test } from "node:test";
import assert from "node:assert/strict";
import { deliveryFeeMinor, InvalidRouteDistance, metersToMillimeters, quoteTtlMs } from "../src/delivery/deliveryQuotePolicy";

test("tarifa base, kilómetros y metros fraccionarios", () => {
  assert.equal(deliveryFeeMinor(0), 150);
  assert.equal(deliveryFeeMinor(100), 162);
  assert.equal(deliveryFeeMinor(1_250), 300);
  assert.equal(metersToMillimeters(1_837.5), 1_837_500n);
  assert.equal(deliveryFeeMinor(1_837.5), 371);
});

test("empates y lados del umbral se redondean una vez al céntimo", () => {
  assert.equal(deliveryFeeMinor(4.166), 150);
  assert.equal(deliveryFeeMinor(4.167), 151);
  assert.equal(deliveryFeeMinor(12.5), 152);
  assert.equal(deliveryFeeMinor(12.499), 151);
});

test("distancias inválidas, no finitas y desbordadas se rechazan", () => {
  for (const value of [-1, NaN, Infinity, -Infinity, 1e17]) {
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
