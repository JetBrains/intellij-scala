package org.jetbrains.jps.incremental.scala.data

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.CompileContext

trait CompilerDataFactory {
  def from(context: CompileContext, chunk: ModuleChunk): Either[String, CompilerData]
}
