package org.jetbrains.jps.incremental.scala.local.worksheet

import org.jetbrains.jps.incremental.scala.Client

class DebugLoggingReporter(client: Client) extends ILoopWrapperReporter {

  override def report(severity: String, line: Integer, column: Integer, lineContent: String, message: String): Unit = ()

  override def internalDebug(message: String): Unit = client.debug(message)
}

