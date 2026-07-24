package com.centinela.serverless.infrastructure.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * W3C TraceContext propagation across Service Bus {@code applicationProperties}.
 *
 * <p>The Spring Cloud Azure Service Bus binder surfaces message properties as entry headers on
 * the inbound {@link org.springframework.messaging.Message}, so {@code traceparent} (and
 * optionally {@code tracestate}) ride along automatically. Manual handling is needed for two
 * scenarios:</p>
 * <ul>
 *   <li><b>Inbound:</b> the binder does not always extract custom keys into Micrometer Tracing's
 *       current context. We read them out and decorate the consumer span with the parent
 *       {@code traceId} so Log Analytics queries can stitch the saga.</li>
 *   <li><b>Outbound:</b> the binder publishes headers as-is on the outbound message; we stamp
 *       the active span's {@code traceparent} onto the outbound {@code MessageBuilder} via
 *       {@link #applyOutboundTraceparent(org.springframework.messaging.support.MessageBuilder)}.</li>
 * </ul>
 *
 * <p>Per ADR-007 §7.2: every Service Bus boundary propagates {@code traceparent} /
 * {@code tracestate} so the saga trace survives the broker.</p>
 */
@Component
public class TraceparentPropagator {

    private static final Logger log = LoggerFactory.getLogger(TraceparentPropagator.class);

    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String TRACESTATE_HEADER = "tracestate";

    private final Tracer tracer;

    public TraceparentPropagator(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Extract W3C {@code traceparent} and {@code tracestate} from the inbound message headers
     * and start a CONSUMER span. When no parent header is present, the span is the root.
     */
    public Span startConsumerSpan(String entity, Map<String, Object> headers) {
        String traceparent = headerAsString(headers, TRACEPARENT_HEADER);
        String tracestate = headerAsString(headers, TRACESTATE_HEADER);

        Span.Builder builder = tracer.spanBuilder()
                .name("SB RECEIVE " + entity)
                .kind(Span.Kind.CONSUMER);

        if (traceparent != null) {
            log.debug("Continuing trace from traceparent={} tracestate={}", traceparent, tracestate);
            builder = builder.tag("w3c.traceparent", traceparent);
            if (tracestate != null) {
                builder = builder.tag("w3c.tracestate", tracestate);
            }
        } else {
            log.debug("No traceparent on inbound message — starting root span for entity={}", entity);
        }
        Span span = builder.start();
        return span;
    }

    /**
     * Apply the active span's {@code traceparent} (and {@code tracestate} if non-empty) to an
     * outbound message built via {@link org.springframework.messaging.support.MessageBuilder}.
     * The Spring Cloud Stream binder will persist these as Service Bus application properties.
     */
    public <T> org.springframework.messaging.support.MessageBuilder<T> applyOutboundTraceparent(
            org.springframework.messaging.support.MessageBuilder<T> builder) {
        Span current = tracer.currentSpan();
        if (current == null) {
            return builder;
        }
        String traceparent = current.context().traceId() + "-" + current.context().spanId() + "-01";
        builder.setHeader(TRACEPARENT_HEADER, traceparent);
        String tracestate = null;
        if (tracestate != null && !tracestate.isBlank()) {
            builder.setHeader(TRACESTATE_HEADER, tracestate);
        }
        return builder;
    }

    private static String headerAsString(Map<String, Object> headers, String key) {
        if (headers == null) {
            return null;
        }
        Object raw = headers.get(key);
        return raw == null ? null : raw.toString();
    }
}
