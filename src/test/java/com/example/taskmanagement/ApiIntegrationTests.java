package com.example.taskmanagement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end behaviour of the HTTP contract: authentication, tenant isolation,
 * optimistic locking, validation and error shapes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTests {

    @Autowired
    private MockMvc mvc;

    // ---------- authentication ----------

    @Test
    void apiDocsArePublicAndRootOpensThem() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Task Management API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui.html"));
    }

    @Test
    void requestWithoutTokenGets401() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void tamperedTokenGets401() throws Exception {
        String token = register();
        String[] parts = token.split("\\.");
        // Change the payload but keep the original signature.
        String forged = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA." + parts[2];
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenGets401NotServerError() throws Exception {
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordGets401() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotCallAdminApi() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(register())))
                .andExpect(status().isForbidden());
    }

    @Test
    void userResponsesNeverContainPasswordHashes() throws Exception {
        String me = mvc.perform(get("/api/users/me").header("Authorization", bearer(register())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(me).doesNotContain("password");

        String admin = login("admin@example.com", "admin123");
        String users = mvc.perform(get("/api/admin/users").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(users).contains("admin@example.com").doesNotContain("password");
    }

    // ---------- tenant isolation ----------

    @Test
    void anotherTenantsTaskIsNotFoundNotForbidden() throws Exception {
        String alice = register();
        String bob = register();
        String taskId = createTask(alice, createProject(alice));

        mvc.perform(get("/api/tasks/" + taskId).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/tasks/" + taskId).header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("Hijack", null, 0L)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/tasks/" + taskId).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());

        // Same answer as for a task that doesn't exist at all.
        mvc.perform(get("/api/tasks/" + UUID.randomUUID()).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotAddTasksToAnotherTenantsProject() throws Exception {
        String alice = register();
        String bob = register();
        String aliceProject = createProject(alice);
        mvc.perform(post("/api/tasks").header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sneaky\",\"projectId\":\"" + aliceProject + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listingOnlyReturnsCallersTasks() throws Exception {
        String alice = register();
        String bob = register();
        createTask(alice, createProject(alice));
        mvc.perform(get("/api/tasks").header("Authorization", bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
        mvc.perform(get("/api/tasks").header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    // ---------- optimistic locking ----------

    @Test
    void staleVersionGets409() throws Exception {
        String alice = register();
        String taskId = createTask(alice, createProject(alice));

        update(alice, taskId, 0L).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        // A second client still holding version 0 must not overwrite the first client's change.
        update(alice, taskId, 0L).andExpect(status().isConflict());
    }

    @Test
    void updateWithoutVersionIsRejected() throws Exception {
        String alice = register();
        String taskId = createTask(alice, createProject(alice));
        update(alice, taskId, null).andExpect(status().isBadRequest());
    }

    // ---------- validation & error shapes ----------

    @Test
    void invalidPayloadGets400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/tasks").header("Authorization", bearer(register()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.title").exists())
                .andExpect(jsonPath("$.data.projectId").exists());
    }

    @Test
    void malformedJsonGets400() throws Exception {
        mvc.perform(post("/api/tasks").header("Authorization", bearer(register()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownSortFieldGets400AndSortWithoutDirectionWorks() throws Exception {
        String token = register();
        mvc.perform(get("/api/tasks?sort=password,asc").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks?sort=title").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void nonUuidIdGets400() throws Exception {
        mvc.perform(get("/api/tasks/42").header("Authorization", bearer(register())))
                .andExpect(status().isBadRequest());
    }

    // ---------- operations ----------

    @Test
    void healthIsPublicAndEveryResponseCarriesARequestId() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void wellFormedIncomingRequestIdIsEchoed() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Request-Id", "trace-12345678"))
                .andExpect(header().string("X-Request-Id", "trace-12345678"));
    }

    // ---------- helpers ----------

    private String register() throws Exception {
        return register(uniqueEmail());
    }

    private String register(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return field(body, "token");
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return field(body, "token");
    }

    private String createProject(String token) throws Exception {
        String body = mvc.perform(post("/api/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Payments revamp\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return field(body, "id");
    }

    private String createTask(String token, String projectId) throws Exception {
        String body = mvc.perform(post("/api/tasks").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Add Redis cache\",\"projectId\":\"" + projectId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return field(body, "id");
    }

    private ResultActions update(String token, String taskId, Long version) throws Exception {
        String projectId = field(mvc.perform(get("/api/tasks/" + taskId).header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString(), "projectId");
        return mvc.perform(put("/api/tasks/" + taskId).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(taskJson("Cache FX rates", projectId, version)));
    }

    private static String taskJson(String title, String projectId, Long version) {
        String project = projectId == null ? UUID.randomUUID().toString() : projectId;
        return "{\"title\":\"" + title + "\",\"projectId\":\"" + project + "\""
                + (version == null ? "" : ",\"version\":" + version) + "}";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertThat(m.find()).as("field %s in %s", name, json).isTrue();
        return m.group(1);
    }
}
