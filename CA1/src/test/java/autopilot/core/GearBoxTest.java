package autopilot.core;

import autopilot.core.GearBox.Gear;
import autopilot.exception.IllegalGearChangeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GearBoxTest {

    private static final double STATIONARY_KMH = 0.0;
    private static final double MOVING_KMH = 5.0;

    private double sensorSpeedKmh;
    private GearBox gearBox;

    @BeforeEach
    void setUp() {
        sensorSpeedKmh = STATIONARY_KMH;
        gearBox = new GearBox(() -> sensorSpeedKmh);
    }

    @ParameterizedTest(name = "{0} -> {1} at {2} km/h")
    @CsvFileSource(resources = "/gearbox/allowed-transitions.csv", numLinesToSkip = 1)
    void changeTo_AllowedTransition_SwitchesToTargetGear(Gear currentGear, Gear targetGear, double speedKmh) {
        shiftWhileStationaryTo(currentGear);
        sensorSpeedKmh = speedKmh;

        gearBox.changeTo(targetGear);

        assertEquals(targetGear, gearBox.getCurrentGear());
    }

    @ParameterizedTest(name = "{0} -> {1} at {2} km/h")
    @CsvFileSource(resources = "/gearbox/rejected-transitions.csv", numLinesToSkip = 1)
    void changeTo_RejectedTransition_ThrowsIllegalGearChangeException(Gear currentGear, Gear targetGear,
                                                                     double speedKmh) {
        shiftWhileStationaryTo(currentGear);
        sensorSpeedKmh = speedKmh;

        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(targetGear));
    }

    @Test
    void changeTo_RejectedTransition_KeepsCurrentGear() {
        sensorSpeedKmh = MOVING_KMH;

        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(Gear.R));

        assertEquals(Gear.P, gearBox.getCurrentGear());
    }

    @Test
    void changeTo_TargetIsCurrentGear_IgnoresSpeedReading() {
        sensorSpeedKmh = Double.NaN;

        assertDoesNotThrow(() -> gearBox.changeTo(Gear.P));
    }

    @ParameterizedTest(name = "speed = {0} km/h")
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void changeTo_InvalidSpeedReading_ThrowsIllegalArgumentException(double invalidSpeedKmh) {
        sensorSpeedKmh = invalidSpeedKmh;

        assertThrows(IllegalArgumentException.class, () -> gearBox.changeTo(Gear.R));
    }

    @Test
    void changeTo_NullTargetGear_ThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> gearBox.changeTo(null));
    }
    private void shiftWhileStationaryTo(Gear gear) {
        switch (gear) {
            case P -> { }
            case R -> gearBox.changeTo(Gear.R);
            case N -> {
                gearBox.changeTo(Gear.R);
                gearBox.changeTo(Gear.N);
            }
            case D -> {
                gearBox.changeTo(Gear.R);
                gearBox.changeTo(Gear.D);
            }
        }
    }
}
