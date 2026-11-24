package com.babylion.team1.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class GeminiFeedbackClientTest {

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 429, 503})
    void assessmentRetriesOnlyTransientResponses(int status) throws Exception {
        var client = new GeminiFeedbackClient("test-key", "test-model");
        var http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
        ReflectionTestUtils.setField(client, "http", http);

        var attempts = new ArrayList<GeminiFeedbackClient.Attempt>();
        assertThatThrownBy(() -> client.assess("보고서", "제출", List.of(), attempts::add))
                .isInstanceOf(GeminiFeedbackClient.ModelFailure.class)
                .hasMessage("HTTP_" + status);

        int expected = status == 429 || status == 503 ? 3 : 1;
        assertThat(attempts).hasSize(expected).allMatch(attempt -> attempt.errorCode().equals("HTTP_" + status));
        verify(http, times(expected)).send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }
}
