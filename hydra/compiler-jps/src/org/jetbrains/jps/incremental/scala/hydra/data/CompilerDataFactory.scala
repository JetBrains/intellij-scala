package org.jetbrains.jps.incremental.scala.hydra.data

import java.io.File

import com.intellij.openapi.diagnostic.{Logger => JpsLogger}
import org.jetbrains.jps.incremental.scala.data.{CompilerDataFactory => ScalaCompilerDataFactory}
import org.jetbrains.jps.incremental.scala.data.{CompilerData => ScalaCompilerData}
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.scala.data.CompilerData
import org.jetbrains.jps.incremental.scala.SettingsManager
import org.jetbrains.jps.incremental.scala.data.CompilerJars
import org.jetbrains.jps.incremental.scala.hydra.HydraSettingsManager
import org.jetbrains.jps.model.module.JpsModule

object CompilerDataFactory extends ScalaCompilerDataFactory {
  private val Log: JpsLogger = JpsLogger.getInstance(CompilerData.getClass.getName)

  override def from(context: CompileContext, chunk: ModuleChunk): Either[String, CompilerData] = {
    val project = context.getProjectDescriptor
    val target = chunk.representativeTarget
    val module = target.getModule

    val compilerJars = if (SettingsManager.hasScalaSdk(module)) {
      compilerJarsIn(module).flatMap { case jars: CompilerJars =>
        val compileJars =
          if (useHydraCompiler(project, module, jars)) {
            getHydraCompilerJars(project, module, jars)
          } else jars
        Log.info("Compiler jars: " + compileJars.files.map(_.getName))
        val absentJars = compileJars.files.filter(!_.exists)
        Either.cond(absentJars.isEmpty,
          Some(compileJars),
          "Scala compiler JARs not found (module '" + chunk.representativeTarget().getModule.getName + "'): "
            + absentJars.map(_.getPath).mkString(", "))
      }
    } else {
      Right(None)
    }

    compilerJars.flatMap { jars =>
      val incrementalityType = SettingsManager.getProjectSettings(project.getProject).getIncrementalityType
      javaHome(context, module).map(CompilerData(jars, _, incrementalityType))
    }

  }

  private def javaHome(context: CompileContext, module: JpsModule): Either[String, Option[File]] =
    ScalaCompilerData.javaHome(context, module)

  private def compilerVersion(module: JpsModule): Option[String] =
    ScalaCompilerData.compilerVersion(module)

  private def compilerJarsIn(module: JpsModule): Either[String, CompilerJars] =
    ScalaCompilerData.compilerJarsIn(module)

  private def useHydraCompiler(project: ProjectDescriptor, module: JpsModule, jars: CompilerJars): Boolean = {
    val hydraGlobalSettings = HydraSettingsManager.getGlobalHydraSettings(project.getModel.getGlobal)
    val hydraProjectSettings = HydraSettingsManager.getHydraSettings(project.getProject)

    val enabled = hydraProjectSettings.isHydraEnabled
    val compilerVer = compilerVersion(module)
    val hydraArtifactsExist = compilerVer.map(v => hydraGlobalSettings.containsArtifactsFor(v, hydraProjectSettings.getHydraVersion)).getOrElse(false)
    val res = enabled && hydraArtifactsExist

    if (enabled && !res) {
      val reason =
        if (compilerVer.isEmpty) s"could not extract compiler version from module $module, ${compilerJarsIn(module)}"
        else s"Hydra artifacts not found for ${compilerVer.get} and ${hydraProjectSettings.getHydraVersion}."

      Log.error(s"Not using Hydra compiler for ${module.getName} because $reason")
    }
    res
  }

  private def getHydraCompilerJars(project: ProjectDescriptor, module: JpsModule, jars: CompilerJars) = {
    val scalaVersion = compilerVersion(module).get
    val hydraData = HydraData(project.getProject, scalaVersion)
    val hydraOtherJars = hydraData.otherJars
    val extraJars = if(hydraOtherJars.nonEmpty) hydraOtherJars else jars.extra
    CompilerJars(jars.library, hydraData.getCompilerJar.getOrElse(jars.compiler), extraJars)
  }
}
