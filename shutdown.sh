#!/bin/bash
# =============================================================================
# Script de Apagado - Centinela
# Detiene o elimina los recursos que consumen credito
# Ejecutar al cierre de cada jornada
# =============================================================================

set -e

RESOURCE_GROUP="centinela-rg"

echo "=== Apagando recursos de Centinela ==="
echo ""

# Detener App Service
echo "Deteniendo App Service..."
az webapp stop --resource-group $RESOURCE_GROUP --name $(az webapp list --resource-group $RESOURCE_GROUP --query "[0].name" -o tsv) 2>/dev/null || true

# Detener Redis Cache
echo "Deteniendo Redis Cache..."
az redis stop --resource-group $RESOURCE_GROUP --name $(az redis list --resource-group $RESOURCE_GROUP --query "[0].name" -o tsv) 2>/dev/null || true

# Detener PostgreSQL (pausa, no elimina)
echo "Pausando PostgreSQL..."
az postgres flexible-server stop --resource-group $RESOURCE_GROUP --name $(az postgres flexible-server list --resource-group $RESOURCE_GROUP --query "[0].name" -o tsv) 2>/dev/null || true

echo ""
echo "=== Recursos detenidos ==="
echo ""
echo "Para reanudar:"
echo "  az webapp start --resource-group $RESOURCE_GROUP --name <app-name>"
echo "  az redis start --resource-group $RESOURCE_GROUP --name <redis-name>"
echo "  az postgres flexible-server start --resource-group $RESOURCE_GROUP --name <pg-name>"
echo ""
echo "Para ELIMINAR todo (libera credito completo):"
echo "  az group delete --name $RESOURCE_GROUP --yes --no-wait"
echo ""
echo "IMPORTANTE: Ejecutar shutdown.sh al cierre de cada jornada"
echo "para evitar consumo innecesario de credito."
