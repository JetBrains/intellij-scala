package org.jetbrains.jps.incremental.scala.local.worksheet;

public interface ILoopWrapperReporter {

    void report(String severity,
                Integer line,
                Integer column,
                String lineContent,
                String message);

    void internalDebug(String message);
}