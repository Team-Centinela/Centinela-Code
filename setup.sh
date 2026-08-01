#!/bin/bash

# Script de configuracion del proyecto Centinela
# Ejecutar desde la raiz del proyecto

set -e

echo "=== Configurando proyecto Centinela ==="

# Backend
echo "Compilando backend..."
cd centinela-backend
mvn clean package -DskipTests -q
echo "Backend compilado correctamente"

# Frontend
echo "Configurando frontend..."
cd ../centinela-frontend
pnpm install --frozen-lockfile
pnpm build
echo "Frontend configurado correctamente"

cd ..

echo ""
echo "=== Proyecto Centinela configurado ==="
echo ""
echo "Para ejecutar en modo local:"
echo "  Backend:  cd centinela-backend && mvn spring-boot:run -Dspring-boot.run.profiles=local"
echo "  Frontend: cd centinela-frontend && pnpm dev"
echo ""
echo "Puertos:"
echo "  Backend:  http://localhost:8080"
echo "  Frontend: http://localhost:3000"
