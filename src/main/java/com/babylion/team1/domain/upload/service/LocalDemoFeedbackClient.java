package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.analysis.entity.AnalysisOutcome;
import com.babylion.team1.domain.analysis.entity.AnalysisReworkType;
import com.babylion.team1.domain.analysis.entity.ScopeClassification;
import com.babylion.team1.domain.upload.service.AnalysisFeedbackRules.Type;
import com.babylion.team1.domain.upload.service.AnalysisPersistence.FeedbackText;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Local synthetic fixtures only. Uses real parsing, persistence and API controllers, no external LLM. */
@Component
@Primary
@Profile("demo")
class LocalDemoFeedbackClient extends GeminiFeedbackClient {
    LocalDemoFeedbackClient(Environment environment) {
        super("", "demo-local");
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.startsWith("jdbc:mysql://127.0.0.1:3307/toki_demo?")
                || !"127.0.0.1".equals(environment.getProperty("server.address"))) {
            throw new IllegalStateException("Demo requires the isolated loopback toki_demo database and server");
        }
    }

    @Override
    Assessment assess(String taskName, String successDefinition, List<AssessmentMessage> messages,
                      Consumer<Attempt> audit) {
        if (messages.isEmpty() || messages.stream().anyMatch(message -> !message.text().startsWith("[DEMO]"))) {
            throw new ModelFailure("DEMO_SYNTHETIC_INPUT_REQUIRED");
        }
        audit.accept(new Attempt(model(), 0, 0, 0, null));
        return new Assessment(ScopeClassification.IN_SCOPE, AnalysisOutcome.UNKNOWN,
                AnalysisReworkType.UNKNOWN, List.of(messages.get(0).key()));
    }

    @Override
    FeedbackText generate(Type type, String question, Consumer<Attempt> audit) {
        if (!question.startsWith("[DEMO]")) throw new ModelFailure("DEMO_SYNTHETIC_INPUT_REQUIRED");
        audit.accept(new Attempt(model(), 0, 0, 0, null));
        return new FeedbackText("[DEMO] " + type.improvement, "[DEMO] " + type.exampleAfter);
    }
}
