# Chasqui Food

App Android de delivery (Kotlin + Jetpack Compose) con backend `foodApi` en Firebase Cloud Functions v2 (TypeScript). Clientes descubren locales y piden con pago contra entrega; propietarios gestionan negocios y catálogo; responsables de local reciben pedidos; el administrador de Chasqui aprueba y supervisa.

**Estado (31-10-2026):** HU15 (revisión de compra), HU16 (confirmar pedido con avisos) y HU34 (acceso según permisos) publicadas y validadas en el proyecto Firebase `chaski-food-9adba`. Siguen HU17–HU22.

## Qué leer primero
| Orden | Documento | Para qué |
|---|---|---|
| 1 | [docs/ESTADO_IMPLEMENTACION.md](docs/ESTADO_IMPLEMENTACION.md) | Qué está hecho, estado de Cloud y **todos los pendientes** (§4). |
| 2 | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Cómo funciona: Android, foodApi, Firestore, reglas, avisos y entornos LOCAL/CONECTADO. |
| 3 | [docs/GUIA_DESARROLLO.md](docs/GUIA_DESARROLLO.md) | Comandos, pruebas, metodología para nuevas historias, despliegues autorizados, Git e interfaces. |
| 4 | [AGENTS.md](AGENTS.md) | Reglas de trabajo para asistentes de IA (Claude Code lo carga vía [CLAUDE.md](CLAUDE.md)). |
| Consulta | [docs/HU16_DECISIONES_Y_REQUISITOS.md](docs/HU16_DECISIONES_Y_REQUISITOS.md), [docs/HU34_DECISIONES_Y_REQUISITOS.md](docs/HU34_DECISIONES_Y_REQUISITOS.md) | Registro histórico de decisiones y del porqué (pedidos/avisos y permisos). |

## Inicio rápido
```powershell
# LOCAL (emuladores, proyecto demo-chaski-hu15) — detalles en docs/GUIA_DESARROLLO.md §2.1
Copy-Item app/src/debug/google-services.demo.json app/src/debug/google-services.json   # solo si no tienes uno propio
pnpm --dir functions install --frozen-lockfile; pnpm --dir functions run build
$env:CHASKI_ROUTE_FIXTURE = 'enabled'; pnpm --dir functions run emulators
.\gradlew.bat :app:assembleDebug -PchaskiFirebaseMode=LOCAL

# CONECTADO (Firebase real; requiere el google-services.json legítimo, nunca en Git)
.\gradlew.bat :app:assembleDebug "-PchaskiFirebaseMode=CONECTADO" "-PchaskiConnectedProjectId=chaski-food-9adba" "-PchaskiCheckoutApiBaseUrl=https://foodapi-u65lcqarqa-tl.a.run.app/api/v1/"
```

## Reglas básicas
- Probar en LOCAL antes de CONECTADO. **Ningún cambio en Cloud** (Functions, reglas, índices, datos, IAM, secretos) sin autorización humana explícita.
- La autorización la decide el backend (foodApi + reglas de Firestore), nunca la app.
- `checkoutApi` fue eliminada: todo el backend es `foodApi` (`functions/`).
- No subir credenciales, `google-services.json`, `.secret.local`, tokens ni datos personales.
