package org.jetbrains.plugins.scala.compiler.data.worksheet

import java.io.File

sealed trait WorksheetArgs {

  def isRepl: Boolean = this.isInstanceOf[WorksheetArgsRepl]
}

// TODO: move to object
final case class WorksheetArgsPlain(
  worksheetClassName: String, // Compiled class name to execute
  pathToRunnersJar: File, // Path to runners.jar (needed to load MacroPrinter for types)
  worksheetTempFile: File, // Output - path to temp file, where processed worksheet code is written
  originalFileName: String, // original file name used for stack traces
  outputDirs: Seq[File], // Output dir for compiled worksheet (i.e. for compiled temp file with processed code)
) extends WorksheetArgs

final case class WorksheetArgsRepl(
  sessionId: String,
  codeChunk: String,
  continueOnChunkError: Boolean, // NOTE: this is currently only used for testing purposes
  outputDirs: Seq[File]
) extends WorksheetArgs
