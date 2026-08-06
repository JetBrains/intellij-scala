package org.jetbrains.plugins.scala.testingSupport.test;

import com.intellij.execution.testframework.TestSearchScope;
import org.jetbrains.annotations.NonNls;

public enum SearchForTest {
    IN_WHOLE_PROJECT("In whole project"),
    IN_SINGLE_MODULE("In single module"),
    ACCROSS_MODULE_DEPENDENCIES("Across module dependencies");

    //NOTE: this value is only used to persist the enum, do not change it or migrate old settings very carefully
    private final String value;

    SearchForTest(@NonNls String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static SearchForTest parse(String str) {
        if (IN_SINGLE_MODULE.value.equals(str)) return IN_SINGLE_MODULE;
        else if (IN_WHOLE_PROJECT.value.equals(str)) return IN_WHOLE_PROJECT;
        else return ACCROSS_MODULE_DEPENDENCIES;
    }

    public TestSearchScope toPlatformTestSearchScope() {
        final TestSearchScope result;
        if (this == SearchForTest.IN_WHOLE_PROJECT)
            result = TestSearchScope.WHOLE_PROJECT;
        else if (this == SearchForTest.IN_SINGLE_MODULE)
            result = TestSearchScope.SINGLE_MODULE;
        else
            result = TestSearchScope.MODULE_WITH_DEPENDENCIES;
        return result;
    }
}
