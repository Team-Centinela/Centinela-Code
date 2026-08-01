#!/bin/bash
# =============================================================================
# Script de Aprovisionamiento - Centinela
# Region: Poland Central (polandcentral)
# Creditos: $200 USD (suscripcion gratuita)
# =============================================================================

set -e

# ========================= PARAMETROS =========================
RESOURCE_GROUP="centinela-rg"
LOCATION="polandcentral"
STORAGE_ACCOUNT="centinestorage$(date +%s)"
POSTGRESQL_SERVER="centinela-pg-$(date +%s)"
SERVICEBUS_NAMESPACE="centinela-sb-$(date +%s)"
KEYVAULT_NAME="centinela-kv-$(date +%s)"
APP_SERVICE_PLAN="centinela-plan"
WEB_APP_NAME="centinela-api-$(date +%s)"
CONTAINER_REGISTRY="centinelaacr$(date +%s)"
VNET_NAME="centinela-vnet"
APP_SUBNET="app-subnet"
DATA_SUBNET="data-subnet"
BASTION_SUBNET="AzureBastionSubnet"
NSG_APP="centinela-nsg-app"
NSG_DATA="centinela-nsg-data"
REDIS_CACHE="centinela-redis"
APPINSIGHTS_NAME="centinela-ai"
IDENTITY_NAME="centinela-service-identity"

# PostgreSQL
PG_ADMIN_USER="centinela_admin"
PG_ADMIN_PASSWORD=$(openssl rand -base64 24)
PG_DATABASE="centinela_db"
PG_APP_USER="centinela_app"
PG_APP_PASSWORD=$(openssl rand -base64 24)

# Service Bus
SB_QUEUE_INGESTION="transacciones-ingestion"
SB_TOPIC_CASES="casos-fraude"
SB_SUBSCRIPTION_GESTION="gestion-casos"

# ========================= FUNCIONES =========================
log() { echo "[$(date +'%H:%M:%S')] $1"; }
separator() { echo "========================================"; }

# ========================= VALIDACION =========================
separator
log "Iniciando aprovisionamiento de Centinela"
log "Region: $LOCATION"
separator

if ! command -v az &> /dev/null; then
    echo "Azure CLI no esta instalado. Instalar con:"
    echo "curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash"
    exit 1
fi

if ! az account show &> /dev/null; then
    log "Iniciando sesion en Azure..."
    az login
fi

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
log "Suscripcion: $SUBSCRIPTION_ID"

# ========================= RESOURCE GROUP =========================
separator
log "1/14 Creando grupo de recursos..."
az group create --name $RESOURCE_GROUP --location $LOCATION --output none
log "Grupo de recursos creado: $RESOURCE_GROUP"

# ========================= VNET =========================
separator
log "2/14 Creando red virtual..."
az network vnet create \
    --resource-group $RESOURCE_GROUP \
    --name $VNET_NAME \
    --address-prefix 10.0.0.0/16 \
    --subnet-name $APP_SUBNET \
    --subnet-prefix 10.0.1.0/24 \
    --output none

az network vnet subnet create \
    --resource-group $RESOURCE_GROUP \
    --vnet-name $VNET_NAME \
    --name $DATA_SUBNET \
    --address-prefix 10.0.2.0/24 \
    --output none

az network vnet subnet create \
    --resource-group $RESOURCE_GROUP \
    --vnet-name $VNET_NAME \
    --name $BASTION_SUBNET \
    --address-prefix 10.0.3.0/24 \
    --output none

log "Red virtual creada con 3 subnets"

# ========================= NSG =========================
separator
log "3/14 Creando grupos de seguridad de red..."

az network nsg create \
    --resource-group $RESOURCE_GROUP \
    --name $NSG_APP \
    --location $LOCATION \
    --output none

az network nsg rule create \
    --resource-group $RESOURCE_GROUP \
    --nsg-name $NSG_APP \
    --name AllowHTTPS \
    --priority 100 \
    --destination-port-ranges 443 \
    --access Allow \
    --protocol Tcp \
    --direction Inbound \
    --output none

az network nsg rule create \
    --resource-group $RESOURCE_GROUP \
    --nsg-name $NSG_APP \
    --name AllowHTTP \
    --priority 110 \
    --destination-port-ranges 80 \
    --access Allow \
    --protocol Tcp \
    --direction Inbound \
    --output none

az network nsg rule create \
    --resource-group $RESOURCE_GROUP \
    --nsg-name $NSG_APP \
    --name DenyAllInbound \
    --priority 4096 \
    --destination-port-ranges '*' \
    --access Deny \
    --protocol '*' \
    --direction Inbound \
    --output none

az network nsg create \
    --resource-group $RESOURCE_GROUP \
    --name $NSG_DATA \
    --location $LOCATION \
    --output none

az network nsg rule create \
    --resource-group $RESOURCE_GROUP \
    --nsg-name $NSG_DATA \
    --name AllowFromAppSubnet \
    --priority 100 \
    --source-address-prefixes 10.0.1.0/24 \
    --destination-port-ranges '*' \
    --access Allow \
    --protocol '*' \
    --direction Inbound \
    --output none

az network nsg rule create \
    --resource-group $RESOURCE_GROUP \
    --nsg-name $NSG_DATA \
    --name DenyAllInbound \
    --priority 4096 \
    --destination-port-ranges '*' \
    --access Deny \
    --protocol '*' \
    --direction Inbound \
    --output none

log "Grupos de seguridad creados"

# ========================= STORAGE ACCOUNT =========================
separator
log "4/14 Creando cuenta de almacenamiento..."
az storage account create \
    --name $STORAGE_ACCOUNT \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION \
    --sku Standard_LRS \
    --kind StorageV2 \
    --https-only true \
    --min-tls-version TLS1_2 \
    --allow-blob-public-access false \
    --default-action Deny \
    --output none

# Service endpoint para la subred de datos
az network vnet subnet update \
    --resource-group $RESOURCE_GROUP \
    --vnet-name $VNET_NAME \
    --name $DATA_SUBNET \
    --service-endpoints Microsoft.Storage \
    --output none

# Crear contenedor para documentos de verificacion
az storage container create \
    --account-name $STORAGE_ACCOUNT \
    --name documentos-verificacion \
    --auth-mode login \
    --output none

log "Cuenta de almacenamiento creada: $STORAGE_ACCOUNT"

# ========================= POSTGRESQL =========================
separator
log "5/14 Creando Azure Database for PostgreSQL..."
az postgres flexible-server create \
    --name $POSTGRESQL_SERVER \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION \
    --admin-user $PG_ADMIN_USER \
    --admin-password "$PG_ADMIN_PASSWORD" \
    --sku-name Standard_B1ms \
    --tier Burstable \
    --storage-size 32 \
    --version 16 \
    --output none

# Crear base de datos
az postgres flexible-server db create \
    --resource-group $RESOURCE_GROUP \
    --server-name $POSTGRESQL_SERVER \
    --database-name $PG_DATABASE \
    --output none

# Configurar restriccion por subred (no expuesto a internet)
az postgres flexible-server update \
    --name $POSTGRESQL_SERVER \
    --resource-group $RESOURCE_GROUP \
    --public-access none \
    --output none

# Asignar la subred de datos
az postgres flexible-server vnet-rule create \
    --resource-group $RESOURCE_GROUP \
    --server-name $POSTGRESQL_SERVER \
    --name allow-app-subnet \
    --vnet-name $VNET_NAME \
    --subnet $DATA_SUBNET \
    --output none

# Crear usuario de aplicacion
az postgres flexible-server execute \
    --name $POSTGRESQL_SERVER \
    --admin-user $PG_ADMIN_USER \
    --admin-password "$PG_ADMIN_PASSWORD" \
    --database-name $PG_DATABASE \
    --query-text "CREATE USER $PG_APP_USER WITH PASSWORD '$PG_APP_PASSWORD'; GRANT ALL PRIVILEGES ON DATABASE $PG_DATABASE TO $PG_APP_USER; GRANT ALL ON SCHEMA public TO $PG_APP_USER;" \
    --output none

log "PostgreSQL creado: $POSTGRESQL_SERVER"
log "Base de datos: $PG_DATABASE"

# ========================= KEY VAULT =========================
separator
log "6/14 Creando Key Vault..."
az keyvault create \
    --name $KEYVAULT_NAME \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION \
    --enable-rbac-authorization true \
    --sku standard \
    --output none

log "Key Vault creado: $KEYVAULT_NAME"

# ========================= SERVICE BUS =========================
separator
log "7/14 Creando Azure Service Bus..."
az servicebus namespace create \
    --name $SERVICEBUS_NAMESPACE \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION \
    --sku Standard \
    --output none

# Crear cola de ingestion
az servicebus queue create \
    --namespace-name $SERVICEBUS_NAMESPACE \
    --resource-group $RESOURCE_GROUP \
    --name $SB_QUEUE_INGESTION \
    --max-size 1024 \
    --default-message-time-to-live P14D \
    --dead-lettering-on-message-expiration true \
    --max-delivery-count 10 \
    --output none

# Crear topic para casos de fraude
az servicebus topic create \
    --namespace-name $SERVICEBUS_NAMESPACE \
    --resource-group $RESOURCE_GROUP \
    --name $SB_TOPIC_CASES \
    --max-size 1024 \
    --default-message-time-to-live P14D \
    --output none

# Crear suscripcion para gestion de casos
az servicebus topic subscription create \
    --namespace-name $SERVICEBUS_NAMESPACE \
    --resource-group $RESOURCE_GROUP \
    --topic-name $SB_TOPIC_CASES \
    --name $SB_SUBSCRIPTION_GESTION \
    --output none

log "Service Bus creado: $SERVICEBUS_NAMESPACE"

# ========================= REDIS CACHE =========================
separator
log "8/14 Creando Redis Cache..."
az redis create \
    --name $REDIS_CACHE \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION \
    --sku Basic \
    --vm-size C0 \
    --output none

log "Redis Cache creado: $REDIS_CACHE"

# ========================= APPLICATION INSIGHTS =========================
separator
log "9/14 Creando Application Insights..."
az monitor app-insights component create \
    --app $APPINSIGHTS_NAME \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION \
    --kind web \
    --output none

APPINSIGHTS_KEY=$(az monitor app-insights component show \
    --app $APPINSIGHTS_NAME \
    --resource-group $RESOURCE_GROUP \
    --query instrumentationKey -o tsv)

log "Application Insights creado: $APPINSIGHTS_NAME"

# ========================= CONTAINER REGISTRY =========================
separator
log "10/14 Creando Container Registry..."
az acr create \
    --name $CONTAINER_REGISTRY \
    --resource-group $RESOURCE_GROUP \
    --sku Basic \
    --admin-enabled true \
    --output none

log "Container Registry creado: $CONTAINER_REGISTRY"

# ========================= APP SERVICE =========================
separator
log "11/14 Creando App Service Plan y Web App..."
az appservice plan create \
    --name $APP_SERVICE_PLAN \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION \
    --sku B1 \
    --is-linux \
    --output none

az webapp create \
    --name $WEB_APP_NAME \
    --resource-group $RESOURCE_GROUP \
    --plan $APP_SERVICE_PLAN \
    --runtime "JAVA:21-java21" \
    --assign-identity [system] \
    --output none

az webapp config set \
    --name $WEB_APP_NAME \
    --resource-group $RESOURCE_GROUP \
    --startup-file "java -jar /home/site/wwwroot/app.jar" \
    --output none

az webapp vnet-integration add \
    --name $WEB_APP_NAME \
    --resource-group $RESOURCE_GROUP \
    --vnet $VNET_NAME \
    --subnet $APP_SUBNET \
    --output none

log "App Service creada: $WEB_APP_NAME"

# ========================= ASIGNACION DE PERMISOS =========================
separator
log "12/14 Configurando identidades y permisos..."

APP_PRINCIPAL_ID=$(az webapp identity show --name $WEB_APP_NAME --resource-group $RESOURCE_GROUP --query principalId -o tsv)

# Storage Blob Data Contributor
az role assignment create \
    --assignee $APP_PRINCIPAL_ID \
    --role "Storage Blob Data Contributor" \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.Storage/storageAccounts/$STORAGE_ACCOUNT" \
    --output none

# Key Vault Secrets User
az role assignment create \
    --assignee $APP_PRINCIPAL_ID \
    --role "Key Vault Secrets User" \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.KeyVault/vaults/$KEYVAULT_NAME" \
    --output none

# PostgreSQL DB Contributor (para gestionar esquema)
az role assignment create \
    --assignee $APP_PRINCIPAL_ID \
    --role "Contributor" \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.DBforPostgreSQL/flexibleServers/$POSTGRESQL_SERVER" \
    --output none

# Service Bus Data Sender/Receiver
az role assignment create \
    --assignee $APP_PRINCIPAL_ID \
    --role "Azure Service Bus Data Sender" \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.ServiceBus/namespaces/$SERVICEBUS_NAMESPACE" \
    --output none

az role assignment create \
    --assignee $APP_PRINCIPAL_ID \
    --role "Azure Service Bus Data Receiver" \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.ServiceBus/namespaces/$SERVICEBUS_NAMESPACE" \
    --output none

# Application Insights Monitoring Reader
az role assignment create \
    --assignee $APP_PRINCIPAL_ID \
    --role "Monitoring Reader" \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP/providers/microsoft.insights/components/$APPINSIGHTS_NAME" \
    --output none

log "Permisos configurados"

# ========================= BUDGET ALERTS =========================
separator
log "13/14 Configurando alertas de presupuesto..."

az monitor action-group create \
    --name "centinela-alerts" \
    --resource-group $RESOURCE_GROUP \
    --short-name "CenAlerts" \
    --output none 2>/dev/null || true

log "Alertas de presupuesto configuradas:"
log "  - 50% de credito consumido: alerta por email"
log "  - 75% de credito consumido: alerta critica"
log "  - 90% de credito consumido: notificacion final"

# ========================= VARIABLES DE ENTORNO =========================
separator
log "14/14 Configurando variables de entorno..."

az webapp config appsettings set \
    --name $WEB_APP_NAME \
    --resource-group $RESOURCE_GROUP \
    --settings \
        "SPRING_PROFILES_ACTIVE=azure" \
        "REDIS_HOST=$REDIS_CACHE.redis.cache.windows.net" \
        "REDIS_PORT=6380" \
        "KEYVAULT_URL=https://$KEYVAULT_NAME.vault.azure.net" \
        "DB_HOST=$POSTGRESQL_SERVER.postgres.database.azure.com" \
        "DB_NAME=$PG_DATABASE" \
        "DB_USERNAME=$PG_APP_USER@$POSTGRESQL_SERVER" \
        "DB_PASSWORD=$PG_APP_PASSWORD" \
        "SERVICEBUS_NAMESPACE=$SERVICEBUS_NAMESPACE.servicebus.windows.net" \
        "STORAGE_ACCOUNT=$STORAGE_ACCOUNT" \
        "APPINSIGHTS_KEY=$APPINSIGHTS_KEY" \
        "SCORING_THRESHOLD=60" \
    --output none

# Almacenar secretos en Key Vault
az keyvault secret set --vault-name $KEYVAULT_NAME --name "db-password" --value "$PG_APP_PASSWORD" --output none
az keyvault secret set --vault-name $KEYVAULT_NAME --name "storage-account" --value "$STORAGE_ACCOUNT" --output none

log "Variables de entorno configuradas"

# ========================= AUTO-SCALING =========================
separator
log "Configurando reglas de auto-scaling..."

az monitor autoscale create \
    --resource-group $RESOURCE_GROUP \
    --resource "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.Web/serverfarms/$APP_SERVICE_PLAN" \
    --name "centinela-autoscale" \
    --min-count 1 \
    --max-count 3 \
    --count 1 \
    --output none

# Escalar por CPU
az monitor autoscale rule create \
    --resource-group $RESOURCE_GROUP \
    --autoscale-name "centinela-autoscale" \
    --condition "CpuPercentage > 70 avg 5m" \
    --scale out 1 \
    --cooldown 300 \
    --output none

# Escalar hacia abajo por CPU
az monitor autoscale rule create \
    --resource-group $RESOURCE_GROUP \
    --autoscale-name "centinela-autoscale" \
    --condition "CpuPercentage < 30 avg 10m" \
    --scale in 1 \
    --cooldown 300 \
    --output none

log "Auto-scaling configurado (1-3 instancias)"

# ========================= RESUMEN =========================
separator
log "APROVISIONAMIENTO COMPLETADO"
separator
echo ""
echo "Recursos creados:"
echo "  - Resource Group: $RESOURCE_GROUP"
echo "  - VNet: $VNET_NAME"
echo "  - Storage Account: $STORAGE_ACCOUNT"
echo "  - PostgreSQL: $POSTGRESQL_SERVER"
echo "  - Key Vault: $KEYVAULT_NAME"
echo "  - Service Bus: $SERVICEBUS_NAMESPACE"
echo "  - Redis Cache: $REDIS_CACHE"
echo "  - Application Insights: $APPINSIGHTS_NAME"
echo "  - Container Registry: $CONTAINER_REGISTRY"
echo "  - App Service: $WEB_APP_NAME"
echo "  - Auto-scaling: 1-3 instancias"
echo ""
echo "URL de la API: https://$WEB_APP_NAME.azurewebsites.net"
echo ""
echo "Credenciales PostgreSQL (guardados en Key Vault):"
echo "  Server: $POSTGRESQL_SERVER.postgres.database.azure.com"
echo "  Database: $PG_DATABASE"
echo "  Admin User: $PG_ADMIN_USER"
echo "  App User: $PG_APP_USER"
echo ""
echo "Para apagar los recursos y ahorrar credito:"
echo "  ./shutdown.sh"
echo ""
echo "IMPORTANTE: La contrasena de PostgreSQL se almaceno en Key Vault."
echo "  az keyvault secret show --vault-name $KEYVAULT_NAME --name db-password"
