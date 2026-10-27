package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SteeringControllerTest {

    private static final double LOW_SPEED_KMH = 50.0;
    private static final double HIGH_SPEED_KMH = 130.0;
    private static final double HIGH_SPEED_LIMIT_DEGREES = 90.0;
    private static final double MAX_CHANGE_PER_TICK_DEGREES = 60.0;
    private static final double VALID_ANGLE_DEGREES = 30.0;
    private static final double WIDE_TURN_DEGREES = 300.0;
    private static final int MAX_TICKS_TO_REACH_ANY_ANGLE = 16;

    private SteeringController steeringController;

    @BeforeEach
    void setUp() {
        steeringController = new SteeringController();
    }

    @ParameterizedTest(name = "[{index}] {arguments}")
    @CsvSource(useHeadersInDisplayName = true, textBlock = """
            requestedAngle, speedKmh, expectedTargetAngle
            100.0,          50.0,     100.0
            450.0,          50.0,     450.0
            451.0,          50.0,     450.0
            -451.0,         50.0,     -450.0
            100.0,          120.0,    100.0
            90.0,           120.1,    90.0
            100.0,          120.1,    90.0
            -100.0,         120.1,    -90.0
            """)
    void update_RequestedAngleAtSpeed_ClampsTargetToSpeedDependentLimit(double requestedAngle, double speedKmh,
                                                                        double expectedTargetAngle) {
        steeringController.update(requestedAngle, speedKmh);

        assertEquals(expectedTargetAngle, steeringController.getTargetAngleDegrees());
    }

    @ParameterizedTest(name = "[{index}] {arguments}")
    @CsvSource(useHeadersInDisplayName = true, textBlock = """
            requestedAngle, expectedAngle
            30.0,           30.0
            60.0,           60.0
            61.0,           60.0
            -61.0,          -60.0
            """)
    void update_FromCenter_LimitsChangeToMaxStepPerTick(double requestedAngle, double expectedAngle) {
        double angle = steeringController.update(requestedAngle, LOW_SPEED_KMH);

        assertEquals(expectedAngle, angle);
    }

    @Test
    void update_TargetBeyondOneStep_ReachesTargetOverConsecutiveTicks() {
        double farTargetDegrees = 150.0;

        double[] angles = {
                steeringController.update(farTargetDegrees, LOW_SPEED_KMH),
                steeringController.update(farTargetDegrees, LOW_SPEED_KMH),
                steeringController.update(farTargetDegrees, LOW_SPEED_KMH)
        };

        double[] expectedAngles = {MAX_CHANGE_PER_TICK_DEGREES, 2 * MAX_CHANGE_PER_TICK_DEGREES, farTargetDegrees};
        assertArrayEquals(expectedAngles, angles);
    }

    @Test
    void update_HighSpeedWhileTurnedBeyondHighSpeedLimit_UnwindsByMaxStepInsteadOfJumping() {
        steerTo(WIDE_TURN_DEGREES);

        double angle = steeringController.update(WIDE_TURN_DEGREES, HIGH_SPEED_KMH);

        assertEquals(WIDE_TURN_DEGREES - MAX_CHANGE_PER_TICK_DEGREES, angle);
    }

    @Test
    void update_HighSpeedWhileTurnedBeyondHighSpeedLimit_EventuallySettlesAtHighSpeedLimit() {
        steerTo(WIDE_TURN_DEGREES);

        for (int tick = 0; tick < MAX_TICKS_TO_REACH_ANY_ANGLE; tick++) {
            steeringController.update(WIDE_TURN_DEGREES, HIGH_SPEED_KMH);
        }

        assertEquals(HIGH_SPEED_LIMIT_DEGREES, steeringController.getCurrentAngleDegrees());
    }

    @ParameterizedTest(name = "angle = {0}")
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void update_NonFiniteAngle_ThrowsIllegalArgumentException(double invalidAngleDegrees) {
        assertThrows(IllegalArgumentException.class,
                () -> steeringController.update(invalidAngleDegrees, LOW_SPEED_KMH));
    }

    @ParameterizedTest(name = "speed = {0} km/h")
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void update_InvalidSpeed_ThrowsIllegalArgumentException(double invalidSpeedKmh) {
        assertThrows(IllegalArgumentException.class,
                () -> steeringController.update(VALID_ANGLE_DEGREES, invalidSpeedKmh));
    }

    @ParameterizedTest(name = "[{index}] {arguments}")
    @CsvSource(useHeadersInDisplayName = true, textBlock = """
            startAngle, expectedAngle
            0.0,        0.0
            45.0,       0.0
            60.0,       0.0
            100.0,      40.0
            -100.0,     -40.0
            """)
    void selfCenter_FromSteeredAngle_MovesTowardCenterByAtMostMaxStep(double startAngle, double expectedAngle) {
        steerTo(startAngle);

        double angle = steeringController.selfCenter();

        assertEquals(expectedAngle, angle);
    }

    private void steerTo(double angleDegrees) {
        for (int tick = 0; tick < MAX_TICKS_TO_REACH_ANY_ANGLE; tick++) {
            steeringController.update(angleDegrees, LOW_SPEED_KMH);
        }
    }
}
