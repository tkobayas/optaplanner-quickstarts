package org.acme.employeescheduling.solver;

import java.time.Duration;
import java.time.LocalDateTime;

import org.acme.employeescheduling.domain.Availability;
import org.acme.employeescheduling.domain.AvailabilityType;
import org.acme.employeescheduling.domain.Shift;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.Joiners;

public class EmployeeSchedulingConstraintProvider implements ConstraintProvider {

    private static int getMinuteOverlap(Shift shift1, Shift shift2) {
        // The overlap of two timeslot occurs in the range common to both timeslots.
        // Both timeslots are active after the higher of their two start times,
        // and before the lower of their two end times.
        LocalDateTime shift1Start = shift1.getStart();
        LocalDateTime shift1End = shift1.getEnd();
        LocalDateTime shift2Start = shift2.getStart();
        LocalDateTime shift2End = shift2.getEnd();
        return (int) Duration.between((shift1Start.compareTo(shift2Start) > 0) ? shift1Start : shift2Start,
                                      (shift1End.compareTo(shift2End) < 0) ? shift1End : shift2End).toMinutes();
    }

    private static int getShiftDurationInMinutes(Shift shift) {
        return (int) Duration.between(shift.getStart(), shift.getEnd()).toMinutes();
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                requiredSkill(constraintFactory),
                noOverlappingShifts(constraintFactory),
                oneShiftPerDay(constraintFactory),
                unavailableEmployee(constraintFactory),
                desiredDayForEmployee(constraintFactory),
                undesiredDayForEmployee(constraintFactory),
        };
    }

    Constraint requiredSkill(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Shift.class)
                .filter(shift -> !shift.getEmployee().getSkillSet().contains(shift.getRequiredSkill()))
                .penalize("Missing required skill", HardSoftScore.ONE_HARD);
    }

    Constraint noOverlappingShifts(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachUniquePair(Shift.class, Joiners.equal(Shift::getEmployee),
                                                   Joiners.overlapping(Shift::getStart, Shift::getEnd))
                .penalize("Overlapping shift", HardSoftScore.ONE_HARD,
                          EmployeeSchedulingConstraintProvider::getMinuteOverlap);
    }

    Constraint oneShiftPerDay(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachUniquePair(Shift.class, Joiners.equal(Shift::getEmployee),
                                                   Joiners.equal(shift -> shift.getStart().toLocalDate()))
                .penalize("Max one shift per day", HardSoftScore.ONE_HARD);
    }

    Constraint unavailableEmployee(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Shift.class)
                .join(Availability.class, Joiners.equal((Shift shift) -> shift.getStart().toLocalDate(), Availability::getDate),
                      Joiners.equal(Shift::getEmployee, Availability::getEmployee))
                .filter((shift, availability) -> availability.getAvailabilityType() == AvailabilityType.UNAVAILABLE)
                .penalize("Unavailable employee", HardSoftScore.ONE_HARD, (shift, availability) ->
                        getShiftDurationInMinutes(shift));
    }

    Constraint desiredDayForEmployee(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Shift.class)
                .join(Availability.class, Joiners.equal((Shift shift) -> shift.getStart().toLocalDate(), Availability::getDate),
                      Joiners.equal(Shift::getEmployee, Availability::getEmployee))
                .filter((shift, availability) -> availability.getAvailabilityType() == AvailabilityType.DESIRED)
                .reward("Desired day for employee", HardSoftScore.ONE_SOFT, (shift, availability) ->
                        getShiftDurationInMinutes(shift));
    }

    Constraint undesiredDayForEmployee(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Shift.class)
                .join(Availability.class, Joiners.equal((Shift shift) -> shift.getStart().toLocalDate(), Availability::getDate),
                      Joiners.equal(Shift::getEmployee, Availability::getEmployee))
                .filter((shift, availability) -> availability.getAvailabilityType() == AvailabilityType.UNDESIRED)
                .penalize("Undesired day for employee", HardSoftScore.ONE_SOFT, (shift, availability) ->
                        getShiftDurationInMinutes(shift));
    }

}
