# Instrucciones para asistentes de IA en Chasqui Food

Aplican a Claude Code, Codex y cualquier otro asistente. Antes de trabajar, lee [docs/ESTADO_IMPLEMENTACION.md](docs/ESTADO_IMPLEMENTACION.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) y [docs/GUIA_DESARROLLO.md](docs/GUIA_DESARROLLO.md); después, el código pertinente. No audites todo el repositorio sin necesidad. Si un documento contradice al código, verifica en el código y corrige el documento.

## Colaboración
- Responde en español. Antes de implementar, explica en 3–4 líneas el enfoque y su motivo: el equipo está aprendiendo.
- Sigue la metodología de [GUIA_DESARROLLO §3](docs/GUIA_DESARROLLO.md#3-metodología-para-implementar-historias-de-usuario): analiza, propone alternativas en decisiones importantes y **deja la decisión al humano**. Una propuesta tuya no está aprobada hasta que el humano la aprueba.
- Justifica cualquier carpeta, dependencia o abstracción nueva. Señala malas prácticas existentes sin corregirlas fuera del alcance autorizado.
- Revisa rama, HEAD y cambios locales antes de editar; preserva trabajo ajeno y archivos ignorados.
- No hagas commit, push, merge/rebase, cambios de rama ni operaciones que descarten trabajo sin autorización para ese alcance.
- Commits en español con prefijo de tipo; **sin líneas `Co-Authored-By` ni atribución a asistentes**.
- Al terminar, resume cambios, verificaciones realmente ejecutadas y limitaciones.

## Arquitectura
- Módulo `app`, paquete `com.chaskifood.app`, organización por feature (Presentation → Domain ← Data) con Hilt. Infraestructura compartida en `core/`; tema y componentes en `ui/`.
- Sin reglas de negocio en Compose ni acceso directo a Firebase, Room o HTTP desde pantallas. Estado inmutable, corrutinas cancelables, observación ligada al ciclo de vida.
- Reutiliza servicios existentes (`SharedAccessContext`, `CommercialOperations`, `AuthRepository.signOut`…). No centralices providers ni crees abstracciones vacías.
- Backend: amplía `foodApi` en `functions/` separando validación, política y datos, con pruebas en `functions/test/`. **No recrees `checkoutApi`.**

## Seguridad y datos
- No copies ni publiques credenciales, tokens, correos, UID, domicilios ni configuración privada. `google-services.json`, `.secret.local` y `local.properties` quedan fuera de Git.
- No consultes datos reales innecesarios ni cambies Firebase (Functions, reglas, índices, datos, IAM, secretos, facturación) sin autorización específica. Antes de publicar reglas, compáralas con las publicadas.
- Los permisos en el cliente no sustituyen al backend ni a las reglas; no debilites autorización, validación o manejo de errores para hacer pasar un flujo.
- Room: preserva instalaciones; cambios de esquema con versión, migración no destructiva y pruebas. Nunca borres bases ni actives fallback destructivo.
- Usa datos sintéticos y el proyecto `demo-chaski-hu15` en emuladores; distingue siempre LOCAL de CONECTADO.
- No retires `docs/HU04_PLAN.md` (local) mientras su prueba de laboratorio dependa de él.

## Diseño y validación
- Interfaces: respeta Material 3, `ChaskiTheme`, componentes y navegación existentes y DEC-HU34-29 (interfaz del cliente conservada). Si el usuario trabaja con Pencil, lee los `.pen` solo por su MCP y no los modifiques sin autorización.
- Verificaciones proporcionales: `git diff --check`, `.\gradlew.bat :app:assembleDebug`, `:app:testDebugUnitTest`, pruebas de Functions/reglas en el emulador e instrumentadas cuando aplique. Compilar androidTest no equivale a ejecutarlo; un build exitoso no acredita una historia.
- Informa fallos, omisiones y limitaciones sin ocultarlos. No atribuyas resultados antiguos al código actual sin volver a ejecutar la verificación.
