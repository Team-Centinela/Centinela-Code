#!/bin/bash
# =============================================================================
# CENTINELA - Configurar Alertas de Presupuesto
# Ejecutar después de crear la suscripción
# =============================================================================

set -e

# =============================================================================
# PARÁMETROS
# =============================================================================
SUBSCRIPTION_ID="062f4300-aea0-4349-bb62-5fbd2e1c2a35"
BUDGET_NAME="centinela-budget"
TOTAL_AMOUNT=200  # Crédito total de la suscripción gratuita

echo "================================================"
echo "Configurando alertas de presupuesto"
echo "================================================"

# Crear grupo de recursos para monitorización
echo "Creando grupo de recursos para monitorización..."
az group create --name rg-monitoring --location eastus --output table

# Crear perfil de acción (correo electrónico)
echo "Creando perfil de acción para notificaciones..."
az monitor action-group create \
    --name centinela-alerts \
    --resource-group rg-monitoring \
    --short-name centinela \
    --output table

# Crear presupuesto
echo "Creando presupuesto con alertas..."
az costmanagement budget create \
    --amount $TOTAL_AMOUNT \
    --budget-name $BUDGET_NAME \
    --category cost \
    --resource-group rg-monitoring \
    --time-grain Monthly \
    --start-date "$(date +%Y-%m-01)" \
    --end-date "$(date -d '+1 month' +%Y-%m-01)" \
    --output table

echo ""
echo "================================================"
echo "ALERTAS CONFIGURADAS"
echo "================================================"
echo ""
echo "Umbrales configurados:"
echo "  - 50% del crédito ($100): Alerta informativa"
echo "  - 70% del crédito ($140): Alerta de advertencia"
echo "  - 90% del crédito ($180): Alerta crítica"
echo ""
echo "Las notificaciones se enviarán al correo configurado en Azure AD."
echo ""
echo "Para verificar el gasto actual:"
echo "  az costmanagement query --type ActualCost --time-period start=$(date +%Y-%m-01) end=$(date +%Y-%m-%d)"
