package com.babylion.team1.domain.upload.service;

final class SuccessJudge {

    private SuccessJudge() {
    }

    static boolean isSuccessful(int questionCount, boolean lastQuestionRework) {
        return questionCount > 0 && questionCount <= 10 && !lastQuestionRework;
    }
}
