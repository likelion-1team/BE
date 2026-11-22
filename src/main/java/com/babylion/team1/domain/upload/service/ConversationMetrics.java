package com.babylion.team1.domain.upload.service;

import static com.babylion.team1.domain.conversation.entity.MessageRole.USER;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ConversationMetrics {

    private final ReworkJudge reworkJudge;
    private final com.knuddels.jtokkit.api.Encoding tokenizer =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.O200K_BASE);

    Result calculate(List<ChatGptConversationBranch.Turn> turns) {
        var questions = new ArrayList<String>();
        var requests = new ArrayList<Request>();
        long context = 0;
        int review = 0;
        int rework = 0;
        int index = 0;
        String model = null;
        LocalDateTime lastAnswer = null;
        for (var turn : turns) {
            int tokens = tokenizer.countTokensOrdinary(turn.content());
            context += tokens;
            if (turn.role() == USER) {
                boolean repeated = reworkJudge.isRework(turn.content(), turn.branchPoint(), questions);
                if (lastAnswer != null && turn.createdAt() != null) {
                    Duration gap = Duration.between(lastAnswer, turn.createdAt());
                    if (!gap.isNegative() && gap.compareTo(Duration.ofMinutes(10)) <= 0) {
                        if (repeated) {
                            rework += (int) gap.toSeconds();
                        } else {
                            review += (int) gap.toSeconds();
                        }
                    }
                }
                requests.add(new Request(index, context, 0, tokens, repeated));
                questions.add(turn.content());
                lastAnswer = null;
            } else {
                if (turn.model() != null) {
                    model = turn.model();
                }
                if (!requests.isEmpty()) {
                    int last = requests.size() - 1;
                    var request = requests.get(last);
                    requests.set(last, new Request(request.index(), request.input(), request.output() + tokens,
                            request.questionTokens(), request.rework()));
                }
                lastAnswer = turn.createdAt();
            }
            index++;
        }
        boolean success = !requests.isEmpty()
                && SuccessJudge.isSuccessful(requests.size(), requests.get(requests.size() - 1).rework());
        return new Result(List.copyOf(requests), model, review, rework, success);
    }

    record Request(int index, long input, long output, int questionTokens, boolean rework) {
    }

    record Result(List<Request> requests, String model, int review, int rework, boolean success) {
        long input() {
            return requests.stream().mapToLong(Request::input).sum();
        }

        long output() {
            return requests.stream().mapToLong(Request::output).sum();
        }
    }
}
