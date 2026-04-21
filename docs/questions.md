# Business Logic Questions & Constraints

## Question 1: What happens to the deposit if a property booking or appointment is cancelled?

**My understanding:**
When an appointment is cancelled suddenly or a property lease breaks before the contract ends, a penalty must occur to cover the opportunity cost of the lost time slot or booking, whereas standard cancellations within an acceptable 24-hour advance timeframe might allow for full refunds.

**Solution:**
We implemented an advance notice threshold in the `AppointmentService`. If an appointment is cancelled or rescheduled with less than 24 hours of notice, a logic block assigns a pre-calculated cancellation penalty directly to the `penalty` property of the `Appointment` entity, and we withhold an equivalent percentage from the `Transaction` entity when processing a refund in the `FinanceDomain`. Otherwise, a 100% refund goes back to the initial transaction entry.

## Question 2: How do we prevent overlapping appointments or double-booking of resources/staff?

**My understanding:**
In standard medical or physical asset reservation scenarios, two patients cannot be seen by the same doctor simultaneously, and one piece of medical equipment cannot physically exist in two rooms. Because concurrent requests could lead to race conditions, application-level checks alone are insufficient.

**Solution:**
We rely on a JPA query that uses `(startTime < newEndTime AND endTime > newStartTime)` for a given `staffId` or `resourceId`. By checking if `count > 0` before allowing a save, we halt logic execution and throw a `BusinessException`. Additionally, to prevent race conditions during heavy concurrent scheduling, we can ensure database-level locking on the parent resource row or utilize composite unique indexes during the actual database transaction.
