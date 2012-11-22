package org.jetbrains.jps.incremental.scala

import data.CompilationData
import org.jetbrains.jps.incremental.MessageHandler
import xsbti.compile.CompileProgress

/**
 * @author Pavel Fatin
 */
trait Compiler {
  def compile(compilationData: CompilationData,
              messageHandler: MessageHandler,
              fileHandler: FileHandler,
              progress: CompileProgress)
}
