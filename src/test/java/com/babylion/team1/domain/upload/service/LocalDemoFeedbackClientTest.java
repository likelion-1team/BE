package com.babylion.team1.domain.upload.service;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class LocalDemoFeedbackClientTest {
    private StandardEnvironment environment(String url) {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("demo-test", Map.of(
                "spring.datasource.url", url, "server.address", "127.0.0.1")));
        return environment;
    }
    @Test void refusesAnyNonDemoDatabase() {
        assertThatThrownBy(() -> new LocalDemoFeedbackClient(environment("jdbc:mysql://remote/production")))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void acceptsOnlyMarkedSyntheticInputsAndKeepsOutcomeUnknown() {
        var client = new LocalDemoFeedbackClient(environment("jdbc:mysql://127.0.0.1:3307/toki_demo?serverTimezone=Asia/Seoul"));
        var audit = new ArrayList<GeminiFeedbackClient.Attempt>();
        var result = client.assess("demo", "demo", List.of(new GeminiFeedbackClient.AssessmentMessage("m_0", "USER", "[DEMO] hello")), audit::add);
        assertThat(result.outcome().name()).isEqualTo("UNKNOWN");
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).model()).isEqualTo("demo-local");
        assertThatThrownBy(() -> client.assess("demo", "demo", List.of(new GeminiFeedbackClient.AssessmentMessage("m_0", "USER", "private text")), audit::add))
                .isInstanceOf(GeminiFeedbackClient.ModelFailure.class);
    }
}
