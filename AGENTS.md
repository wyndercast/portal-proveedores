# AGENTS.md — Backend del Portal de Proveedores

Este repositorio contiene el backend Spring Boot del portal de proveedores.

## Reglas de trabajo

- Usar Java 17 y Spring Boot 4.1.2.
- Seguir desarrollo dirigido por especificaciones (SDD).
- Consultar las especificaciones en `../docs/specs/` y los contratos en `../docs/integrations/` antes de implementar funcionalidades.
- No inventar endpoints, métodos HTTP, campos JSON, identificadores, estados ni reglas de Valuekeep.
- Mantener el monolito modular organizado por funcionalidad (`package-by-feature`).
- Separar DTO internos de modelos externos de Valuekeep.
- Centralizar OAuth2, cliente HTTP, rate limiting y errores de Valuekeep en `shared/integration/valuekeep/`.
- No incorporar una base de datos propia sin una decisión aprobada.
- No registrar tokens, credenciales ni secretos.
- No crear Dockerfiles, workflows ni configuración de DigitalOcean hasta que exista una especificación de despliegue aprobada.

## Configuración

- Usar `application.yml` como configuración base.
- Mantener secretos fuera del repositorio.
- Spring Boot no carga archivos `.env` automáticamente.
- Los nombres definitivos de las propiedades de Valuekeep dependen de los contratos aprobados.
