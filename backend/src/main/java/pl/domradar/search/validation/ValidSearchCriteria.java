package pl.domradar.search.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Reguła biznesowa nr 8: spójność kryteriów wyszukiwania — min ≤ max w każdym zakresie,
 * poprawny poligon (≤ 100 punktów, bez samoprzecięć), promień ≤ 50 km z podanym punktem.
 */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SearchCriteriaValidator.class)
public @interface ValidSearchCriteria {

    String message() default "Niepoprawne kryteria wyszukiwania";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
