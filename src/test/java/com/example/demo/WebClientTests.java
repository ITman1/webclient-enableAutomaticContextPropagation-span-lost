package com.example.demo;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Hooks;

import java.util.Optional;

@AutoConfigureObservability(metrics = false)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebClientTests {

    @Autowired
    private Tracer tracer;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @LocalServerPort
    private int port;

    private String traceId;

    @BeforeAll
    static void beforeAll() {
        Hooks.enableAutomaticContextPropagation();
    }

    @BeforeEach
    void beforeEach() {
        traceId = tracer.startScopedSpan("tracing-test").context().traceId();
    }

    @Test
    void spanIdToParentId() {
        TraceContext contextBeforeCall = tracer.currentSpan().context();
        String responseTraceIds = blockingWebClientCall();
        TraceContext contextAfterCall = Optional.ofNullable(tracer.currentSpan()).map(Span::context).orElse(null);

        Assertions.assertEquals(traceId + "-" + traceId, responseTraceIds, "response should contain trace ID from Tracer and from MDC");
        Assertions.assertEquals(contextBeforeCall, contextAfterCall, "current context before and after blocking call should be the same");
    }

    private String blockingWebClientCall() {
        return webClientBuilder
                .build()
                .get()
                .uri("http://localhost:" + port + "/trace-id")
                .header("hcitrace", "some-hci-trace")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

}
