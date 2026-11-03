# Actuator

Spring Boot Actuator exposes operational endpoints for your application.

## Endpoints

The health endpoint is available at /actuator/health by default.
Set management.endpoints.web.base-path to change where the endpoints live.

```yaml
management:
  endpoints:
    web:
      base-path: /ops
