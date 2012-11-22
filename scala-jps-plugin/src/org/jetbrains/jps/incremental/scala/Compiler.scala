package org.jetbrains.jps.incremental.scala

import java.io.File
import org.jetbrains.jps.incremental.MessageHandler
import xsbti.compile.CompileProgress

/**
 * @author Pavel Fatin
 */
trait Compiler {
  def compile(sources: Seq[File], classpath: Seq[File], options: Seq[String], output: File, scalaFirst: Boolean, cacheFile: File,
              messageHandler: MessageHandler, fileHandler: FileHandler, progress: CompileProgress)
}
