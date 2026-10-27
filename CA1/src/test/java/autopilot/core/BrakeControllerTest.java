package autopilot.core;

import autopilot.core.GearBox.Gear;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrakeControllerTest {

    private static final Gear DRIVE = Gear.D;
    private static final double MAX_PRESSURE = 100.0;
    private static final double ABS_HIGH_PULSE = 90.0;
    private static final double ABS_LOW_PULSE = 60.0;
    private static final double ABS_TRIGGERING_PRESSURE = 95.0;
    private static final double ABS_TRIGGERING_SPEED_KMH = 50.0;
    private static final double REGULAR_PRESSURE = 40.0;
    private static final double VALID_SPEED_KMH = 50.0;
    private static final double VALID_PRESSURE = 40.0;

    private BrakeController brakeController;

    @BeforeEach
    void setUp() {
        brakeController = new BrakeController();
    }

    @ParameterizedTest(name = "[{index}] {arguments}")
    @CsvSource(useHeadersInDisplayName = true, textBlock = """
            requestedPressure, speedKmh, expectedPressure
            0.0,               50.0,     0.0
            40.0,              50.0,     40.0
            80.0,              50.0,     80.0
            80.1,              50.0,     90.0
            100.0,             30.0,     100.0
            100.0,             30.1,     90.0
            150.0,             20.0,     100.0
            """)
    void apply_SingleRequest_ReturnsBoundedOrAbsPressure(double requestedPressure, double speedKmh,
                                                         double expectedPressure) {
        double appliedPressure = brakeController.apply(requestedPressure, speedKmh, DRIVE);

        assertEquals(expectedPressure, appliedPressure);
    }

    @Test
    void apply_ConsecutiveAbsRequests_AlternatesHighAndLowPulses() {
        double[] pulses = {
                applyAbsTriggeringRequest(),
                applyAbsTriggeringRequest(),
                applyAbsTriggeringRequest()
        };

        assertArrayEquals(new double[]{ABS_HIGH_PULSE, ABS_LOW_PULSE, ABS_HIGH_PULSE}, pulses);
    }

    @Test
    void apply_RegularRequestBetweenAbsRequests_RestartsAbsWithHighPulse() {
        applyAbsTriggeringRequest();
        brakeController.apply(REGULAR_PRESSURE, ABS_TRIGGERING_SPEED_KMH, DRIVE);

        double pulseAfterRegularBraking = applyAbsTriggeringRequest();

        assertEquals(ABS_HIGH_PULSE, pulseAfterRegularBraking);
    }

    @Test
    void apply_RegularRequest_DoesNotActivateWarning() {
        brakeController.apply(REGULAR_PRESSURE, VALID_SPEED_KMH, DRIVE);

        assertFalse(brakeController.isWarningActive());
    }

    @ParameterizedTest(name = "pressure = {0}")
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void apply_InvalidPressure_ThrowsIllegalArgumentException(double invalidPressure) {
        assertThrows(IllegalArgumentException.class,
                () -> brakeController.apply(invalidPressure, VALID_SPEED_KMH, DRIVE));
    }

    @ParameterizedTest(name = "speed = {0} km/h")
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void apply_InvalidSpeed_ThrowsIllegalArgumentException(double invalidSpeedKmh) {
        assertThrows(IllegalArgumentException.class,
                () -> brakeController.apply(VALID_PRESSURE, invalidSpeedKmh, DRIVE));
    }

    @Test
    void apply_GearIsPark_ThrowsIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> brakeController.apply(VALID_PRESSURE, VALID_SPEED_KMH, Gear.P));
    }

    @Test
    void apply_NullGear_ThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> brakeController.apply(VALID_PRESSURE, VALID_SPEED_KMH, null));
    }

    @ParameterizedTest(name = "gear = {0}")
    @EnumSource(value = Gear.class, mode = EnumSource.Mode.EXCLUDE, names = "P")
    void emergencyBrake_NonParkGear_AppliesMaximumPressure(Gear gear) {
        brakeController.emergencyBrake(gear);

        assertEquals(MAX_PRESSURE, brakeController.getPressure());
    }

    @Test
    void emergencyBrake_NonParkGear_ActivatesWarning() {
        brakeController.emergencyBrake(DRIVE);

        assertTrue(brakeController.isWarningActive());
    }

    @Test
    void emergencyBrake_DuringAbsPulsing_RestartsAbsWithHighPulse() {
        applyAbsTriggeringRequest();
        brakeController.emergencyBrake(DRIVE);

        double pulseAfterEmergency = applyAbsTriggeringRequest();

        assertEquals(ABS_HIGH_PULSE, pulseAfterEmergency);
    }

    @Test
    void emergencyBrake_GearIsPark_ThrowsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> brakeController.emergencyBrake(Gear.P));
    }

    @Test
    void emergencyBrake_NullGear_ThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> brakeController.emergencyBrake(null));
    }

    private double applyAbsTriggeringRequest() {
        return brakeController.apply(ABS_TRIGGERING_PRESSURE, ABS_TRIGGERING_SPEED_KMH, DRIVE);
    }
}
