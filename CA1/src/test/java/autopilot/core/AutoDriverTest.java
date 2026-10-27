package autopilot.core;

import autopilot.exception.SensorFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoDriverTest {

    private static final GearBox.Gear DRIVE = GearBox.Gear.D;
    private static final double TARGET_SPEED_KMH = 80.0;
    private static final double CLEAR_ROAD_DISTANCE_METERS = 100.0;
    private static final double CENTERED_LANE_OFFSET_METERS = 0.0;
    private static final double DRIFTED_LANE_OFFSET_METERS = 1.0;
    private static final double CENTERED_STEERING_DEGREES = 0.0;
    private static final double NO_BRAKE_PRESSURE = 0.0;
    private static final double SAFE_MODE_BRAKE_PRESSURE = 30.0;

    private enum Sensor { SPEED, OBSTACLE }

    private double speedKmh;
    private double obstacleDistanceMeters;
    private double laneOffsetMeters;
    private Sensor failedSensor;

    private AutoDriver autoDriver;

    @BeforeEach
    void setUp() {
        speedKmh = TARGET_SPEED_KMH;
        obstacleDistanceMeters = CLEAR_ROAD_DISTANCE_METERS;
        laneOffsetMeters = CENTERED_LANE_OFFSET_METERS;
        failedSensor = null;

        autoDriver = new AutoDriver(
                () -> read(Sensor.SPEED, speedKmh),
                () -> read(Sensor.OBSTACLE, obstacleDistanceMeters),
                () -> laneOffsetMeters,
                TARGET_SPEED_KMH);
    }

    @Test
    void tick_AllSensorsHealthy_DoesNotEnterSafeMode() {
        AutoDriver.DriveResult result = autoDriver.tick(DRIVE);

        assertFalse(result.safeMode());
    }

    @Test
    void tick_ClearRoadAtTargetSpeed_HoldsSpeed() {
        AutoDriver.DriveResult result = autoDriver.tick(DRIVE);

        assertEquals(AdaptiveCruise.Mode.HOLD, result.cruiseMode());
    }

    @Test
    void tick_ClearRoadAtTargetSpeed_AppliesNoBrakePressure() {
        AutoDriver.DriveResult result = autoDriver.tick(DRIVE);

        assertEquals(NO_BRAKE_PRESSURE, result.brakePressure());
    }

    @ParameterizedTest(name = "[{index}] {arguments}")
    @CsvSource(useHeadersInDisplayName = true, textBlock = """
            laneOffsetMeters, expectedSteeringDegrees
            1.0,              -45.0
            -1.0,             45.0
            0.5,              0.0
            -0.5,             0.0
            """)
    void tick_LaneOffset_SteersTowardLaneCenterOnlyBeyondThreshold(double offsetMeters,
                                                                   double expectedSteeringDegrees) {
        laneOffsetMeters = offsetMeters;

        AutoDriver.DriveResult result = autoDriver.tick(DRIVE);

        assertEquals(expectedSteeringDegrees, result.steeringAngleDegrees());
    }

    @ParameterizedTest(name = "{0} sensor fails")
    @EnumSource(Sensor.class)
    void tick_SensorFails_EntersSafeMode(Sensor sensor) {
        failedSensor = sensor;

        AutoDriver.DriveResult result = autoDriver.tick(DRIVE);

        assertTrue(result.safeMode());
    }

    @ParameterizedTest(name = "{0} sensor fails")
    @EnumSource(Sensor.class)
    void tick_SensorFails_AppliesGentleBrakePressure(Sensor sensor) {
        failedSensor = sensor;

        AutoDriver.DriveResult result = autoDriver.tick(DRIVE);

        assertEquals(SAFE_MODE_BRAKE_PRESSURE, result.brakePressure());
    }

    @ParameterizedTest(name = "{0} sensor fails")
    @EnumSource(Sensor.class)
    void tick_SensorFails_ReportsBrakeCruiseMode(Sensor sensor) {
        failedSensor = sensor;

        AutoDriver.DriveResult result = autoDriver.tick(DRIVE);

        assertEquals(AdaptiveCruise.Mode.BRAKE, result.cruiseMode());
    }

    @ParameterizedTest(name = "{0} sensor fails")
    @EnumSource(Sensor.class)
    void tick_SensorFailsWhileSteering_CentersSteering(Sensor sensor) {
        laneOffsetMeters = DRIFTED_LANE_OFFSET_METERS;
        autoDriver.tick(DRIVE);
        failedSensor = sensor;

        AutoDriver.DriveResult result = autoDriver.tick(DRIVE);

        assertEquals(CENTERED_STEERING_DEGREES, result.steeringAngleDegrees());
    }

    private double read(Sensor sensor, double reading) {
        if (sensor == failedSensor) {
            throw new SensorFailureException(sensor + " sensor failed");
        }
        return reading;
    }
}
