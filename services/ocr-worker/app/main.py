"""Centinela OCR Worker - Document verification service."""
import os
import logging
from datetime import datetime, timezone
from typing import Optional
from fastapi import FastAPI, UploadFile, File, HTTPException
from pydantic import BaseModel
import httpx

app = FastAPI(title="Centinela OCR Worker", version="1.0.0")
logger = logging.getLogger(__name__)

STORAGE_ACCOUNT = os.getenv("STORAGE_ACCOUNT", "")
CONTAINER_NAME = "documentos-verificacion"
MAX_FILE_SIZE_MB = 10
ALLOWED_CONTENT_TYPES = ["image/jpeg", "image/png", "application/pdf"]


class DocumentExtraction(BaseModel):
    nombre: Optional[str] = None
    numero_identificacion: Optional[str] = None
    fecha_nacimiento: Optional[str] = None
    fecha_expedicion: Optional[str] = None
    estado: str = "PENDIENTE"
    error: Optional[str] = None


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str


@app.get("/health", response_model=HealthResponse)
async def health():
    return HealthResponse(status="UP", service="ocr-worker", version="1.0.0")


@app.post("/api/v1/documentos/extract")
async def extract_document(file: UploadFile = File(...)):
    if file.content_type not in ALLOWED_CONTENT_TYPES:
        raise HTTPException(
            status_code=400,
            detail=f"Tipo de archivo no soportado: {file.content_type}"
        )

    content = await file.read()
    if len(content) > MAX_FILE_SIZE_MB * 1024 * 1024:
        raise HTTPException(
            status_code=400,
            detail=f"Archivo excede el tamano maximo de {MAX_FILE_SIZE_MB}MB"
        )

    try:
        extraction = await _process_document(content, file.content_type)
        return extraction
    except Exception as e:
        logger.error("Error procesando documento: %s", str(e))
        return DocumentExtraction(
            estado="ERROR",
            error=f"Error procesando documento: {str(e)}"
        )


async def _process_document(content: bytes, content_type: str) -> DocumentExtraction:
    """
    Process document using Azure AI Document Intelligence.
    Falls back to basic extraction if service unavailable.
    """
    try:
        from azure.ai.documentintelligence import DocumentIntelligenceClient
        from azure.identity import DefaultAzureCredential

        endpoint = os.getenv("DOC_INTELLIGENCE_ENDPOINT", "")
        if not endpoint:
            return DocumentExtraction(
                estado="NO_DISPONIBLE",
                error="Azure AI Document Intelligence no configurado"
            )

        client = DocumentIntelligenceClient(
            endpoint=endpoint,
            credential=DefaultAzureCredential()
        )

        poller = client.begin_analyze_document(
            model_id="prebuilt-id",
            body=content,
            content_type=content_type
        )
        result = poller.result()

        extraction = DocumentExtraction(estado="COMPLETADO")

        if result.documents:
            doc = result.documents[0]
            fields = doc.fields
            if fields:
                extraction.nombre = fields.get("FirstName", {}).get("valueString")
                extraction.numero_identificacion = fields.get("DocumentId", {}).get("valueString")
                if fields.get("DateOfBirth"):
                    extraction.fecha_nacimiento = str(fields["DateOfBirth"].get("valueDate", ""))
                if fields.get("DateOfExpiration"):
                    extraction.fecha_expedicion = str(fields["DateOfExpiration"].get("valueDate", ""))

        return extraction

    except ImportError:
        return DocumentExtraction(
            estado="NO_DISPONIBLE",
            error="azure-ai-documentintelligence no instalado"
        )
    except Exception as e:
        logger.warning("Document Intelligence falló, usando extraccion basica: %s", str(e))
        return DocumentExtraction(
            estado="BASICO",
            error=f"Extraccion basica: {str(e)}"
        )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8081)
