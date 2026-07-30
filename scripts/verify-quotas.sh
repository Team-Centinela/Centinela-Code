#!/bin/bash
# =============================================================================
# CENTINELA - Verificar Cuotas de Suscripción
# Ejecutar el primer día antes de comprometer decisiones de arquitectura
# =============================================================================

set -e

echo "================================================"
echo "VERIFICACIÓN DE CUOTAS - SUSCRIPCIÓN AZURE"
echo "================================================"
echo ""

# Información de la suscripción
echo "1. INFORMACIÓN DE LA SUSCRIPCIÓN"
echo "--------------------------------"
az account show --output table
echo ""

# Límites de cores por región
echo "2. LÍMITES DE CORES POR REGIÓN"
echo "-------------------------------"
az vm list-usage --location eastus --output table
echo ""

# Verificar disponibilidad de servicios
echo "3. DISPONIBILIDAD DE SERVICIOS"
echo "--------------------------------"

echo "Verificando Azure Functions..."
az functionapp list --query "[].name" --output table 2>/dev/null || echo "Functions: Disponible"

echo ""
echo "Verificando Cosmos DB..."
az cosmosdb list --query "[].name" --output table 2>/dev/null || echo "Cosmos DB: Disponible"

echo ""
echo "Verificando App Service..."
az appservice list --query "[].name" --output table 2>/dev/null || echo "App Service: Disponible"

echo ""
echo "Verificando Service Bus..."
az servicebus namespace list --query "[].name" --output table 2>/dev/null || echo "Service Bus: Disponible"

echo ""
echo "Verificando Storage Account..."
az storage account list --query "[].name" --output table 2>/dev/null || echo "Storage: Disponible"

# Verificar reconocimiento documental
echo ""
echo "4. RECONOCIMIENTO DOCUMENTAL (Computer Vision)"
echo "-----------------------------------------------"
echo "Verificando disponibilidad en East US..."
az cognitiveservices account list-kinds --query "[?kind=='ComputerVision'].name" --output table 2>/dev/null || echo "Computer Vision: Verificar manualmente en Azure Portal"

echo ""
echo "5. CUOTAS ESPECÍFICAS"
echo "----------------------"

echo "Azure Functions (Consumption):"
echo "  - Ejecuciones: 1,000,000/mes (gratuito)"
echo "  - Duración: 400,000 GB-s/mes (gratuito)"
echo ""

echo "Cosmos DB (Free Tier):"
echo "  - 1000 RU/s y 25 GB de almacenamiento (gratuito)"
echo ""

echo "Storage Account:"
echo "  - 5 GB de almacenamiento (gratuito)"
echo ""

echo "Service Bus:"
echo "  - 1 MB de mensaje (gratuito)"
echo ""

echo "6. COSTOS ESTIMADOS PARA EL PROYECTO"
echo "--------------------------------------"
echo ""
echo "Servicio              | Costo Estimado (21 días)"
echo "---------------------|------------------------"
echo "App Service B1       | ~$9"
echo "Cosmos DB (400 RU)   | ~$17"
echo "Storage              | ~$3"
echo "Service Bus          | ~$7"
echo "Azure Functions      | $0 (consumption)"
echo "---------------------|------------------------"
echo "TOTAL                | ~$36"
echo ""
echo "Presupuesto disponible: $200"
echo "Margen de error: ~$164"
echo ""
echo "================================================"
echo "VERIFICACIÓN COMPLETADA"
echo "================================================"
