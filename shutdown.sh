#!/bin/bash
# =============================================================================
# Script de Apagado - Centinela
# Detiene o elimina los recursos que consumen credito
# Ejecutar al cierre de cada jornada
# =============================================================================

set -e

RESOURCE_GROUP="rg-centinela-prod"

echo "=== Apagando recursos de Centinela ==="
echo ""

# Detener App Service
echo "Deteniendo App Service..."
az webapp stop --resource-group $RESOURCE_GROUP --name centinela-api-1785613299 2>/dev/null || true
az webapp stop --resource-group $RESOURCE_GROUP --name centinela-web-1785613299 2>/dev/null || true

# Detener Redis Cache
echo "Deteniendo Redis Cache..."
az redis stop --resource-group $RESOURCE_GROUP --name centinela-redis 2>/dev/null || true

# Pausar PostgreSQL (ahorra ~90% del costo)
echo "Pausando PostgreSQL..."
az postgres flexible-server stop --resource-group $RESOURCE_GROUP --name centinela-pg-v2 2>/dev/null || true

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
