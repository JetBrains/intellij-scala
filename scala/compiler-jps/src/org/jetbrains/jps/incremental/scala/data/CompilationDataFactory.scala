package org.jetbrains.jps.incremental.scala.data

import java.io.File

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.CompileContext

trait CompilationDataFactory {
  def from(sources: Seq[File], allSources: Seq[File], context: CompileContext, chunk: ModuleChunk): Either[String, CompilationData]
}
