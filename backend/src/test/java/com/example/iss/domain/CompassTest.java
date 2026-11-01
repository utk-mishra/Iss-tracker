package com.example.iss.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compass.fromAzimuthDeg: 16-point compass fallback (PROMPT.md section 5).
 * dirs[round(az/22.5) % 16], normalised first.
 */
class CompassTest {

    @Nested
    @DisplayName("16-point compass names")
    class SixteenPointNames {

        @ParameterizedTest(name = "given azimuth {0} degrees, when resolved, then compass is {1}")
        @CsvSource({
                "0, N",
                "22.5, NNE",
                "45, NE",
                "67.5, ENE",
                "90, E",
                "112.5, ESE",
                "135, SE",
                "157.5, SSE",
                "180, S",
                "202.5, SSW",
                "225, SW",
                "247.5, WSW",
                "270, W",
                "292.5, WNW",
                "315, NW",
                "337.5, NNW"
        })
        void givenCardinalAzimuth_whenResolved_thenReturnsExpectedCompass(double azimuthDeg, String expected) {
            assertThat(Compass.fromAzimuthDeg(azimuthDeg)).isEqualTo(Compass.valueOf(expected));
        }
    }

    @Nested
    @DisplayName("boundaries around 11.25 degrees (half-sector width)")
    class HalfSectorBoundaries {

        @Test
        @DisplayName("given azimuth 11.24, when resolved, then compass is N")
        void given11_24_whenResolved_thenN() {
            assertThat(Compass.fromAzimuthDeg(11.24)).isEqualTo(Compass.N);
        }

        @Test
        @DisplayName("given azimuth exactly 11.25, when resolved, then compass is NNE")
        void given11_25_whenResolved_thenNNE() {
            assertThat(Compass.fromAzimuthDeg(11.25)).isEqualTo(Compass.NNE);
        }

        @Test
        @DisplayName("given azimuth 11.26, when resolved, then compass is NNE")
        void given11_26_whenResolved_thenNNE() {
            assertThat(Compass.fromAzimuthDeg(11.26)).isEqualTo(Compass.NNE);
        }

        @Test
        @DisplayName("given azimuth 33.74, when resolved, then compass is NNE")
        void given33_74_whenResolved_thenNNE() {
            assertThat(Compass.fromAzimuthDeg(33.74)).isEqualTo(Compass.NNE);
        }

        @Test
        @DisplayName("given azimuth exactly 33.75, when resolved, then compass is NE")
        void given33_75_whenResolved_thenNE() {
            assertThat(Compass.fromAzimuthDeg(33.75)).isEqualTo(Compass.NE);
        }
    }

    @Nested
    @DisplayName("azimuth normalisation")
    class Normalisation {

        @Test
        @DisplayName("given azimuth exactly 360, when resolved, then compass is N")
        void given360_whenResolved_thenN() {
            assertThat(Compass.fromAzimuthDeg(360)).isEqualTo(Compass.N);
        }

        @Test
        @DisplayName("given azimuth -10, when resolved, then compass is N")
        void givenNegative10_whenResolved_thenN() {
            assertThat(Compass.fromAzimuthDeg(-10)).isEqualTo(Compass.N);
        }

        @Test
        @DisplayName("given azimuth 370, when resolved, then compass is N")
        void given370_whenResolved_thenN() {
            assertThat(Compass.fromAzimuthDeg(370)).isEqualTo(Compass.N);
        }
    }

    @Nested
    @DisplayName("invalid input")
    class InvalidInput {

        @Test
        @DisplayName("given a NaN azimuth, when resolved, then it does not crash and returns N")
        void givenNaNAzimuth_whenResolved_thenDoesNotCrashAndReturnsN() {
            Compass result = Compass.fromAzimuthDeg(Double.NaN);

            assertThat(result).isEqualTo(Compass.N);
        }
    }
}
