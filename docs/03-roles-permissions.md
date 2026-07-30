# Matriz de Roles y Permisos - Centinela

## Roles Definidos

| Rol | Descripción | Autenticación |
|-----|-------------|---------------|
| **Analista de Fraude** | Revisa casos, ve evidencia y decide si confirmar o descartar fraude | Azure AD (usuario) |
| **Administrador** | Configura reglas, ajusta umbrales, gestiona usuarios | Azure AD (usuario) |
| **Servicio** | Comunicación entre componentes internos del sistema | Identidad Gestiónada |
| **Auditor** | Solo lectura sobre todo el sistema | Azure AD (usuario) |

## Matriz de Permisos

### Analista de Fraude

| Recurso | Permiso | Operación del Sistema |
|---------|---------|----------------------|
| Cosmos DB (casos) | Lectura/Escritura | Actualizar estado del caso, asignar analista |
| Cosmos DB (transacciones) | Solo lectura | Verificar detalles de transacción asociada |
| Storage (documentos) | Escritura | Subir documentos de verificación |
| Storage (documentos) | Lectura | Descargar documentos para revisión |
| Service Bus (casos) | Lectura | Recibir casos asignados |

**NO PUEDE:**
- Modificar configuración de infraestructura
- Crear o eliminar recursos de Azure
- Acceder a Key Vault
- Modificar reglas de detección

### Administrador

| Recurso | Permiso | Operación del Sistema |
|---------|---------|----------------------|
| Cosmos DB (reglas) | Lectura/Escritura | Configurar reglas de detección y umbrales |
| Cosmos DB (comercios riesgo) | Lectura/Escritura | Gestionar lista de comercios de riesgo |
| Key Vault | Lectura/Escritura | Gestionar secretos y credenciales |
| App Service | Lectura/Escritura | Configurar la aplicación |
| Function App | Lectura/Escritura | Configurar la Function App |

**NO PUEDE:**
- Modificar permisos de otros usuarios
- Eliminar grupos de recursos
- Acceder a datos de transacciones en bruto

### Servicio (Identidad Gestiónada)

| Recurso | Permiso | Operación del Sistema |
|---------|---------|----------------------|
| Cosmos DB (transacciones) | Escritura | Almacenar transacciones procesadas |
| Cosmos DB (casos) | Escritura | Crear nuevos casos de fraude |
| Service Bus (transacciones) | Escritura | Publicar eventos de transacción |
| Service Bus (casos) | Escritura | Enviar casos marcados a la cola |
| Storage (documentos) | Lectura | Leer documentos para verificación |
| Key Vault | Lectura | Obtener cadenas de conexión |

**NO PUEDE:**
- Crear o eliminar recursos de Azure
- Modificar configuración de la aplicación
- Acceder a Azure AD (no tiene permisos de usuario)

### Auditor

| Recurso | Permiso | Operación del Sistema |
|---------|---------|----------------------|
| Cosmos DB (todos) | Solo lectura | Revisar transacciones, casos y auditoría |
| Storage (documentos) | Solo lectura | Ver documentos de verificación |
| Service Bus | Solo lectura | Ver estado de colas |
| App Service | Solo lectura | Ver configuración |
| Key Vault | Solo lectura | Ver nombres de secretos (no valores) |

**NO PUEDE:**
- Modificar cualquier recurso
- Crear o eliminar recursos
- Acceder a valores de secretos en Key Vault

## Pruebas de Acceso Negativas

| # | Rol | Acción Intentada | Resultado Esperado |
|---|-----|------------------|-------------------|
| 1 | Analista | Modificar configuración de App Service | **Denegado** |
| 2 | Auditor | Eliminar un caso de Cosmos DB | **Denegado** |
| 3 | Servicio | Crear un nuevo Resource Group | **Denegado** |

## Autenticación vs Autorización en Centinela

### Autenticación (¿Quién eres?)

**Ejemplo**: Un analista intenta acceder al sistema.

```python
# El analista se autentica con Azure AD
# Azure AD verifica sus credenciales y devuelve un token
token = azure_ad.authenticate(username="analista@empresa.com", password="***")
```

**Dónde ocurre**: En Azure Active Directory (Azure AD)

### Autorización (¿Qué puedes hacer?)

**Ejemplo**: El analista intenta modificar una regla de detección.

```python
# El sistema verifica si el analista tiene permiso
# para modificar reglas de detección
if not has_permission(token, "rules.update"):
    raise PermissionDenied("No tiene permiso para modificar reglas")
```

**Dónde ocurre**: En cada componente del sistema (API, Function App, Cosmos DB)

### Ejemplo Concreto

1. **Autenticación**: El analista inicia sesión con su correo y contraseña
2. **Autorización**: El sistema verifica que es un "Analista de Fraude"
3. **Acción permitida**: El analista puede ver y actualizar casos
4. **Acción denegada**: El analista no puede modificar reglas de detección
