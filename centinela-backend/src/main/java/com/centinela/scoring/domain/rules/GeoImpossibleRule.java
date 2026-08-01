package com.centinela.scoring.domain.rules;

import com.centinela.scoring.domain.model.Coordenada;
import com.centinela.scoring.domain.model.RuleActivation;
import com.centinela.scoring.domain.model.TransactionHistory;
import com.centinela.scoring.domain.port.Rule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GeoImpossibleRule implements Rule {

    @Value("${centinela.rules.geo.max-distance-km:1000}")
    private int maxDistanceKm;

    @Value("${centinela.rules.geo.min-time-minutes:60}")
    private int minTimeMinutes;

    @Value("${centinela.rules.geo.points:25}")
    private int points;

    @Override
    public String getRuleId() { return "GEO_IMPOSSIBLE"; }

    @Override
    public String getRuleName() { return "Ubicacion geograficamente imposible"; }

    @Override
    public int getPoints() { return points; }

    @Override
    public RuleActivation evaluate(TransactionHistory current, List<TransactionHistory> history) {
        if (current.getUbicacionLat() == null || current.getUbicacionLon() == null) {
            return null;
        }

            Coordenada coordenadaActual = new Coordenada(current.getUbicacionLat(), current.getUbicacionLon());

        for (TransactionHistory prev : history) {
            if (prev.getUbicacionLat() == null || prev.getUbicacionLon() == null) {
                continue;
            }
            if (prev.getTransactionId().equals(current.getTransactionId())) {
                continue;
            }

            Coordenada previousLocation = new Coordenada(prev.getUbicacionLat(), prev.getUbicacionLon());
            double distance = coordenadaActual.distanciaKm(previousLocation);
            long minutesDiff = Duration.between(prev.getMarcaTiempo(), current.getMarcaTiempo()).toMinutes();

            if (distance > maxDistanceKm && minutesDiff < minTimeMinutes) {
                Map<String, Object> datos = new HashMap<>();
                datos.put("ubicacion_anterior_lat", prev.getUbicacionLat());
                datos.put("ubicacion_anterior_lon", prev.getUbicacionLon());
                datos.put("ubicacion_actual_lat", current.getUbicacionLat());
                datos.put("ubicacion_actual_lon", current.getUbicacionLon());
                datos.put("distancia_km", Math.round(distance));
                datos.put("tiempo_transcurrido_minutos", minutesDiff);

                String descripcion = String.format(
                        "La transaccion anterior de esta cuenta se originó a %.0f km; " +
                        "esta se origina a %.0f km, con %d minutos de diferencia (+%d puntos)",
                        distance, distance, minutesDiff, points);

                return new RuleActivation(getRuleId(), getRuleName(), BigDecimal.valueOf(points), datos, descripcion);
            }
        }

        return null;
    }
}
