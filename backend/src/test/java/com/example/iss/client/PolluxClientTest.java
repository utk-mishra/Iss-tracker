package com.example.iss.client;

import com.example.iss.domain.Pass;
import com.example.iss.domain.PassFeed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PolluxClient: static pure parse(JsonNode) against the REAL fixtures
 * (pollux-passes-default.json, pollux-passes-n20.json). Negative-path rules (malformed
 * pass skipped, missing window fields) are tested via in-memory corrupted clones of
 * those real fixtures, per PROMPT.md section 3.2 and the approved Step 5 plan.
 */
class PolluxClientTest {

    private JsonNode defaultFixture;
    private JsonNode n20Fixture;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        defaultFixture = mapper.readTree(new File("src/test/resources/fixtures/pollux-passes-default.json"));
        n20Fixture = mapper.readTree(new File("src/test/resources/fixtures/pollux-passes-n20.json"));
    }

    @Nested
    @DisplayName("parse() against the real fixtures")
    class ParseRealFixtures {

        @Test
        @DisplayName("given the default fixture (5 passes), when parsed, then 5 passes are returned")
        void givenDefaultFixture_whenParsed_thenFivePasses() {
            PassFeed feed = PolluxClient.parse(defaultFixture);

            assertThat(feed.passes()).hasSize(5);
        }

        @Test
        @DisplayName("given the n20 fixture (20 passes), when parsed, then 20 passes are returned")
        void givenN20Fixture_whenParsed_thenTwentyPasses() {
            PassFeed feed = PolluxClient.parse(n20Fixture);

            assertThat(feed.passes()).hasSize(20);
        }

        @Test
        @DisplayName("given the n20 fixture, when parsed, then tle_age_hours and tle_stale are surfaced")
        void givenN20Fixture_whenParsed_thenTleMetadataSurfaced() {
            PassFeed feed = PolluxClient.parse(n20Fixture);

            assertThat(feed.tleAgeHours()).isEqualTo(7.4);
            assertThat(feed.tleStale()).isFalse();
        }

        @Test
        @DisplayName("given the n20 fixture, when parsed, then rise/set directions are the API's own compass strings")
        void givenN20Fixture_whenParsed_thenDirectionsAreApiStrings() {
            PassFeed feed = PolluxClient.parse(n20Fixture);

            Pass firstPass = feed.passes().get(0);
            assertThat(firstPass.riseDirection()).isEqualTo("SSW");
            assertThat(firstPass.setDirection()).isEqualTo("ENE");
        }

        @Test
        @DisplayName("given the real zero-duration visible pass in the n20 fixture, when parsed, then it is not observable")
        void givenRealZeroDurationPass_whenParsed_thenNotObservable() {
            PassFeed feed = PolluxClient.parse(n20Fixture);

            Pass zeroDurationPass = feed.passes().stream()
                    .filter(p -> p.visible() && p.visibleDurationSec() == 0)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected a real zero-duration visible pass in the fixture"));

            assertThat(zeroDurationPass.isObservable()).isFalse();
        }
    }

    @Nested
    @DisplayName("malformed pass handling (corrupted clone of the real fixture)")
    class MalformedPassHandling {

        @Test
        @DisplayName("given one pass missing its rise time, when parsed, then that pass is skipped and the rest parse")
        void givenOnePassMissingRiseTime_whenParsed_thenSkippedAndRestParse() {
            JsonNode corrupted = n20Fixture.deepCopy();
            ArrayNode passesArray = (ArrayNode) corrupted.get("passes");
            ObjectNode firstPass = (ObjectNode) passesArray.get(0);
            ((ObjectNode) firstPass.get("rise")).remove("time");

            PassFeed feed = PolluxClient.parse(corrupted);

            assertThat(feed.passes()).hasSize(19);
        }
    }

    @Nested
    @DisplayName("visible pass with missing window fields (corrupted clone of the real fixture)")
    class VisiblePassMissingWindowFields {

        @Test
        @DisplayName("given a visible pass whose visible_start/end/duration are missing, when parsed, then it is kept but not observable")
        void givenVisiblePassMissingWindowFields_whenParsed_thenKeptButNotObservable() {
            JsonNode corrupted = n20Fixture.deepCopy();
            ArrayNode passesArray = (ArrayNode) corrupted.get("passes");
            ObjectNode visiblePassNode = null;
            for (JsonNode node : passesArray) {
                if (node.path("visible").asBoolean(false)) {
                    visiblePassNode = (ObjectNode) node;
                    break;
                }
            }
            assertThat(visiblePassNode).as("a visible pass must exist in the fixture").isNotNull();
            visiblePassNode.remove("visible_start");
            visiblePassNode.remove("visible_end");
            visiblePassNode.remove("visible_duration_sec");

            PassFeed feed = PolluxClient.parse(corrupted);

            assertThat(feed.passes()).hasSize(20);
            Pass patchedPass = feed.passes().stream()
                    .filter(p -> p.visible() && p.visibleStart() == null)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected the patched visible pass to still be present"));
            assertThat(patchedPass.isObservable()).isFalse();
            assertThat(patchedPass.visibleDurationSec()).isZero();
        }
    }

    @Nested
    @DisplayName("compass fallback when the API string is missing")
    class CompassFallbackWhenMissing {

        @Test
        @DisplayName("given a pass whose rise.compass is removed, when parsed, then riseDirection falls back to a Compass-derived value from azimuth")
        void givenRiseCompassRemoved_whenParsed_thenFallsBackToComputedCompass() {
            JsonNode corrupted = n20Fixture.deepCopy();
            ArrayNode passesArray = (ArrayNode) corrupted.get("passes");
            ObjectNode firstPass = (ObjectNode) passesArray.get(0);
            double azimuth = firstPass.get("rise").get("azimuth_deg").asDouble();
            ((ObjectNode) firstPass.get("rise")).remove("compass");

            PassFeed feed = PolluxClient.parse(corrupted);

            String expectedFallback = com.example.iss.domain.Compass.fromAzimuthDeg(azimuth).name();
            assertThat(feed.passes().get(0).riseDirection()).isEqualTo(expectedFallback);
        }
    }

    @Nested
    @DisplayName("URL building")
    class UrlBuilding {

        @Test
        @DisplayName("given lat/lon/pass count, when the URL is built, then it always includes n and uses locale-independent decimal formatting")
        void givenLatLonPassCount_whenUrlBuilt_thenIncludesNAndLocaleIndependentFormatting() {
            PolluxClient client = new PolluxClient(new HttpJson(), "https://iss-api.polluxlabs.io/iss-pass", 20);

            String url = client.buildUrl(28.6139, 77.2090);

            assertThat(url).contains("n=20");
            assertThat(url).contains("lat=28.6139");
            assertThat(url).contains("lon=77.2090");
            assertThat(url).doesNotContain(",");
        }

        @Test
        @DisplayName("given the default system locale uses comma decimals, when the URL is built, then it still uses a dot")
        void givenCommaDecimalLocale_whenUrlBuilt_thenStillUsesDot() {
            Locale original = Locale.getDefault();
            try {
                Locale.setDefault(Locale.GERMANY);
                PolluxClient client = new PolluxClient(new HttpJson(), "https://iss-api.polluxlabs.io/iss-pass", 20);

                String url = client.buildUrl(28.6139, 77.2090);

                assertThat(url).contains("lat=28.6139");
                assertThat(url).doesNotContain("28,6139");
            } finally {
                Locale.setDefault(original);
            }
        }
    }
}
