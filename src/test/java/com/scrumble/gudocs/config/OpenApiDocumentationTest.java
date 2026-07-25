package com.scrumble.gudocs.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode fetchApiDocs() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void CurrentUserId는_요청_파라미터로_노출되지_않는다() throws Exception {
        JsonNode paths = fetchApiDocs().get("paths");

        paths.forEach(pathItem -> pathItem.forEach(operation -> {
            JsonNode parameters = operation.get("parameters");
            if (parameters != null) {
                parameters.forEach(param ->
                        assertThat(param.get("name").asText())
                                .as("userId는 세션 주입 값이라 파라미터로 노출되면 안 됨")
                                .isNotEqualTo("userId"));
            }
        }));
    }

    @Test
    void 주요_요청_DTO는_requestBody로_노출된다() throws Exception {
        JsonNode paths = fetchApiDocs().get("paths");

        assertThat(paths.path("/api/subscriptions").path("post").has("requestBody")).isTrue();
        assertThat(paths.path("/api/subscriptions/{subscriptionId}").path("put").has("requestBody")).isTrue();
        assertThat(paths.path("/api/subscriptions/{subscriptionId}/status").path("put").has("requestBody")).isTrue();
        assertThat(paths.path("/api/users/me/name").path("put").has("requestBody")).isTrue();
    }

    @Test
    void 내_정보_조회는_auth_me로만_노출된다() throws Exception {
        JsonNode paths = fetchApiDocs().get("paths");

        assertThat(paths.path("/api/auth/me").has("get")).isTrue();
        assertThat(paths.path("/api/users/me").has("get")).isFalse();
    }
}
