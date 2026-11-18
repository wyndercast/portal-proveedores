# Backend — Portal de Proveedores

Backend Spring Boot del Portal de Servicios para Proveedores.

## Estado

La base del proyecto está inicializada. La implementación funcional se realizará incrementalmente conforme a las especificaciones SDD y a los contratos verificados de Valuekeep.

## Tecnología base

- Java 17
- Spring Boot 4.1.2
- Maven Wrapper

## Configuración local

La configuración base se encuentra en `src/main/resources/application.yml`, con perfiles `local` y `prod`. Los secretos y valores dependientes del entorno deben proporcionarse mediante variables externas; no deben almacenarse en el repositorio.

Para arrancar localmente:

```bash
cp .env.example .env
./scripts/run-local.sh
```

El health check está disponible en `http://localhost:8080/actuator/health`.

Para compilar y probar:

```bash
./mvnw -B test
```

Para construir la imagen:

```bash
docker build -t portal-proveedores-backend:local .
```

La documentación común del proyecto se encuentra en `../docs/` desde este repositorio local.

## Alcance pendiente

Todavía no se implementan módulos de negocio, integración con Valuekeep, seguridad propia, generación de informes ni llamadas reales a Valuekeep. Cada elemento se incorporará después de aprobar su especificación correspondiente.

La CI y el CD se ejecutan mediante workflows independientes. El despliegue de producción requiere configurar secretos, la aplicación de DigitalOcean y el registro de imágenes; no se ejecuta automáticamente por push.
