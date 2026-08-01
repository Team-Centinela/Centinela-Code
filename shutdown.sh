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

# Detener App Service (no elimina, solo detiene)
echo "Deteniendo App Service..."
az webapp stop --name centinela-api-* --resource-group $RESOURCE_GROUP 2>/dev/null || true

# Detener Redis Cache
echo "Deteniendo Redis Cache..."
az redis stop --name centinela-redis-* --resource-group $RESOURCE_GROUP 2>/dev/null || true

echo ""
echo "=== Recursos detenidos ==="
echo ""
echo "Para reanudar:"
echo "  az webapp start --name centinela-api-<suffix> --resource-group $RESOURCE_GROUP"
echo "  az redis start --name centinela-redis-<suffix> --resource-group $RESOURCE_GROUP"
echo ""
echo "Para ELIMINAR todo (libera credito completo):"
echo "  az group delete --name $RESOURCE_GROUP --yes --no-wait"
echo ""
echo "IMPORTANTE: Ejecutar shutdown.sh al cierre de cada jornada"
echo "para evitar consumo innecesario de credito."
