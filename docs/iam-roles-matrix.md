# Matriz de Roles y Permisos - Centinela

## Roles Definidos

### 1. Analista de Fraude
**Funcion**: Revisar casos marcados, verificar evidencia, resolver o escalar

| Recurso | Accion | Permiso Azure | Justificacion |
|---|---|---|---|
| Blob Storage | Leer documentos | Storage Blob Data Reader | Ver documentos de verificacion |
| Blob Storage | Subir documentos | Storage Blob Data Contributor | Cargar documentos de escalamiento |
| PostgreSQL | Leer datos | PostgreSQL DB Viewer | Consultar casos y transacciones |
| PostgreSQL | Actualizar casos | PostgreSQL DB Contributor | Actualizar estado de casos |
| App Service | Leer metricas | Monitoring Reader | Ver dashboard de monitoreo |

**Permisos denegados**:
- Modificar configuracion de infraestructura
- Crear o eliminar recursos
- Acceder a Key Vault
- Modificar Service Bus

### 2. Administrador
**Funcion**: Configurar reglas, ajustar umbral, gestionar usuarios

| Recurso | Accion | Permiso Azure | Justificacion |
|---|---|---|---|
| PostgreSQL | Configurar | PostgreSQL DB Administrator | Gestionar esquema y configuracion |
| Service Bus | Gestionar | Azure Service Bus Data Sender/Receiver | Configurar colas y topics |
| Key Vault | Gestionar secrets | Key Vault Secrets Officer | Administra secretos |
| App Service | Configurar | Website Contributor | Configurar App Service |
| Monitoring | Ver alertas | Monitoring Reader | Ver estado del sistema |

**Permisos denegados**:
- Eliminar recursos de infraestructura
- Acceder a datos de transacciones directamente

### 3. Servicio (Identidad Gestionada)
**Funcion**: Operar componentes internos automaticamente

| Recurso | Accion | Permiso Azure | Justificacion |
|---|---|---|---|
| PostgreSQL | Leer/Escribir | PostgreSQL DB Contributor | Persistir transacciones y casos |
| Blob Storage | Leer/Escribir | Storage Blob Data Contributor | Almacenar documentos |
| Service Bus | Enviar/Recibir | Azure Service Bus Data Sender/Receiver | Publicar/consumir eventos |
| Key Vault | Leer secrets | Key Vault Secrets User | Obtener cadenas de conexion |

**NO tiene**:
- Permisos de plano de control (crear/eliminar recursos)
- Credenciales administradas por la celula

### 4. Auditor (solo lectura)
**Funcion**: Ver todo el sistema sin modificar nada

| Recurso | Accion | Permiso Azure | Justificacion |
|---|---|---|---|
| Subscription | Ver recursos | Reader | Ver todos los recursos |
| PostgreSQL | Solo lectura | PostgreSQL DB Viewer | Consultar datos |
| Monitoring | Ver metricas | Monitoring Reader | Ver dashboards y alertas |

**NO tiene**:
- Ningun permiso de escritura
- Acceso a secretos
- Posibilidad de modificar configuracion

## Pruebas de Acceso Negativas

| # | Rol | Accion Intentada | Resultado Esperado | Resultado Real |
|---|---|---|---|---|
| 1 | Analista | Modificar configuracion de App Service | Denegado | PASS |
| 2 | Auditor | Modificar cualquier recurso | Denegado | PASS |
| 3 | Servicio | Crear un recurso nuevo | Denegado | PASS |

## Autenticacion vs Autorizacion

### Autenticacion (Quien eres?)
**Ocurre en**: Azure AD / Managed Identity
**Ejemplo**: El App Service se autentica contra Key Vault usando su Managed Identity (principalId)

### Autorizacion (Que puedes hacer?)
**Ocurre en**: Cada servicio de Azure
**Ejemplo**: La Managed Identity tiene el rol "Key Vault Secrets User" que le permite leer secrets pero no crearlos
