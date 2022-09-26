package org.jetbrains.plugins.scala.util.runners;


import com.intellij.pom.java.LanguageLevel;

// required at compile time to use in annotations
public enum TestJdkVersion {

    JDK_1_8, JDK_11;

    public LanguageLevel toProductionVersion() {
        return switch (this) {
            case JDK_1_8 -> LanguageLevel.JDK_1_8;
            case JDK_11 -> LanguageLevel.JDK_11;
        };
    };

    public static TestJdkVersion from(LanguageLevel level) {
        return switch (level) {
            case JDK_1_8 -> JDK_1_8;
            case JDK_11 -> JDK_11;
            default -> throw new RuntimeException("Jdk is not supported in tests for now");
        };
    }
}
