"""
Centinela - Explicador de Casos
Genera explicaciones legibles para los analistas de fraude
"""

from typing import Dict, List, Any
from datetime import datetime

def generate_explanation(
    transaction_id: str,
    score: int,
    threshold: int,
    rules_triggered: List[Dict]
) -> str:
    """
    Genera una explicación legible de por qué una transacción fue marcada.
    
    Args:
        transaction_id: ID de la transacción
        score: Score total calculado
        threshold: Umbral de detección
        rules_triggered: Lista de reglas que se activaron
    
    Returns:
        Explicación en texto legible
    """
    
    # Construir explicación
    explanation_parts = []
    
    # Encabezado
    explanation_parts.append(
        f"Transacción marcada con score {score} (umbral: {threshold})."
    )
    explanation_parts.append("")
    
    # Detalle de cada regla activada
    for rule in rules_triggered:
        if rule['triggered']:
            rule_name = rule['rule']
            points = rule['points']
            details = rule.get('details', {})
            
            # Generar texto según la regla
            if rule_name == 'velocity':
                count = details.get('transaction_count', 0)
                window = details.get('window_minutes', 0)
                explanation_parts.append(
                    f"Se detectaron {count} transacciones de esta cuenta en los últimos {window} minutos, "
                    f"cuando el promedio es de 1 cada 6 horas (+{points} puntos)."
                )
            
            elif rule_name == 'amount_anomaly':
                current = details.get('current_amount', 0)
                average = details.get('average_amount', 0)
                ratio = details.get('ratio', 0)
                explanation_parts.append(
                    f"El monto de ${current:,.2f} supera en {ratio:.0f}× el promedio histórico "
                    f"de la cuenta (${average:,.2f}) (+{points} puntos)."
                )
            
            elif rule_name == 'geo_impossible':
                distance = details.get('distance_km', 0)
                time_diff = details.get('time_diff_minutes', 0)
                explanation_parts.append(
                    f"La transacción anterior de esta cuenta se originó hace {time_diff:.0f} minutos; "
                    f"esta se origina a {distance:.0f} km (+{points} puntos)."
                )
            
            elif rule_name == 'risky_merchant':
                category = details.get('category', '')
                explanation_parts.append(
                    f"El comercio pertenece a la categoría de riesgo: {category} (+{points} puntos)."
                )
    
    # Unir todo
    return "\n".join(explanation_parts)

def format_case_report(case_data: Dict) -> str:
    """
    Formatea un reporte completo del caso para el analista.
    
    Args:
        case_data: Datos del caso
    
    Returns:
        Reporte formateado
    """
    
    report = []
    
    report.append("=" * 60)
    report.append("REPORTE DE CASO DE FRAUDE")
    report.append("=" * 60)
    report.append("")
    
    report.append(f"Caso ID: {case_data.get('case_id', 'N/A')}")
    report.append(f"Transacción ID: {case_data.get('transaction_id', 'N/A')}")
    report.append(f"Fecha de apertura: {case_data.get('opened_at', 'N/A')}")
    report.append(f"Estado: {case_data.get('status', 'N/A')}")
    report.append("")
    
    report.append("-" * 60)
    report.append("EXPLICACIÓN DE LA DETECCIÓN")
    report.append("-" * 60)
    report.append("")
    
    # Generar explicación
    explanation = generate_explanation(
        transaction_id=case_data.get('transaction_id', ''),
        score=case_data.get('score', 0),
        threshold=case_data.get('threshold', 0),
        rules_triggered=case_data.get('rules_evaluated', [])
    )
    report.append(explanation)
    
    report.append("")
    report.append("-" * 60)
    report.append("ACCIONES DEL ANALISTA")
    report.append("-" * 60)
    report.append("")
    report.append("1. Revisar la explicación de la detección")
    report.append("2. Verificar la información de la transacción")
    report.append("3. Si es fraude confirmado: Marcar como CONFIRMADO")
    report.append("4. Si es falso positivo: Marcar como DESCARTADO")
    report.append("5. Documentar la razón de la decisión")
    report.append("")
    report.append("=" * 60)
    
    return "\n".join(report)

# =============================================================================
# EJEMPLO DE USO
# =============================================================================

if __name__ == "__main__":
    # Ejemplo de datos de caso
    example_case = {
        "case_id": "CASE-001",
        "transaction_id": "TXN-123456",
        "opened_at": "2024-01-15T14:35:00Z",
        "status": "OPEN",
        "score": 82,
        "threshold": 60,
        "rules_evaluated": [
            {
                "rule": "velocity",
                "triggered": True,
                "points": 35,
                "details": {
                    "transaction_count": 5,
                    "window_minutes": 4,
                    "threshold": 3
                }
            },
            {
                "rule": "amount_anomaly",
                "triggered": True,
                "points": 30,
                "details": {
                    "current_amount": 4200000,
                    "average_amount": 50000,
                    "ratio": 84
                }
            },
            {
                "rule": "geo_impossible",
                "triggered": True,
                "points": 17,
                "details": {
                    "distance_km": 8000,
                    "time_diff_minutes": 11,
                    "max_possible_distance": 165
                }
            }
        ]
    }
    
    # Generar reporte
    report = format_case_report(example_case)
    print(report)
