package org.jetbrains.jps.incremental.scala

import data.CompilerData
import java.io.File
import org.jetbrains.jps.incremental.MessageHandler
import sbt.inc.AnalysisStore

/**
 * @author Pavel Fatin
 */
trait CompilerFactory {
  def createCompiler(compilerData: CompilerData,
                     storeProvider: File => AnalysisStore,
                     messageHandler: MessageHandler): Compiler
}
