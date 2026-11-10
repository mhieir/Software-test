package airplane;

import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;
import airplane.exceptions.FlightException;
import airplane.models.FlightStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AircraftTest {

    private Engine engine;
    private FuelSystem fuelSystem;
    private LandingGear landingGear;
    private NavigationSystem navigationSystem;
    private Aircraft aircraft;

    @BeforeEach
    void setUp() {
        engine = mock(Engine.class);
        fuelSystem = mock(FuelSystem.class);
        landingGear = mock(LandingGear.class);
        navigationSystem = mock(NavigationSystem.class);
        aircraft = new Aircraft(engine, fuelSystem, landingGear, navigationSystem);
    }

    private void stubAllSystemsReady() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);
    }

    private void bringAircraftIntoFlight() {
        stubAllSystemsReady();
        doNothing().when(engine).start();
        doNothing().when(landingGear).retract();
        aircraft.prepareForTakeoff();
        aircraft.takeOff();
    }

    @Test
    void newAircraftStartsGroundedWithoutTouchingComponents() {
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verifyNoInteractions(engine, fuelSystem, landingGear, navigationSystem);
    }

    @Test
    void constructorRejectsNullComponents() {
        assertThrows(NullPointerException.class,
                () -> new Aircraft(null, fuelSystem, landingGear, navigationSystem));
        assertThrows(NullPointerException.class,
                () -> new Aircraft(engine, null, landingGear, navigationSystem));
        assertThrows(NullPointerException.class,
                () -> new Aircraft(engine, fuelSystem, null, navigationSystem));
        assertThrows(NullPointerException.class,
                () -> new Aircraft(engine, fuelSystem, landingGear, null));
    }

    @Test
    void prepareForTakeoffBecomesReadyWhenAllChecksPass() {
        stubAllSystemsReady();

        aircraft.prepareForTakeoff();

        assertEquals(FlightStatus.READY, aircraft.getStatus());
        verify(engine, times(1)).isOperational();
        verify(fuelSystem, times(1)).hasEnoughFuel();
        verify(navigationSystem, times(1)).isRouteAvailable();
        verify(engine, never()).start();
        verifyNoInteractions(landingGear);
    }

    @Test
    void prepareForTakeoffFailsWhenEngineIsNotOperational() {
        when(engine.isOperational()).thenReturn(false);

        FlightException exception = assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals("Cannot prepare for takeoff: engine is not operational.", exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(engine, times(1)).isOperational();
        verifyNoInteractions(fuelSystem, navigationSystem, landingGear);
    }

    @Test
    void prepareForTakeoffFailsWhenFuelIsInsufficient() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(false);

        FlightException exception = assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals("Cannot prepare for takeoff: insufficient fuel.", exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(fuelSystem, times(1)).hasEnoughFuel();
        verifyNoInteractions(navigationSystem);
    }

    @Test
    void prepareForTakeoffFailsWhenRouteIsUnavailable() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(false);

        FlightException exception = assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals("Cannot prepare for takeoff: route is unavailable.", exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(navigationSystem, times(1)).isRouteAvailable();
    }

    @Test
    void prepareForTakeoffKeepsAircraftGroundedWhenNavigationSystemThrows() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        IllegalStateException gpsFailure = new IllegalStateException("GPS signal lost");
        when(navigationSystem.isRouteAvailable()).thenThrow(gpsFailure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, aircraft::prepareForTakeoff);

        assertSame(gpsFailure, thrown);
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        assertThrows(FlightException.class, aircraft::takeOff);
        verifyNoInteractions(landingGear);
    }

    @Test
    void takeOffBeforePreparationIsRejected() {
        FlightException exception = assertThrows(FlightException.class, aircraft::takeOff);

        assertEquals("Operation is not allowed while aircraft status is GROUNDED.", exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verifyNoInteractions(engine, landingGear);
    }

    @Test
    void takeOffStartsEngineThenRetractsLandingGear() {
        stubAllSystemsReady();
        doNothing().when(engine).start();
        doNothing().when(landingGear).retract();
        aircraft.prepareForTakeoff();

        aircraft.takeOff();

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        InOrder order = inOrder(engine, landingGear);
        order.verify(engine, times(1)).start();
        order.verify(landingGear, times(1)).retract();
        verify(landingGear, never()).deploy();
        verify(engine, never()).stop();
    }

    @Test
    void takeOffIsAbortedWhenEngineFailsToStart() {
        stubAllSystemsReady();
        IllegalStateException engineFailure = new IllegalStateException("The engine is not operational.");
        doThrow(engineFailure).when(engine).start();
        aircraft.prepareForTakeoff();

        FlightException exception = assertThrows(FlightException.class, aircraft::takeOff);

        assertEquals("Takeoff failed.", exception.getMessage());
        assertSame(engineFailure, exception.getCause());
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());
        verify(landingGear, never()).retract();
    }

    @Test
    void takeOffIsAbortedWhenLandingGearFailsToRetract() {
        stubAllSystemsReady();
        doNothing().when(engine).start();
        RuntimeException gearFailure = new RuntimeException("Hydraulic failure");
        doThrow(gearFailure).when(landingGear).retract();
        aircraft.prepareForTakeoff();

        FlightException exception = assertThrows(FlightException.class, aircraft::takeOff);

        assertSame(gearFailure, exception.getCause());
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());
        verify(engine, times(1)).start();
        assertThrows(FlightException.class, aircraft::prepareForTakeoff);
    }

    @Test
    void cruiseConsumesRequestedFuelOnEveryCall() {
        bringAircraftIntoFlight();

        aircraft.cruise(100.0);
        aircraft.cruise(100.0);
        aircraft.cruise(250.5);

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(fuelSystem, times(3)).consumeFuel(any(Double.class));
        verify(fuelSystem, times(2)).consumeFuel(100.0);
        verify(fuelSystem, times(1)).consumeFuel(250.5);
        verify(landingGear, never()).deploy();
    }

    @Test
    void cruiseRejectsNonPositiveFuelAmount() {
        bringAircraftIntoFlight();

        assertThrows(IllegalArgumentException.class, () -> aircraft.cruise(0));
        assertThrows(IllegalArgumentException.class, () -> aircraft.cruise(-5));

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(fuelSystem, never()).consumeFuel(any(Double.class));
    }

    @Test
    void cruiseBeforeTakeOffIsRejected() {
        assertThrows(FlightException.class, () -> aircraft.cruise(10));

        verifyNoInteractions(fuelSystem, engine);
    }

    @Test
    void cruiseTriggersEmergencyLandingWhenEngineFailsMidFlight() {
        when(engine.isOperational()).thenReturn(true, true, false);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);
        aircraft.prepareForTakeoff();
        aircraft.takeOff();

        aircraft.cruise(50);
        aircraft.cruise(50);

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(fuelSystem, times(1)).consumeFuel(any(Double.class));
        InOrder order = inOrder(landingGear, engine);
        order.verify(landingGear).deploy();
        order.verify(engine).stop();
    }

    @Test
    void cruiseEmergencyLandsAndReportsFuelConsumptionFailure() {
        bringAircraftIntoFlight();
        IllegalStateException fuelFailure = new IllegalStateException("Not enough fuel.");
        doThrow(fuelFailure).when(fuelSystem).consumeFuel(any(Double.class));

        FlightException exception = assertThrows(FlightException.class, () -> aircraft.cruise(1000));

        assertEquals("A problem occurred while consuming fuel.", exception.getMessage());
        assertSame(fuelFailure, exception.getCause());
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    @Test
    void landDeploysLandingGearThenStopsEngine() {
        bringAircraftIntoFlight();
        doNothing().when(landingGear).deploy();
        doNothing().when(engine).stop();

        aircraft.land();

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        InOrder order = inOrder(landingGear, engine);
        order.verify(landingGear, times(1)).deploy();
        order.verify(engine, times(1)).stop();
        assertThrows(FlightException.class, aircraft::land);
        assertThrows(FlightException.class, aircraft::emergencyLand);
    }

    @Test
    void emergencyLandWhileGroundedIsRejected() {
        assertThrows(FlightException.class, aircraft::emergencyLand);
        assertThrows(FlightException.class, aircraft::land);

        verifyNoInteractions(landingGear, engine);
    }

    @Test
    void landedAircraftCanFlyAgainAfterResettingMocks() {
        bringAircraftIntoFlight();
        aircraft.land();
        verify(engine, times(1)).start();
        verify(engine, times(1)).stop();

        reset(engine, fuelSystem, landingGear, navigationSystem);
        verifyNoInteractions(engine, fuelSystem, landingGear, navigationSystem);
        assertThrows(FlightException.class, aircraft::prepareForTakeoff);
        reset(engine);

        bringAircraftIntoFlight();
        aircraft.cruise(20);
        aircraft.emergencyLand();

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(engine, times(2)).isOperational();
        verify(engine, times(1)).start();
        verify(engine, times(1)).stop();
        verify(fuelSystem, times(1)).consumeFuel(20.0);
        verify(landingGear, times(1)).retract();
        verify(landingGear, times(1)).deploy();
        verifyNoMoreInteractions(engine);
    }
}
