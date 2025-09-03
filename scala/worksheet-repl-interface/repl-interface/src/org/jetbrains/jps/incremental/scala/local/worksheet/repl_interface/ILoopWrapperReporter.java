package org.jetbrains.jps.incremental.scala.local.worksheet.repl_interface;

public interface ILoopWrapperReporter {

    void report(String severity,
                Integer line,
                Integer column,
                String lineContent,
                String message);
}