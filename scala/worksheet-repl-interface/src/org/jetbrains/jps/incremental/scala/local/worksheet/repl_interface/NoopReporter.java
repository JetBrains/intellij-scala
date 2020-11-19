package org.jetbrains.jps.incremental.scala.local.worksheet.repl_interface;

public class NoopReporter implements ILoopWrapperReporter {

    @Override
    public void report(String severity,
                       Integer line,
                       Integer column,
                       String lineContent,
                       String message) {
    }
}
