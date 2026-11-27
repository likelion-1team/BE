package com.babylion.team1.domain.comparison;

import com.babylion.team1.domain.team.repository.TeamMemberRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class PromptComparisonService {

    private static final long WINDOW_NANOS = Duration.ofMinutes(1).toNanos();
    private static final int PAIRS_PER_USER = 3;
    private static final int PAIRS_PER_SERVER = 12;

    private final TeamMemberRepository members;
    private final OpenAiComparisonClient openAi;
    private final Map<Long, Integer> runsByUser = new HashMap<>();
    private long windowStartedAt = System.nanoTime();
    private int runsInWindow;

    PromptComparisonResult.Configuration configuration(Long teamId, Long userId) {
        requireMembership(teamId, userId);
        return openAi.configuration();
    }

    PromptComparisonResult compare(Long teamId, Long userId, PromptComparisonRequest request) {
        if (!configuration(teamId, userId).configured()) {
            throw new CustomException(ErrorCode.PROMPT_COMPARISON_UNAVAILABLE);
        }
        claimRun(userId);
        var baseline = openAi.run(request.task(), request.baselinePrompt());
        var improved = openAi.run(request.task(), request.improvedPrompt());
        return baseline.thenCombine(improved, PromptComparisonResult::new).join();
    }

    private void requireMembership(Long teamId, Long userId) {
        members.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_A_MEMBER));
    }

    private synchronized void claimRun(Long userId) {
        long now = System.nanoTime();
        if (now - windowStartedAt >= WINDOW_NANOS) {
            windowStartedAt = now;
            runsInWindow = 0;
            runsByUser.clear();
        }
        int userRuns = runsByUser.getOrDefault(userId, 0);
        if (userRuns >= PAIRS_PER_USER || runsInWindow >= PAIRS_PER_SERVER) {
            throw new CustomException(ErrorCode.RATE_LIMITED);
        }
        runsByUser.put(userId, userRuns + 1);
        runsInWindow++;
    }
}
