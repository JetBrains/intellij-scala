package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io.File

import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.compiler.data.CompilerJars
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgs

trait WorksheetInProcessRunner {

  def loadAndRun(args: WorksheetArgs.RunPlain, context: WorksheetRunnerContext, client: Client): Unit
}

case class WorksheetRunnerContext(compilerJars: CompilerJars, classpath: Seq[File])