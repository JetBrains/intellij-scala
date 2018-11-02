package org.jetbrains.jps.incremental.scala.hydra.data

import java.io.File
import java.nio.file.Paths

import com.intellij.openapi.diagnostic.{Logger => JpsLogger}
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.scala.data.CompilationData
import org.jetbrains.jps.incremental.scala.data.CompilerData
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.scala.data.BaseCompilationData
import org.jetbrains.jps.incremental.scala.hydra.HydraSettingsManager
import org.jetbrains.jps.model.module.JpsModule

object CompilationDataFactory extends BaseCompilationData {
  private val Log: JpsLogger = JpsLogger.getInstance(CompilationData.getClass.getName)

  override protected def extraOptions(target: ModuleBuildTarget, context: CompileContext, module: JpsModule, outputGroups: Seq[(File, File)]) = {
    val hydraSettings = HydraSettingsManager.getHydraSettings(context.getProjectDescriptor.getProject)
    val hydraGlobalSettings = HydraSettingsManager.getGlobalHydraSettings(context.getProjectDescriptor.getModel.getGlobal)
    val scalaVersion = CompilerData.compilerVersion(module)
    val hydraConfigFolder = if (target.isTests) "test" else "main"
    val hydraOptions =
      if (hydraSettings.isHydraEnabled && scalaVersion.nonEmpty && hydraGlobalSettings.containsArtifactsFor(scalaVersion.get, hydraSettings.getHydraVersion))
        Seq("-sourcepath", outputGroups.map(_._1).mkString(File.pathSeparator), "-cpus", hydraSettings.getNumberOfCores,
          "-YsourcePartitioner:" + hydraSettings.getSourcePartitioner, "-YhydraStore", Paths.get(hydraSettings.getHydraStorePath, module.getName, hydraConfigFolder).toString,
          "-YpartitionFile", Paths.get(hydraSettings.getHydraStorePath, module.getName).toString, "-YrootDirectory", hydraSettings.getProjectRoot,
          "-YtimingsFile", Paths.get(hydraSettings.getHydraStorePath, "timings.csv").toString, "-YhydraTag", s"${module.getName}/${hydraConfigFolder}")
      else
        Seq.empty
    Log.debug("Hydra options: " + hydraOptions.mkString(" "))

    hydraOptions
  }
}
