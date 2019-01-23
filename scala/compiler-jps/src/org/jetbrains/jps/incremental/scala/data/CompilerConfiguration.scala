package org.jetbrains.jps.incremental.scala.data

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental.scala.SettingsManager
import org.jetbrains.jps.incremental.scala.data.CompilationData.javaOptionsFor
import org.jetbrains.jps.incremental.scala.data.CompilationData.scalaOptionsFor
import org.jetbrains.jps.incremental.scala.model.CompileOrder

case class CompilerConfiguration(
  scalacOptions: Seq[String],
  javacOptions: Seq[String],
  order: CompileOrder
)

object CompilerConfiguration {
  def from(context: CompileContext, chunk: ModuleChunk): CompilerConfiguration = {
    val commonOptions = {
      val encoding = context.getProjectDescriptor.getEncodingConfiguration.getPreferredModuleChunkEncoding(chunk)
      Option(encoding).map(Seq("-encoding", _)).getOrElse(Seq.empty)
    }
    val compilerSettings =
      SettingsManager.getProjectSettings(context.getProjectDescriptor.getProject).getCompilerSettings(chunk)

    val order = compilerSettings.getCompileOrder

    CompilerConfiguration(
      scalacOptions = commonOptions ++ scalaOptionsFor(compilerSettings, chunk),
      javacOptions = commonOptions ++ javaOptionsFor(context, chunk),
      order
    )
  }
}

