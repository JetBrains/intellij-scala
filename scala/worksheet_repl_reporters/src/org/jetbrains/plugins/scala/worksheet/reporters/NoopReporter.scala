package org.jetbrains.plugins.scala.worksheet.reporters

class NoopReporter extends ILoopWrapperReporter {

  override def report(
    severity: String,
    line: Integer,
    column: Integer,
    lineContent: String,
    message: String
  ): Unit = ()
}

