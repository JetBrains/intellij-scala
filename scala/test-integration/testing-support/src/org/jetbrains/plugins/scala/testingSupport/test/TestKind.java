package org.jetbrains.plugins.scala.testingSupport.test;

import org.jetbrains.annotations.NonNls;

public enum TestKind {
    ALL_IN_PACKAGE("All in package"),
    CLAZZ("Class"),
    TEST_NAME("Test name"),
    REGEXP("Regular expression");

    // NOTE: this value is only used to persist the enum, do not change it or migrate old settings very carefully
    private final String value;

    TestKind(@NonNls String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static TestKind parse(String s) {
        if (ALL_IN_PACKAGE.value.equals(s)) return ALL_IN_PACKAGE;
        else if (CLAZZ.value.equals(s)) return CLAZZ;
        else if (TEST_NAME.value.equals(s)) return TEST_NAME;
        else if (REGEXP.value.equals(s)) return REGEXP;
        else return null;
    }
}
