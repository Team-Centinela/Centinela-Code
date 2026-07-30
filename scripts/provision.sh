#!/bin/bash
# =============================================================================
# CENTINELA - Script de Aprovisionamiento
# Motor de detección de fraude transaccional en tiempo real
# =============================================================================

set -e  # Salir si cualquier comando falla

# =============================================================================
# PARÁMETROS - Modificar según necesidad
# =============================================================================
RESOURCE_GROUP="rg-centinela-001"
LOCATION="eastus"                    # Región de despliegue
STORAGE_ACCOUNT="stcentinela001"     # Debe ser globalmente único (3-24 chars, solo minúsculas y números)
KEY_VAULT="kv-centinela-001"         # Debe ser globalmente único
VNET_NAME="vnet-centinela-001"
APP_SERVICE_PLAN="asp-centinela-001"
WEB_APP_NAME="api-centinela-001"     # Debe ser globalmente único
FUNCAPP_NAME="func-scoring-001"     # Debe ser globalmente único
COSMOSDB_NAME="cosmos-centinela-001" # Debe ser globalmente único
SERVICE_BUS_NS="sb-centinela-001"    # Debe ser globalmente único

# =============================================================================
# CREAR GRUPO DE RECURSOS
# =============================================================================
echo "================================================"
echo "Creando grupo de recursos: $RESOURCE_GROUP"
echo "================================================"
az group create --name $RESOURCE_GROUP --location $LOCATION --output table

# =============================================================================
# CREAR RED VIRTUAL
# =============================================================================
echo ""
echo "================================================"
echo "Creando Red Virtual: $VNET_NAME"
echo "================================================"
az network vnet create \
    --resource-group $RESOURCE_GROUP \
    --name $VNET_NAME \
    --address-prefix 10.0.0.0/16 \
    --subnet-name snet-app \
    --subnet-prefix 10.0.1.0/24 \
    --output table

# Crear subred para datos
echo "Creando subred de datos..."
az network vnet subnet create \
    --resource-group $RESOURCE_GROUP \
    --vnet-name $VNET_NAME \
    --name snet-data \
    --address-prefix 10.0.2.0/24 \
    --output table

# Crear subred para Function App
echo "Creando subred para Function App..."
az network vnet subnet create \
    --resource-group $RESOURCE_GROUP \
    --vnet-name $VNET_NAME \
    --name snet-functions \
    --address-prefix 10.0.3.0/24 \
    --output table

# =============================================================================
# CREAR ALMACENAMIENTO (Storage Account)
# =============================================================================
echo ""
echo "================================================"
echo "Creando Almacenamiento: $STORAGE_ACCOUNT"
echo "================================================"
az storage account create \
    --name $STORAGE_ACCOUNT \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION \
    --sku Standard_LRS \
    --kind StorageV2 \
    --access-tier Hot \
    --min-tls-version TLS1_2 \
    --allow-blob-public-access false \
    --output table

# Crear contenedor para documentos
echo "Creando contenedor para documentos..."
az storage container create \
    --name documents \
    --account-name $STORAGE_ACCOUNT \
    --auth-mode login \
    --output table

# Crear contenedor privado (no público)
echo "Configurando nivel de acceso privado..."
az storage container set-permission \
    --name documents \
    --account-name $STORAGE_ACCOUNT \
    --public-access off \
    --output table

# =============================================================================
# CREAR KEY VAULT
# =============================================================================
echo ""
echo "================================================"
echo "Creando Key Vault: $KEY_VAULT"
# =============================================================================
az keyvault create \
    --name $KEY_VAULT \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION \
    --sku standard \
    --enable-rbac-authorization true \
    --output table

# =============================================================================
# CREAR SERVICE BUS (Cola de mensajes)
# =============================================================================
echo ""
echo "================================================"
echo "Creando Service Bus: $SERVICE_BUS_NS"
# =============================================================================
az servicebus namespace create \
    --name $SERVICE_BUS_NS \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION \
    --sku Basic \
    --output table

# Crear cola para transacciones
echo "Creando cola de transacciones..."
az servicebus queue create \
    --name transactions \
    --namespace-name $SERVICE_BUS_NS \
    --resource-group $RESOURCE_GROUP \
    --output table

# Crear cola para casos
echo "Creando cola de casos..."
az servicebus queue create \
    --name cases \
    --namespace-name $SERVICE_BUS_NS \
    --resource-group $RESOURCE_GROUP \
    --output table

# =============================================================================
# CREAR COSMOS DB (Base de datos)
# =============================================================================
echo ""
echo "================================================"
echo "Creando Cosmos DB: $COSMOSDB_NAME"
# =============================================================================
az cosmosdb create \
    --name $COSMOSDB_NAME \
    --resource-group $RESOURCE_GROUP \
    --location "regionName=$LOCATION failoverPriority=0 isZoneRedundant=False" \
    --default-consistency-level Session \
    --enable-automatic-failover false \
    --kind GlobalDocumentDB \
    --output table

# Crear base de datos
echo "Creando base de datos centinela..."
az cosmosdb sql database create \
    --account-name $COSMOSDB_NAME \
    --name centinela \
    --resource-group $RESOURCE_GROUP \
    --output table

# Crear contenedor de transacciones
echo "Creando contenedor de transacciones..."
az cosmosdb sql container create \
    --account-name $COSMOSDB_NAME \
    --database-name centinela \
    --name transactions \
    --partition-key-path "/accountId" \
    --throughput 400 \
    --resource-group $RESOURCE_GROUP \
    --output table

# Crear contenedor de casos
echo "Creando contenedor de casos..."
az cosmosdb sql container create \
    --account-name $COSMOSDB_NAME \
    --database-name centinela \
    --name cases \
    --partition-key-path "/caseId" \
    --throughput 400 \
    --resource-group $RESOURCE_GROUP \
    --output table

# =============================================================================
# CREAR APP SERVICE PLAN
# =============================================================================
echo ""
echo "================================================"
echo "Creando App Service Plan: $APP_SERVICE_PLAN"
# =============================================================================
az appservice plan create \
    --name $APP_SERVICE_PLAN \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION \
    --sku B1 \
    --is-linux \
    --output table

# =============================================================================
# CREAR WEB APP (API de ingesta)
# =============================================================================
echo ""
echo "================================================"
echo "Creando Web App: $WEB_APP_NAME"
# =============================================================================
az webapp create \
    --name $WEB_APP_NAME \
    --resource-group $RESOURCE_GROUP \
    --plan $APP_SERVICE_PLAN \
    --runtime "PYTHON:3.11" \
    --output table

# Configurar VNet integration
echo "Configurando integración con VNet..."
az webapp vnet-integration add \
    --name $WEB_APP_NAME \
    --resource-group $RESOURCE_GROUP \
    --vnet $VNET_NAME \
    --subnet snet-app \
    --output table

# =============================================================================
# CREAR FUNCTION APP (Motor de scoring)
# =============================================================================
echo ""
echo "================================================"
echo "Creando Function App: $FUNCAPP_NAME"
# =============================================================================
az functionapp create \
    --name $FUNCAPP_NAME \
    --resource-group $RESOURCE_GROUP \
    --storage-account $STORAGE_ACCOUNT \
    --consumption-plan-location $LOCATION \
    --runtime python \
    --runtime-version 3.11 \
    --functions-version 4 \
    --os-type Linux \
    --output table

# =============================================================================
# CREAR IDENTITY MANAGED
# =============================================================================
echo ""
echo "================================================"
echo "Habilitando identidad gestionada en Web App"
# =============================================================================
az webapp identity assign \
    --name $WEB_APP_NAME \
    --resource-group $RESOURCE_GROUP \
    --output table

echo "Habilitando identidad gestionada en Function App"
az functionapp identity assign \
    --name $FUNCAPP_NAME \
    --resource-group $RESOURCE_GROUP \
    --output table

# =============================================================================
# GUARDAR INFORMACIÓN DE CONEXIÓN
# =============================================================================
echo ""
echo "================================================"
echo "Guardando cadena de conexión en Key Vault..."
# =============================================================================
STORAGE_KEY=$(az storage account keys list --account-name $STORAGE_ACCOUNT --resource-group $RESOURCE_GROUP --query "[0].value" --output tsv)
COSMOS_KEY=$(az cosmosdb keys list --name $COSMOSDB_NAME --resource-group $RESOURCE_GROUP --query "primaryMasterKey" --output tsv)
SERVICEBUS_KEY=$(az servicebus namespace authorization-rule keys list --name RootManageSharedAccessKey --namespace-name $SERVICE_BUS_NS --resource-group $RESOURCE_GROUP --query "primaryKey" --output tsv)

# Guardar en Key Vault
az keyvault secret set --vault-name $KEY_VAULT --name "StorageConnectionString" --value "DefaultEndpointsProtocol=https;AccountName=$STORAGE_ACCOUNT;AccountKey=$STORAGE_KEY;EndpointSuffix=core.windows.net" --output none
az keyvault secret set --vault-name $KEY_VAULT --name "CosmosConnectionString" --value "AccountEndpoint=https://$COSMOSDB_NAME.documents.azure.com:443/;AccountKey=$COSMOS_KEY;" --output none
az keyvault secret set --vault-name $KEY_VAULT --name "ServiceBusConnectionString" --value "Endpoint=sb://$SERVICE_BUS_NS.servicebus.windows.net/;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=$SERVICEBUS_KEY" --output none

# =============================================================================
# RESUMEN FINAL
# =============================================================================
echo ""
echo "================================================"
echo "APROVISIONAMIENTO COMPLETADO"
echo "================================================"
echo ""
echo "Recursos creados:"
echo "  - Grupo de recursos: $RESOURCE_GROUP"
echo "  - Red Virtual: $VNET_NAME"
echo "  - Almacenamiento: $STORAGE_ACCOUNT"
echo "  - Key Vault: $KEY_VAULT"
echo "  - Service Bus: $SERVICE_BUS_NS"
echo "  - Cosmos DB: $COSMOSDB_NAME"
echo "  - App Service Plan: $APP_SERVICE_PLAN"
echo "  - Web App (API): $WEB_APP_NAME"
echo "  - Function App (Scoring): $FUNCAPP_NAME"
echo ""
echo "Próximos pasos:"
echo "  1. Desplegar la API de ingesta"
echo "  2. Configurar los roles de acceso"
echo "  3. Ejecutar las pruebas de validación"
echo ""
echo "Para apagar los recursos al final del día:"
echo "  ./scripts/shutdown.sh"
