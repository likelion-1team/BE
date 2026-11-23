package com.babylion.team1.domain.upload.service;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ReworkJudge {

    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private final List<String> keywords;

    ReworkJudge(@Value("${app.analysis.rework-keywords}") List<String> keywords) {
        this.keywords = keywords.stream().map(ReworkJudge::normalize).filter(word -> !word.isEmpty()).toList();
    }

    boolean isRework(String question, boolean branched, List<String> previousQuestions) {
        if (previousQuestions.isEmpty()) {
            return false;
        }
        String text = normalize(question);
        if (branched || keywords.stream().anyMatch(text::contains)) {
            return true;
        }
        Set<String> current = trigrams(text);
        return previousQuestions.subList(Math.max(0, previousQuestions.size() - 3), previousQuestions.size())
                .stream().anyMatch(previous -> {
                    String normalized = normalize(previous);
                    if (text.equals(normalized)) {
                        return true;
                    }
                    Set<String> earlier = trigrams(normalized);
                    long overlap = earlier.stream().filter(current::contains).count();
                    long union = current.size() + earlier.size() - overlap;
                    return union > 0 && overlap * 5 >= union * 3;
                });
    }

    private static String normalize(String text) {
        return SPACES.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).strip()).replaceAll(" ");
    }

    private static Set<String> trigrams(String text) {
        int[] points = text.codePoints().toArray();
        Set<String> result = new HashSet<>();
        for (int i = 0; i + 2 < points.length; i++) {
            result.add(new String(points, i, 3));
        }
        return result;
    }
}
