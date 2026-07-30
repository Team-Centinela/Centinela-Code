"""
Centinela - API de Ingesta de Transacciones
Motor de detección de fraude transaccional en tiempo real
"""

from fastapi import FastAPI, HTTPException, status
from pydantic import BaseModel, Field, validator
from datetime import datetime, timezone
from typing import Optional
from uuid import UUID
import uuid

app = FastAPI(
    title="Centinela - API de Ingesta",
    description="API para recibir transacciones y persistirlas para análisis de fraude",
    version="1.0.0"
)

# =============================================================================
# MODELOS DE DATOS
# =============================================================================

class Location(BaseModel):
    latitude: float = Field(..., ge=-90, le=90, description="Latitud (-90 a 90)")
    longitude: float = Field(..., ge=-180, le=180, description="Longitud (-180 a 180)")

class Merchant(BaseModel):
    id: str = Field(..., min_length=1, max_length=50, description="Identificador del comercio")
    name: str = Field(..., min_length=1, max_length=100, description="Nombre del comercio")
    category: str = Field(..., min_length=1, max_length=50, description="Categoría del comercio")

class Metadata(BaseModel):
    channel: Optional[str] = Field(None, description="Canal: online, pos, atm, mobile")
    device_id: Optional[str] = Field(None, description="Identificador del dispositivo")
    ip_address: Optional[str] = Field(None, description="Dirección IP")

class Transaction(BaseModel):
    transaction_id: UUID = Field(..., description="Identificador único de la transacción")
    timestamp: datetime = Field(..., description="Fecha y hora en formato ISO 8601 UTC")
    account_id: str = Field(..., min_length=1, max_length=50, description="Identificador de la cuenta")
    amount: float = Field(..., gt=0, description="Monto de la transacción (debe ser positivo)")
    currency: str = Field(..., min_length=3, max_length=3, description="Código de moneda ISO 4217")
    type: str = Field(..., description="Tipo: purchase, transfer, withdrawal")
    location: Location
    merchant: Merchant
    metadata: Optional[Metadata] = None
    
    # Campo adicional para marca de tiempo del servidor
    server_timestamp: Optional[datetime] = Field(None, description="Marca de tiempo del servidor")

    @validator('type')
    def validate_type(cls, v):
        allowed_types = ['purchase', 'transfer', 'withdrawal']
        if v not in allowed_types:
            raise ValueError(f'Tipo debe ser uno de: {allowed_types}')
        return v

    @validator('currency')
    def validate_currency(cls, v):
        allowed_currencies = ['COP', 'USD', 'EUR', 'GBP', 'JPY', 'MXN', 'BRL']
        if v not in allowed_currencies:
            raise ValueError(f'Moneda debe ser una de: {allowed_currencies}')
        return v

    @validator('timestamp')
    def validate_timestamp(cls, v):
        if v > datetime.now(timezone.utc):
            raise ValueError('La marca de tiempo no puede ser en el futuro')
        return v

# =============================================================================
# ENDPOINTS
# =============================================================================

@app.post(
    "/api/v1/transactions",
    status_code=status.HTTP_201_CREATED,
    response_model=dict,
    summary="Recibir una transacción"
)
async def receive_transaction(transaction: Transaction):
    """
    Recibe una transacción, la valida y la persiste.
    
    Retorna un acuse de recibo inmediato.
    El análisis de fraude se ejecuta de forma asíncrona.
    """
    
    # Agregar marca de tiempo del servidor
    transaction.server_timestamp = datetime.now(timezone.utc)
    
    # Simular persistencia (en producción se guardaría en Cosmos DB)
    # Aquí iría la lógica de persistencia
    
    return {
        "status": "accepted",
        "transaction_id": str(transaction.transaction_id),
        "server_timestamp": transaction.server_timestamp.isoformat(),
        "message": "Transacción recibida y persistida correctamente"
    }

@app.get(
    "/api/v1/transactions/{transaction_id}",
    status_code=status.HTTP_200_OK,
    summary="Consultar una transacción"
)
async def get_transaction(transaction_id: str):
    """
    Consulta una transacción por su ID.
    Solo para auditoría interna.
    """
    
    # Simular consulta (en producción se consultaría Cosmos DB)
    return {
        "transaction_id": transaction_id,
        "status": "processed",
        "message": "Transacción encontrada"
    }

@app.get(
    "/api/v1/health",
    status_code=status.HTTP_200_OK,
    summary="Verificar salud de la API"
)
async def health_check():
    """
    Endpoint de verificación de salud.
    """
    return {
        "status": "healthy",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "version": "1.0.0"
    }

# =============================================================================
# MANEJO DE ERRORES
# =============================================================================

@app.exception_handler(ValueError)
async def value_error_handler(request, exc):
    return HTTPException(
        status_code=status.HTTP_400_BAD_REQUEST,
        detail={
            "error": "Validation error",
            "message": str(exc)
        }
    )

@app.exception_handler(Exception)
async def general_error_handler(request, exc):
    return HTTPException(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        detail={
            "error": "Internal server error",
            "message": "Error interno del servidor"
        }
    )

# =============================================================================
# EJECUCIÓN
# =============================================================================

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
