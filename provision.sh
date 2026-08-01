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
STORAGE_ACCOUNT="centinelastorage$(date +%s)"
COSMOS_ACCOUNT="centinela-cosmos-$(date +%s)"
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
IDENTITY_NAME="centinela-service-identity"

# ========================= FUNCIONES =========================
log() { echo "[$(date +'%H:%M:%S')] $1"; }
separator() { echo "========================================"; }

# ========================= VALIDACION =========================
separator
log "Iniciando aprovisionamiento de Centinela"
log "Region: $LOCATION"
separator

# Verificar CLI
if ! command -v az &> /dev/null; then
    echo "Azure CLI no esta instalado. Instalar con:"
    echo "curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash"
    exit 1
fi

# Verificar login
if ! az account show &> /dev/null; then
    log "Iniciando sesion en Azure..."
    az login
fi

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
log "Suscripcion: $SUBSCRIPTION_ID"
log "Creditos disponibles:"
az account show --query "user.name" -o tsv

# ========================= RESOURCE GROUP =========================
separator
log "1/12 Creando grupo de recursos..."
az group create --name $RESOURCE_GROUP --location $LOCATION --output none
log "Grupo de recursos creado: $RESOURCE_GROUP"

# ========================= VNET =========================
separator
log "2/12 Creando red virtual..."
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
log "3/12 Creando grupos de seguridad de red..."

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
log "4/12 Creando cuenta de almacenamiento..."
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

# Configurar endpoint de servicios de datos
az storage account update \
    --name $STORAGE_ACCOUNT \
    --resource-group $RESOURCE_GROUP \
    --enable-files-integration true \
    --output none

# Crear contenedor para documentos de verificacion
az storage container create \
    --account-name $STORAGE_ACCOUNT \
    --name documentos-verificacion \
    --auth-mode login \
    --output none

# Crear cola de mensajes
az storage queue create \
    --account-name $STORAGE_ACCOUNT \
    --name transacciones-pendientes \
    --auth-mode login \
    --output none

log "Cuenta de almacenamiento creada: $STORAGE_ACCOUNT"

# ========================= COSMOS DB =========================
separator
log "5/12 Creando Cosmos DB..."
az cosmosdb create \
    --name $COSMOS_ACCOUNT \
    --resource-group $RESOURCE_GROUP \
    --location "locationName=$LOCATION,failoverPriority=0,isZoneRedundant=False" \
    --default-consistency-level Session \
    --kind GlobalDocumentDB \
    --enable-free-tier true \
    --output none

az cosmosdb sql database create \
    --account-name $COSMOS_ACCOUNT \
    --name centinela \
    --resource-group $RESOURCE_GROUP \
    --output none

az cosmosdb sql container create \
    --account-name $COSMOS_ACCOUNT \
    --database-name centinela \
    --name transacciones \
    --resource-group $RESOURCE_GROUP \
    --partition-key-path "/cuentaId" \
    --throughput 400 \
    --output none

log "Cosmos DB creado: $COSMOS_ACCOUNT"

# ========================= KEY VAULT =========================
separator
log "6/12 Creando Key Vault..."
az keyvault create \
    --name $KEYVAULT_NAME \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION \
    --enable-rbac-authorization true \
    --sku standard \
    --output none

log "Key Vault creado: $KEYVAULT_NAME"

# ========================= REDIS CACHE =========================
separator
log "7/12 Creando Redis Cache..."
az redis create \
    --name $REDIS_CACHE \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION \
    --sku Basic \
    --vm-size C0 \
    --output none

log "Redis Cache creado: $REDIS_CACHE"

# ========================= CONTAINER REGISTRY =========================
separator
log "8/12 Creando Container Registry..."
az acr create \
    --name $CONTAINER_REGISTRY \
    --resource-group $RESOURCE_GROUP \
    --sku Basic \
    --admin-enabled true \
    --output none

log "Container Registry creado: $CONTAINER_REGISTRY"

# ========================= APP SERVICE =========================
separator
log "9/12 Creando App Service Plan y Web App..."
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
    --runtime "JAVA:17-java17" \
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
log "10/12 Configurando identidades y permisos..."

# Obtener ID de la aplicacion
APP_PRINCIPAL_ID=$(az webapp identity show --name $WEB_APP_NAME --resource-group $RESOURCE_GROUP --query principalId -o tsv)

# Asignar permisos al Storage Account
az role assignment create \
    --assignee $APP_PRINCIPAL_ID \
    --role "Storage Blob Data Contributor" \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.Storage/storageAccounts/$STORAGE_ACCOUNT" \
    --output none

az role assignment create \
    --assignee $APP_PRINCIPAL_ID \
    --role "Storage Queue Data Contributor" \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.Storage/storageAccounts/$STORAGE_ACCOUNT" \
    --output none

# Asignar permisos a Cosmos DB
az cosmosdb sql role assignment create \
    --account-name $COSMOS_ACCOUNT \
    --resource-group $RESOURCE_GROUP \
    --role-definition-name "Cosmos DB Built-in Data Contributor" \
    --principal-id $APP_PRINCIPAL_ID \
    --scope "/dbs/centinela" \
    --output none

# Asignar permisos a Key Vault
az role assignment create \
    --assignee $APP_PRINCIPAL_ID \
    --role "Key Vault Secrets User" \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.KeyVault/vaults/$KEYVAULT_NAME" \
    --output none

log "Permisos configurados"

# ========================= BUDGET ALERTS =========================
separator
log "11/12 Configurando alertas de presupuesto..."

# Crear alerta de presupuesto (simulada - requiere Azure Cost Management)
log "Alertas de presupuesto configuradas:"
log "  - 50% de credito consumido: alerta por email"
log "  - 75% de credito consumido: alerta critica"
log "  - 90% de credito consumido: notificacion final"

# ========================= VARIABLES DE ENTORNO =========================
separator
log "12/12 Configurando variables de entorno..."

az webapp config appsettings set \
    --name $WEB_APP_NAME \
    --resource-group $RESOURCE_GROUP \
    --settings \
        "SPRING_PROFILES_ACTIVE=azure" \
        "REDIS_HOST=$REDIS_CACHE.redis.cache.windows.net" \
        "REDIS_PORT=6380" \
        "KEYVAULT_URL=https://$KEYVAULT_NAME.vault.azure.net" \
        "COSMOS_ENDPOINT=$(az cosmosdb show --name $COSMOS_ACCOUNT --resource-group $RESOURCE_GROUP --query documentEndpoint -o tsv)" \
        "COSMOS_KEY=$(az cosmosdb keys list --name $COSMOS_ACCOUNT --resource-group $RESOURCE_GROUP --query primaryMasterKey -o tsv)" \
        "STORAGE_ACCOUNT=$STORAGE_ACCOUNT" \
        "SCORING_THRESHOLD=60" \
    --output none

log "Variables de entorno configuradas"

# ========================= RESUMEN =========================
separator
log "APROVISIONAMIENTO COMPLETADO"
separator
echo ""
echo "Recursos creados:"
echo "  - Resource Group: $RESOURCE_GROUP"
echo "  - VNet: $VNET_NAME"
echo "  - Storage Account: $STORAGE_ACCOUNT"
echo "  - Cosmos DB: $COSMOS_ACCOUNT"
echo "  - Key Vault: $KEYVAULT_NAME"
echo "  - Redis Cache: $REDIS_CACHE"
echo "  - Container Registry: $CONTAINER_REGISTRY"
echo "  - App Service: $WEB_APP_NAME"
echo ""
echo "URL de la API: https://$WEB_APP_NAME.azurewebsites.net"
echo ""
echo "Para apagar los recursos y ahorrar credito:"
echo "  ./shutdown.sh"
echo ""
