package org.jetbrains.plugins.scala.compiler.data.worksheet

import java.io.File

sealed trait WorksheetArgs

object WorksheetArgs {

  /**
   * @param worksheetClassName Compiled class name to execute
   * @param pathToRunnersJar   Path to runners.jar (needed to load MacroPrinter for types)
   * @param worksheetTempFile  Output - path to temp file, where processed worksheet code is written
   * @param originalFileName   original file name used for stack traces
   * @param outputDirs         Output dir for compiled worksheet (i.e. for compiled temp file with processed code)
   */
  final case class RunPlain(
    worksheetClassName: String,
    pathToRunnersJar: File,
    worksheetTempFile: File,
    originalFileName: String,
    outputDirs: Seq[File],
  ) extends WorksheetArgs

  /**
   * @param continueOnChunkError true if continue evaluating chunks if an error occurred after some chunk evaluation
   *                             (NOTE: this is currently only used for testing purposes)
   */
  final case class RunRepl(
    sessionId: String,
    codeChunk: String,
    dropCachedReplInstance: Boolean,
    continueOnChunkError: Boolean,
    outputDirs: Seq[File]
  ) extends WorksheetArgs
}
