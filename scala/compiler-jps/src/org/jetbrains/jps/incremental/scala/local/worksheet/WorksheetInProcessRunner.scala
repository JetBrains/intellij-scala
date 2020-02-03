package org.jetbrains.jps.incremental.scala.local.worksheet

import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.local.worksheet.WorksheetServer.WorksheetArgs

trait WorksheetInProcessRunner {

  def loadAndRun(worksheetArgs: WorksheetArgs, client: Client): Unit
}