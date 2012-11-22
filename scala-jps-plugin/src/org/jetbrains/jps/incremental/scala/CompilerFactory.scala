package org.jetbrains.jps.incremental.scala

import java.io.File
import org.jetbrains.jps.incremental.MessageHandler
import sbt.inc.AnalysisStore

/**
 * @author Pavel Fatin
 */
trait CompilerFactory {
  def createCompiler(configuration: CompilerConfiguration,
                     storeProvider: File => AnalysisStore,
                     messageHandler: MessageHandler): Compiler
}
