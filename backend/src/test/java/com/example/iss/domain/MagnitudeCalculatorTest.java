package com.example.iss.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MagnitudeCalculator: elevation-band approximation (PROMPT.md section 5).
 * >=80 -> -3.5; >=40 -> -2.5; >=20 -> -1.5; else -0.5.
 */
class MagnitudeCalculatorTest {

    @Nested
    @DisplayName("80 degree boundary")
    class EightyDegreeBoundary {

        @Test
        @DisplayName("given max elevation exactly 80, when calculated, then magnitude is -3.5")
        void given80_whenCalculated_thenMinus3_5() {
            assertThat(MagnitudeCalculator.fromMaxElevationDeg(80)).isEqualTo(-3.5);
        }

        @Test
        @DisplayName("given max elevation 79.9, when calculated, then magnitude is -2.5")
        void given79_9_whenCalculated_thenMinus2_5() {
            assertThat(MagnitudeCalculator.fromMaxElevationDeg(79.9)).isEqualTo(-2.5);
        }
    }

    @Nested
    @DisplayName("40 degree boundary")
    class FortyDegreeBoundary {

        @Test
        @DisplayName("given max elevation exactly 40, when calculated, then magnitude is -2.5")
        void given40_whenCalculated_thenMinus2_5() {
            assertThat(MagnitudeCalculator.fromMaxElevationDeg(40)).isEqualTo(-2.5);
        }

        @Test
        @DisplayName("given max elevation 39.9, when calculated, then magnitude is -1.5")
        void given39_9_whenCalculated_thenMinus1_5() {
            assertThat(MagnitudeCalculator.fromMaxElevationDeg(39.9)).isEqualTo(-1.5);
        }
    }

    @Nested
    @DisplayName("20 degree boundary")
    class TwentyDegreeBoundary {

        @Test
        @DisplayName("given max elevation exactly 20, when calculated, then magnitude is -1.5")
        void given20_whenCalculated_thenMinus1_5() {
            assertThat(MagnitudeCalculator.fromMaxElevationDeg(20)).isEqualTo(-1.5);
        }

        @Test
        @DisplayName("given max elevation 19.9, when calculated, then magnitude is -0.5")
        void given19_9_whenCalculated_thenMinus0_5() {
            assertThat(MagnitudeCalculator.fromMaxElevationDeg(19.9)).isEqualTo(-0.5);
        }
    }
}
