package autopilot.core;

import autopilot.core.AdaptiveCruise.Decision;
import autopilot.core.AdaptiveCruise.Mode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class AdaptiveCruiseTest {

    private static final double TARGET_SPEED_KMH = 100.0;
    private static final double SPEED_KMH = 80.0;
    private static final double BRAKING_DISTANCE_METERS = 30.0;
    private static final double EMERGENCY_DISTANCE_METERS = 10.0;
    private static final double RELEASE_DISTANCE_METERS = 48.0;
    private static final double HYSTERESIS_BAND_DISTANCE_METERS = 44.0;
    private static final double VALID_SPEED_KMH = 80.0;
    private static final double VALID_DISTANCE_METERS = 100.0;
    private static final double AMOUNT_TOLERANCE = 1e-9;

    private AdaptiveCruise adaptiveCruise;

    @BeforeEach
    void setUp() {
        adaptiveCruise = new AdaptiveCruise(TARGET_SPEED_KMH);
    }

    @ParameterizedTest(name = "[{index}] {arguments}")
    @CsvSource(useHeadersInDisplayName = true, textBlock = """
            speedKmh, expectedSafeDistanceMeters
            0.0,      0.0
            80.0,     40.0
            37.0,     18.5
            """)
    void safeDistanceFor_ValidSpeed_ReturnsHalfOfSpeedInMeters(double speedKmh, double expectedSafeDistanceMeters) {
        assertEquals(expectedSafeDistanceMeters, adaptiveCruise.safeDistanceFor(speedKmh));
    }

    @ParameterizedTest(name = "speed = {0} km/h")
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void safeDistanceFor_InvalidSpeed_ThrowsIllegalArgumentException(double invalidSpeedKmh) {
        assertThrows(IllegalArgumentException.class, () -> adaptiveCruise.safeDistanceFor(invalidSpeedKmh));
    }

    @ParameterizedTest(name = "{0} km/h, {1} m -> {2}")
    @MethodSource("decisionsWithoutPriorBraking")
    void update_NotPreviouslyBraking_ReturnsDecisionForDistanceAndSpeedZone(double speedKmh, double distanceMeters,
                                                                           Decision expectedDecision) {
        Decision decision = adaptiveCruise.update(speedKmh, distanceMeters);

        assertDecisionEquals(expectedDecision, decision);
    }

    static Stream<Arguments> decisionsWithoutPriorBraking() {
        return Stream.of(
                arguments(80.0, 0.0, new Decision(Mode.EMERGENCY_BRAKE, 100.0)),
                arguments(80.0, 20.0, new Decision(Mode.EMERGENCY_BRAKE, 100.0)),
                arguments(80.0, 21.0, new Decision(Mode.BRAKE, 47.5)),
                arguments(80.0, 30.0, new Decision(Mode.BRAKE, 25.0)),
                arguments(80.0, 40.0, new Decision(Mode.HOLD, 0.0)),
                arguments(80.0, 41.0, new Decision(Mode.ACCELERATE, 5.0)),
                arguments(95.0, 100.0, new Decision(Mode.ACCELERATE, 5.0)),
                arguments(98.0, 100.0, new Decision(Mode.ACCELERATE, 2.0)),
                arguments(100.0, 100.0, new Decision(Mode.HOLD, 0.0)),
                arguments(110.0, 100.0, new Decision(Mode.HOLD, 0.0)),
                arguments(0.0, 0.0, new Decision(Mode.EMERGENCY_BRAKE, 100.0)),
                arguments(0.0, 10.0, new Decision(Mode.ACCELERATE, 5.0))
        );
    }

    @ParameterizedTest(name = "after {0} m, at {1} m -> {2}")
    @MethodSource("decisionsWhileBraking")
    void update_PreviouslyBraking_KeepsBrakingUntilReleaseDistance(double previousDistanceMeters,
                                                                  double distanceMeters,
                                                                  Decision expectedDecision) {
        adaptiveCruise.update(SPEED_KMH, previousDistanceMeters);

        Decision decision = adaptiveCruise.update(SPEED_KMH, distanceMeters);

        assertDecisionEquals(expectedDecision, decision);
    }

    static Stream<Arguments> decisionsWhileBraking() {
        return Stream.of(
                arguments(BRAKING_DISTANCE_METERS, 35.0, new Decision(Mode.BRAKE, 12.5)),
                arguments(BRAKING_DISTANCE_METERS, 40.0, new Decision(Mode.BRAKE, 10.0)),
                arguments(BRAKING_DISTANCE_METERS, 47.9, new Decision(Mode.BRAKE, 10.0)),
                arguments(BRAKING_DISTANCE_METERS, 48.0, new Decision(Mode.ACCELERATE, 5.0)),
                arguments(EMERGENCY_DISTANCE_METERS, 40.0, new Decision(Mode.BRAKE, 10.0))
        );
    }

    @Test
    void update_AfterReachingReleaseDistance_NoLongerBrakesInHysteresisBand() {
        adaptiveCruise.update(SPEED_KMH, BRAKING_DISTANCE_METERS);
        adaptiveCruise.update(SPEED_KMH, RELEASE_DISTANCE_METERS);

        Decision decision = adaptiveCruise.update(SPEED_KMH, HYSTERESIS_BAND_DISTANCE_METERS);

        assertEquals(Mode.ACCELERATE, decision.mode());
    }

    @ParameterizedTest(name = "speed = {0} km/h")
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void update_InvalidSpeed_ThrowsIllegalArgumentException(double invalidSpeedKmh) {
        assertThrows(IllegalArgumentException.class,
                () -> adaptiveCruise.update(invalidSpeedKmh, VALID_DISTANCE_METERS));
    }

    @ParameterizedTest(name = "distance = {0} m")
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void update_InvalidDistance_ThrowsIllegalArgumentException(double invalidDistanceMeters) {
        assertThrows(IllegalArgumentException.class,
                () -> adaptiveCruise.update(VALID_SPEED_KMH, invalidDistanceMeters));
    }

    private static void assertDecisionEquals(Decision expected, Decision actual) {
        assertEquals(expected.mode(), actual.mode(), () -> "mode of " + actual);
        assertEquals(expected.amount(), actual.amount(), AMOUNT_TOLERANCE, () -> "amount of " + actual);
    }
}
