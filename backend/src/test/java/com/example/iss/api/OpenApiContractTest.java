package com.example.iss.api;

import com.example.iss.client.CloudClient;
import com.example.iss.client.PassesClient;
import com.example.iss.client.PositionClient;
import com.example.iss.domain.CloudForecast;
import com.example.iss.domain.IssPosition;
import com.example.iss.domain.Pass;
import com.example.iss.domain.PassFeed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Contract test (Step 6 addition f): loads openapi.yaml with SnakeYAML (an existing
 * transitive dependency of spring-boot-starter-web; pom.xml is NOT changed) and
 * asserts every field present in a real MockMvc /overview response also exists in the
 * OverviewResponse schema, so the hand-written spec cannot silently drift from reality.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PositionClient positionClient;
    @MockBean
    private PassesClient passesClient;
    @MockBean
    private CloudClient cloudClient;

    @BeforeEach
    void setUp() {
        // Uses real wall-clock time (not a fixed past Instant) because IssFacade's
        // production Clock bean is Clock.systemUTC(); a fixed past timestamp here
        // would make the stubbed pass's setTime already past real "now" and the
        // facade would correctly drop it (PROMPT.md section 5).
        Instant now = Instant.now();
        when(positionClient.fetch()).thenReturn(new IssPosition(10.0, 20.0, 408.0, 27000.0, now, "note"));
        Pass pass = new Pass(
                now.plusSeconds(3600), 10.0, "N",
                now.plusSeconds(3660), 85.0,
                now.plusSeconds(3900), 200.0, "S",
                300, true, now.plusSeconds(3660), now.plusSeconds(3900), 240
        );
        when(passesClient.fetch(anyDouble(), anyDouble())).thenReturn(new PassFeed(List.of(pass), 5.0, false));
        when(cloudClient.fetch(anyDouble(), anyDouble()))
                .thenReturn(new CloudForecast(List.of(now.plusSeconds(3660)), List.of(15)));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("given a real MockMvc overview response, when compared against openapi.yaml, then every response field exists in the OverviewResponse schema")
    void givenRealOverviewResponse_whenComparedAgainstSchema_thenEveryFieldExistsInSchema() throws Exception {
        Map<String, Object> openApiSpec;
        try (InputStream in = new ClassPathResource("openapi.yaml").getInputStream()) {
            openApiSpec = new Yaml().load(in);
        }
        Map<String, Object> schemas = (Map<String, Object>) ((Map<String, Object>) openApiSpec.get("components")).get("schemas");
        Set<String> overviewSchemaFields = schemaPropertyNames(schemas, "OverviewResponse");
        Set<String> positionSchemaFields = schemaPropertyNames(schemas, "PositionDto");
        Set<String> passSchemaFields = schemaPropertyNames(schemas, "PassDto");
        Set<String> metaSchemaFields = schemaPropertyNames(schemas, "MetaDto");
        Set<String> linksSchemaFields = schemaPropertyNames(schemas, "LinksDto");

        // Unique coordinates (not used by other @SpringBootTest classes) avoid colliding
        // with a cached result from another test class, since Spring caches and reuses
        // the ApplicationContext (and therefore the IssFacade's TtlCache) across test
        // classes with identical configuration.
        String responseJson = mockMvc.perform(get("/api/v1/iss/overview").param("lat", "12.3456").param("lon", "65.4321"))
                .andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseJson);

        assertFieldsSubsetOfSchema(response, overviewSchemaFields, "overview response");
        assertFieldsSubsetOfSchema(response.get("position"), positionSchemaFields, "position");
        assertThat(response.get("passes")).hasSizeGreaterThan(0);
        assertFieldsSubsetOfSchema(response.get("passes").get(0), passSchemaFields, "pass");
        assertFieldsSubsetOfSchema(response.get("meta"), metaSchemaFields, "meta");
        assertFieldsSubsetOfSchema(response.get("_links"), linksSchemaFields, "_links");
    }

    @SuppressWarnings("unchecked")
    private Set<String> schemaPropertyNames(Map<String, Object> schemas, String schemaName) {
        Map<String, Object> schema = (Map<String, Object>) schemas.get(schemaName);
        assertThat(schema).as("schema " + schemaName + " must exist in openapi.yaml").isNotNull();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        return properties.keySet();
    }

    private void assertFieldsSubsetOfSchema(JsonNode node, Set<String> schemaFields, String label) {
        node.fieldNames().forEachRemaining(fieldName ->
                assertThat(schemaFields)
                        .as("field '%s' in %s must be declared in the openapi.yaml schema", fieldName, label)
                        .contains(fieldName));
    }
}
