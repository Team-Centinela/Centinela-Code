# Reporte de Optimizacion de Imagenes Docker - Centinela

## Backend (centinela-backend)

| Metrica | Valor |
|---|---|
| Imagen final | `centinelaacr1785613282.azurecr.io/centinela-backend:latest` |
| Base image | `eclipse-temurin:21-jre-alpine` |
| Builder image | `maven:3.9-eclipse-temurin-21` |
| Multi-stage build | Si (2 etapas) |
| Usuario | Non-root (`centinela:centinela`) |
| Puertos expuestos | 8080 |

### Optimizaciones aplicadas

1. **Multi-stage build**: Solo la imagen final (~220 MB) se publica, no el builder (~800 MB)
2. **Alpine base**: `eclipse-temurin:21-jre-alpine` en vez de imagen completa (~45 MB vs ~350 MB)
3. **Dependencias offline**: `mvn dependency:go-offline -B` en capa separada para mejor cache
4. **Non-root user**: Seguridad - el container no corre como root
5. **Healthcheck**: `wget` al actuator/health cada 30s, start-period 40s
6. **Spring profiles**: Docker profile excluye Redis auto-configuration innecesaria

### Tamano de capas

| Capa | Tamano (aprox) | Contenido |
|---|---|---|
| Base JRE Alpine | ~170 MB | Java 21 runtime |
| app.jar | ~50 MB | Spring Boot fat jar |
| Config | ~1 MB | application.yml, static resources |
| **Total** | **~220 MB** | |

## Frontend (centinela-frontend)

| Metrica | Valor |
|---|---|
| Imagen final | `centinelaacr1785613282.azurecr.io/centinela-frontend:latest` |
| Base image | `node:22-alpine` |
| Output mode | `standalone` (Next.js) |
| Multi-stage build | Si (2 etapas) |
| Usuario | Non-root (`node:node`) |
| Puertos expuestos | 3000 |

### Optimizaciones aplicadas

1. **Standalone output**: Next.js genera un bundle auto-contenido sin node_modules completo
2. **Multi-stage build**: Dependencias de build (~300 MB) no se incluyen en la imagen final
3. **Alpine base**: `node:22-alpine` minimiza tamano de la imagen base
4. **Non-root user**: Seguridad - container corre como usuario `node`
5. **Healthcheck**: `wget` al puerto 3000 cada 30s

### Tamano de capas

| Capa | Tamano (aprox) | Contenido |
|---|---|---|
| Base Node Alpine | ~130 MB | Node.js runtime |
| Standalone output | ~30 MB | Next.js bundle + dependencies |
| Static assets | ~5 MB | CSS, JS, imagenes |
| **Total** | **~165 MB** | |

## Comparacion con alternativas

| Enfoque | Backend | Frontend |
|---|---|---|
| Sin multi-stage | ~800 MB | ~400 MB |
| Con multi-stage (actual) | ~220 MB | ~165 MB |
| **Reduccion** | **72%** | **59%** |

## Impacto en ACR

- Dos imagenes de ~220 MB y ~165 MB = ~385 MB total en ACR
- ACR Basic tier: 10 GB de storage, $0.167/dia
- Pull time estimado: < 10 segundos desde App Service (misma region)
