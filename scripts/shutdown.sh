#!/bin/bash
# =============================================================================
# CENTINELA - Script de Apagado
# Ejecutar al cierre de cada jornada para ahorrar crédito
# =============================================================================

set -e

# =============================================================================
# PARÁMETROS (deben coincidir con provision.sh)
# =============================================================================
RESOURCE_GROUP="rg-centinela-001"

echo "================================================"
echo "CENTINELA - Apagando recursos"
echo "================================================"
echo ""
echo "IMPORTANTE: Esto eliminará todos los recursos del grupo $RESOURCE_GROUP"
echo "Presiona Ctrl+C dentro de los próximos 10 segundos para cancelar..."
sleep 10

# Eliminar grupo de recursos completo
echo ""
echo "Eliminando grupo de recursos: $RESOURCE_GROUP"
az group delete --name $RESOURCE_GROUP --yes --no-wait --output table

echo ""
echo "================================================"
echo "RECURSOS ELIMINADOS"
echo "================================================"
echo ""
echo "Los recursos han sido eliminados."
echo "Para volver a crearlos, ejecuta: ./scripts/provision.sh"
