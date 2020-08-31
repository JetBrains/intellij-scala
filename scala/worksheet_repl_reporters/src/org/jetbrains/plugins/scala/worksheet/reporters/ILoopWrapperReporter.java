package org.jetbrains.plugins.scala.worksheet.reporters;

public interface ILoopWrapperReporter {

    void report(String severity,
                Integer line,
                Integer column,
                String lineContent,
                String message);

    default void internalDebug(String message) {};
}