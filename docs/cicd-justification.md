# Justificacion de Plataforma CI/CD - Centinela

## Decision: GitHub Actions

### Contexto

El equipo de Centinela (4 personas) necesita un pipeline de CI/CD que:
- Ejecute tests automaticamente en cada push
- Construya imagenes Docker y las publique en ACR
- Despliegue al App Service en Azure
- Coute lo minimo posible dentro del presupuesto de $60 / 21 dias

### Alternativas Evaluadas

| Criterio | GitHub Actions | Azure DevOps | Jenkins (self-hosted) |
|---|---|---|---|
| **Costo** | Gratis para repos publicos, 2000 min/mes gratis en privados | Gratis tier basico, pero requiere organizacion Azure DevOps | Requiere VM ($0.04/hr minimo) |
| **Integracion ACR** | Nativa con `azure/login@v1` + `docker/build-push-action` | Nativa con tasks predefinidos | Requiere configuracion manual |
| **Integracion App Service** | Nativa con `azure/webapps-deploy@v2` | Nativa con Azure App Service Deploy task | Requiere scripts custom |
| **Latencia de setup** | < 1 min (hosted runner) | < 2 min (Microsoft-hosted) | 5-10 min (provisionar VM) |
| **Complejidad** | Baja (YAML en repo) | Media (requiere organizacion) | Alta (mantener infra) |
| **Secretos** | GitHub Secrets | Azure Dev Vault | Variables de entorno |
| **Monitoreo** | GitHub UI + logs | Azure DevOps Analytics | Requiere herramientas extras |

### Decision

**GitHub Actions** porque:
1. **Costo $0**: El repo es privado pero el uso es minimo (< 200 min/mes)
2. **Zero setup**: No requiere crear organizacion Azure DevOps ni mantener Jenkins
3. **Monorepo friendly**: Los workflows viven en `.github/workflows/` junto al codigo
4. **Integracion nativa**: `azure/login`, `docker/build-push-action`, `azure/webapps-deploy`
5. **Seguridad**: Los secretos (ACR password, Azure credentials) van en GitHub Secrets, nunca en el repo
6. **Equipo pequeno**: 4 personas no justifican la complejidad de Jenkins o Azure DevOps

### Pipeline

```
.github/workflows/ci.yml
├── docs-lint          # Valida markdown docs
├── build-and-test     # Maven build + test
├── build-and-push     # Docker build + ACR push
└── deploy             # App Service container deploy
```

### Limitaciones conocidas

- Los runners hosted tienen 7 GB de RAM (suficiente para Maven + Docker)
- El tiempo maximo por job es 6 horas (muy por encima de lo necesario)
- Los secrets no estan disponibles en PRs de forks (seguridad)
