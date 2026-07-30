#!/bin/bash
# =============================================================================
# CENTINELA - Desplegar API de Ingesta
# =============================================================================

set -e

# =============================================================================
# PARÁMETROS
# =============================================================================
RESOURCE_GROUP="rg-centinela-001"
WEB_APP_NAME="api-centinela-001"
APP_SERVICE_PLAN="asp-centinela-001"

echo "================================================"
echo "Desplegando API de Ingesta"
echo "================================================"

# Navegar al directorio de la API
cd src/api

# Crear archivo de configuración para despliegue
echo "Creando configuración de despliegue..."
cat > .deployment << EOF
[config]
SCM_DO_BUILD_DURING_DEPLOYMENT=true
EOF

# Desplegar usando zip deploy
echo "Empaquetando y desplegando..."
zip -r /tmp/api-deploy.zip . -x "*.pyc" "__pycache__/*" "venv/*"

az webapp deployment source config-zip \
    --name $WEB_APP_NAME \
    --resource-group $RESOURCE_GROUP \
    --src /tmp/api-deploy.zip \
    --output table

# Configurar variables de entorno
echo "Configurando variables de entorno..."
az webapp config appsettings set \
    --name $WEB_APP_NAME \
    --resource-group $RESOURCE_GROUP \
    --settings \
        "SCM_DO_BUILD_DURING_DEPLOYMENT=true" \
        "ENABLE_ORYX_BUILD=true" \
    --output table

# Verificar despliegue
echo ""
echo "================================================"
echo "Verificando despliegue..."
echo "================================================"

# Esperar un momento para que la aplicación se inicie
sleep 10

# Probar endpoint de salud
echo "Probando endpoint de salud..."
curl -s "https://$WEB_APP_NAME.azurewebsites.net/api/v1/health" | python -m json.tool

echo ""
echo "================================================"
echo "DESPLIEGUE COMPLETADO"
echo "================================================"
echo ""
echo "URL de la API: https://$WEB_APP_NAME.azurewebsites.net"
echo "Documentación: https://$WEB_APP_NAME.azurewebsites.net/docs"
echo ""
echo "Endpoints disponibles:"
echo "  POST /api/v1/transactions - Recibir transacción"
echo "  GET  /api/v1/transactions/{id} - Consultar transacción"
echo "  GET  /api/v1/health - Verificar salud"
