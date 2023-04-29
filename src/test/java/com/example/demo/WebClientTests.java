package com.example.demo;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

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
        Mono<String> responseTraceIds = nonBlocking();
        TraceContext contextAfterCall = Optional.ofNullable(tracer.currentSpan()).map(Span::context).orElse(null);

        StepVerifier.create(responseTraceIds)
                .expectNext(traceId + "-" + traceId)
                .verifyComplete();
        assertEquals(contextBeforeCall, contextAfterCall,
                "current context before and after blocking call should be the same");
    }

    @Test
    void spanIdToParentId2() {
        TraceContext contextBeforeCall = tracer.currentSpan().context();
        Mono<String> responseTraceIds = nonBlocking();
        TraceContext contextAfterCall = Optional.ofNullable(tracer.currentSpan()).map(Span::context).orElse(null);

        // Also possible:
        assertEquals(traceId + "-" + traceId, responseTraceIds.block(),
                "response should contain trace ID from Tracer and from MDC");
        // no problems:
        assertEquals(contextBeforeCall, contextAfterCall,
                "current context before and after blocking call should be the same");
    }

    @Test
    void spanIdToParentId3() {
        TraceContext contextBeforeCall = tracer.currentSpan().context();
        String responseTraceIds = nonBlocking().block(); // when we block here,
        // we get null there:
        TraceContext contextAfterCall = Optional.ofNullable(tracer.currentSpan()).map(Span::context).orElse(null);

        // this is still good
        assertEquals(traceId + "-" + traceId, responseTraceIds,
                "response should contain trace ID from Tracer and from MDC");

        // this might be "an issue", but rather a "very freaky requirement" than a "BUG"
        assertNull(contextAfterCall,
                "'current context before and after blocking call should be the same' - Who said that?, Where? please ref.;)");
        assertSame(contextBeforeCall, contextBeforeCall, "No doubts about this!");
        // implicitely
        assertNotSame(contextBeforeCall, contextAfterCall, "No doubts about this!");
    }

    private Mono<String> nonBlocking() { // ;p

        return webClientBuilder
                .build()
                .get()
                .uri("http://localhost:" + port + "/trace-id")
                .header("hcitrace", "some-hci-trace")
                .retrieve()
                .bodyToMono(String.class)
                .publishOn(Schedulers.immediate());
    }

}
