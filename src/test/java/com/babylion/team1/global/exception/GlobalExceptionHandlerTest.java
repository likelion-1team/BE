package com.babylion.team1.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.user.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예외를 일부러 던지는 테스트 전용 컨트롤러로 GlobalExceptionHandler의 응답을 검증한다.
 * 웹 계층만 띄우므로 DB 없이 실행된다.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandlerTest.TestController.class, GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    /** BearerAuthenticationConfig(WebMvcConfigurer)가 의존하므로 컨텍스트 로딩용으로 등록한다. */
    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("CustomException은 ErrorCode의 상태 코드와 메시지로 응답한다")
    void customException() throws Exception {
        mockMvc.perform(get("/test/custom"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_REQUEST.getMessage()));
    }

    @Test
    @DisplayName("CustomException에 넘긴 메시지가 응답 메시지로 쓰인다")
    void customExceptionWithMessage() throws Exception {
        mockMvc.perform(get("/test/custom-message"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("상세 메시지"));
    }

    @Test
    @DisplayName("@RequestBody 검증 실패 시 400과 필드별 에러를 응답한다")
    void requestBodyValidation() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"age\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].reason").exists());
    }

    @Test
    @DisplayName("@RequestParam 제약 조건 위반 시 400과 파라미터 이름을 응답한다")
    void requestParamValidation() throws Exception {
        mockMvc.perform(get("/test/param").param("age", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("age"));
    }

    @Test
    @DisplayName("JSON 형식이 잘못되면 INVALID_JSON으로 응답한다")
    void invalidJson() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }

    @Test
    @DisplayName("PathVariable 타입이 맞지 않으면 INVALID_TYPE으로 응답한다")
    void typeMismatch() throws Exception {
        mockMvc.perform(get("/test/type/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TYPE"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @Test
    @DisplayName("필수 파라미터가 없으면 MISSING_PARAMETER로 응답한다")
    void missingParameter() throws Exception {
        mockMvc.perform(get("/test/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405로 응답한다")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(delete("/test/custom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 415로 응답한다")
    void unsupportedMediaType() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("존재하지 않는 경로는 404로 응답한다")
    void notFound() throws Exception {
        mockMvc.perform(get("/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("처리되지 않은 예외는 500으로 응답하고 내부 메시지를 노출하지 않는다")
    void unhandledException() throws Exception {
        mockMvc.perform(get("/test/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }

    record TestRequest(
            @NotBlank String name,
            @Min(1) Integer age
    ) {
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/custom")
        void custom() {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        @GetMapping("/custom-message")
        void customMessage() {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "상세 메시지");
        }

        @PostMapping("/body")
        void body(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/param")
        void param(@RequestParam @Min(1) int age) {
        }

        @GetMapping("/type/{id}")
        void type(@PathVariable Long id) {
        }

        @GetMapping("/required")
        void required(@RequestParam String name) {
        }

        @GetMapping("/error")
        void error() {
            throw new IllegalStateException("내부 정보가 담긴 메시지");
        }
    }
}
