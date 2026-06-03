package org.jetbrains.sbt.project;

import com.intellij.pom.java.LanguageLevel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify the required JDK language level for a test.
 * It can be used on test methods that extend {@link org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RequiresJdk {
    LanguageLevel value();
}
