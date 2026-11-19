package pl.domradar.source.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import org.springframework.scheduling.support.CronExpression;

import java.lang.annotation.*;

/** Wartość musi być poprawnym wyrażeniem cron Springa (lub pusta). */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidCron.Validator.class)
public @interface ValidCron {

    String message() default "niepoprawne wyrażenie cron (oczekiwane 6 pól, np. '0 0 */6 * * *')";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidCron, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || CronExpression.isValidExpression(value);
        }
    }
}
