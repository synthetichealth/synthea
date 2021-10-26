package org.mitre.synthea.export;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface JSONSkip {
  // Marker interface indicating that a field should be skipped in the JSON export
}
