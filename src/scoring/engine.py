"""
Centinela - Motor de Scoring
Componente serverless para detectar fraude en transacciones
"""

import json
import logging
from datetime import datetime, timedelta, timezone
from typing import List, Dict, Any
from math import radians, cos, sin, asin, sqrt
import azure.functions as func

# =============================================================================
# CONFIGURACIÓN
# =============================================================================

# Umbral de score para marcar una transacción como fraudulenta
SCORE_THRESHOLD = 60

# Puntos asignados por cada regla
RULE_SCORES = {
    "velocity": 35,
    "amount_anomaly": 30,
    "geo_impossible": 17,
    "risky_merchant": 20
}

# =============================================================================
# FUNCIONES DE CÁLCULO
# =============================================================================

def haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """
    Calcula la distancia en km entre dos puntos GPS usando la fórmula de Haversine.
    """
    R = 6371  # Radio de la Tierra en km
    
    lat1, lon1, lat2, lon2 = map(radians, [lat1, lon1, lat2, lon2])
    dlat = lat2 - lat1
    dlon = lon2 - lon1
    
    a = sin(dlat/2)**2 + cos(lat1) * cos(lat2) * sin(dlon/2)**2
    c = 2 * asin(sqrt(a))
    
    return R * c

def check_velocity(transactions: List[Dict], current_time: datetime, window_minutes: int = 5) -> Dict:
    """
    Regla 1: Velocidad
    Detecta si hay demasiadas transacciones en una ventana de tiempo corta.
    """
    cutoff_time = current_time - timedelta(minutes=window_minutes)
    recent_transactions = [t for t in transactions if t['timestamp'] > cutoff_time]
    
    if len(recent_transactions) > 3:  # Más de 3 transacciones en 5 minutos
        return {
            "rule": "velocity",
            "triggered": True,
            "points": RULE_SCORES["velocity"],
            "details": {
                "transaction_count": len(recent_transactions),
                "window_minutes": window_minutes,
                "threshold": 3
            }
        }
    
    return {"rule": "velocity", "triggered": False, "points": 0}

def check_amount_anomaly(transactions: List[Dict], current_amount: float) -> Dict:
    """
    Regla 2: Monto atípico
    Detecta si el monto es significativamente mayor al promedio histórico.
    """
    if len(transactions) < 5:
        return {"rule": "amount_anomaly", "triggered": False, "points": 0}
    
    historical_amounts = [t['amount'] for t in transactions]
    avg_amount = sum(historical_amounts) / len(historical_amounts)
    
    # Si el monto actual es 10 veces mayor al promedio, es sospechoso
    if current_amount > avg_amount * 10:
        return {
            "rule": "amount_anomaly",
            "triggered": True,
            "points": RULE_SCORES["amount_anomaly"],
            "details": {
                "current_amount": current_amount,
                "average_amount": avg_amount,
                "ratio": current_amount / avg_amount
            }
        }
    
    return {"rule": "amount_anomaly", "triggered": False, "points": 0}

def check_geo_impossible(transactions: List[Dict], current_location: Dict, current_time: datetime) -> Dict:
    """
    Regla 3: Geo-imposible
    Detecta si dos transacciones están demasiado lejos en poco tiempo.
    """
    if len(transactions) == 0:
        return {"rule": "geo_impossible", "triggered": False, "points": 0}
    
    last_transaction = max(transactions, key=lambda t: t['timestamp'])
    
    # Calcular distancia
    distance_km = haversine(
        last_transaction['location']['latitude'],
        last_transaction['location']['longitude'],
        current_location['latitude'],
        current_location['longitude']
    )
    
    # Calcular tiempo transcurrido
    time_diff_minutes = (current_time - last_transaction['timestamp']).total_seconds() / 60
    
    # Velocidad máxima de viaje: 900 km/h (avión comercial)
    max_speed_kmh = 900
    max_distance = max_speed_kmh * (time_diff_minutes / 60)
    
    if distance_km > max_distance:
        return {
            "rule": "geo_impossible",
            "triggered": True,
            "points": RULE_SCORES["geo_impossible"],
            "details": {
                "distance_km": distance_km,
                "time_diff_minutes": time_diff_minutes,
                "max_possible_distance": max_distance
            }
        }
    
    return {"rule": "geo_impossible", "triggered": False, "points": 0}

def check_risky_merchant(merchant_category: str, risky_categories: List[str]) -> Dict:
    """
    Regla 4: Comercio de riesgo
    Detecta si el comercio está en la lista de categorías de riesgo.
    """
    if merchant_category in risky_categories:
        return {
            "rule": "risky_merchant",
            "triggered": True,
            "points": RULE_SCORES["risky_merchant"],
            "details": {
                "category": merchant_category,
                "risky_categories": risky_categories
            }
        }
    
    return {"rule": "risky_merchant", "triggered": False, "points": 0}

# =============================================================================
# FUNCIÓN PRINCIPAL
# =============================================================================

def main(msg: func.ServiceBusMessage):
    """
    Función principal del motor de scoring.
    Se activa cuando llega un evento de transacción a la cola.
    """
    
    # Decodificar el mensaje
    transaction_data = json.loads(msg.get_body().decode('utf-8'))
    
    logging.info(f"Procesando transacción: {transaction_data['transaction_id']}")
    
    # Extraer datos de la transacción
    account_id = transaction_data['account_id']
    current_amount = transaction_data['amount']
    current_location = transaction_data['location']
    current_time = datetime.fromisoformat(transaction_data['timestamp'].replace('Z', '+00:00'))
    merchant_category = transaction_data['merchant']['category']
    
    # En producción, aquí consultaríamos Cosmos DB para obtener el historial
    # Por ahora, simulamos datos históricos
    historical_transactions = []
    
    # Lista de categorías de riesgo (configurable)
    risky_categories = ["gambling", "crypto", "adult", "travel"]
    
    # Evaluar todas las reglas
    rule_results = []
    total_score = 0
    
    # 1. Verificar velocidad
    velocity_result = check_velocity(historical_transactions, current_time)
    rule_results.append(velocity_result)
    total_score += velocity_result['points']
    
    # 2. Verificar monto atípico
    amount_result = check_amount_anomaly(historical_transactions, current_amount)
    rule_results.append(amount_result)
    total_score += amount_result['points']
    
    # 3. Verificar geo-imposible
    geo_result = check_geo_impossible(historical_transactions, current_location, current_time)
    rule_results.append(geo_result)
    total_score += geo_result['points']
    
    # 4. Verificar comercio de riesgo
    merchant_result = check_risky_merchant(merchant_category, risky_categories)
    rule_results.append(merchant_result)
    total_score += merchant_result['points']
    
    # Determinar si se marca la transacción
    is_flagged = total_score >= SCORE_THRESHOLD
    
    # Construir resultado
    result = {
        "transaction_id": transaction_data['transaction_id'],
        "account_id": account_id,
        "score": total_score,
        "threshold": SCORE_THRESHOLD,
        "is_flagged": is_flagged,
        "rules_evaluated": rule_results,
        "processing_timestamp": datetime.now(timezone.utc).isoformat()
    }
    
    logging.info(f"Score calculado: {total_score} (Umbral: {SCORE_THRESHOLD})")
    
    if is_flagged:
        logging.warning(f"Transacción MARCADA como fraudulenta: {transaction_data['transaction_id']}")
        # En producción: publicar a la cola de casos
        # await service_bus_client.send_message(cases_queue, json.dumps(result))
    
    return result
