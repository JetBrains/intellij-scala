package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderEnumerator
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.plugins.scala.compiler.data._
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgs
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings
import org.jetbrains.plugins.scala.project.{ModuleExt, VirtualFileExt}
import org.jetbrains.plugins.scala.util.ScalaPluginJars

import java.io.File

//noinspection SameParameterValue
abstract class RemoteServerConnectorBase(
  protected val module: Module,
  filesToCompile: Option[Seq[File]],
  protected val outputDir: File
) {
  filesToCompile.foreach(checkFilesToCompile)

  private val sbtData = {
    val javaClassVersion = System.getProperty("java.class.version")
    SbtData.from(ScalaPluginJars.jpsRoot, javaClassVersion, Utils.getSystemRoot.toPath) match {
      case Left(msg)   => throw new IllegalArgumentException(msg)
      case Right(data) => data
    }
  }

  private val sourceRoot: Option[File] = {
    val fileToCompile = filesToCompile.flatMap(_.headOption)
    fileToCompile.flatMap(_.getAbsoluteFile.getParentFile.toOption)
  }

  protected def scalaParameters: Seq[String] =
    compilerSettings.getOptionsAsStrings(module.hasScala3)

  private val javaParameters = Seq.empty[String]

  private val moduleCompilerClasspath: Seq[File] = module.scalaCompilerClasspath
  protected var additionalCompilerClasspath: Seq[File] = Nil
  def compilerClasspath: Seq[File] = moduleCompilerClasspath ++ additionalCompilerClasspath

  private val additionalRuntimeClasspath: Seq[File] =
    compilerClasspath :+
      ScalaPluginJars.runnersJar :+
      ScalaPluginJars.compilerSharedJar :+
      ScalaPluginJars.scalaJpsJar :+
      outputDir

  protected def worksheetArgs: Option[WorksheetArgs] = None

  private def runtimeClasspath: Seq[File] = {
    val classesRoots = assemblyRuntimeClasspath().map(stripJarPathSuffix).map(new File(_))
    classesRoots ++ additionalRuntimeClasspath
  }

  private def stripJarPathSuffix(f: File): String =
    f.getCanonicalPath.stripSuffix("!").stripSuffix("!/")

  protected def assemblyRuntimeClasspath(): Seq[File] = {
    val enumerator = OrderEnumerator.orderEntries(module).compileOnly().recursively()
    enumerator.getClassesRoots.map(_.toFile).toSeq
  }

  protected final def arguments: Arguments = Arguments(
    sbtData = sbtData,
    compilerData = CompilerData(
      compilerJars = CompilerJarsFactory.fromFiles(compilerClasspath).toOption,
      javaHome = Some(findJdk),
      incrementalType = IncrementalityType.IDEA
    ),
    compilationData = CompilationData(
      sources = filesToCompile.toSeq.flatten,
      classpath = runtimeClasspath,
      output = outputDir,
      scalaOptions = scalaParameters,
      javaOptions = javaParameters,
      order = CompileOrder.valueOf(compilerSettings.compileOrder.name),
      cacheFile = new File(""),
      outputToCacheMap = Map.empty,
      outputGroups = sourceRoot.map(_ -> outputDir).toSeq,
      zincData = ZincData(
        allSources = Seq.empty,
        compilationStartDate = 0,
        isCompile = false
      )
    ),
    worksheetArgs = worksheetArgs
  )

  protected def compilerSettings: ScalaCompilerSettings = module.scalaCompilerSettings

  private def findJdk = CompileServerLauncher.compileServerJdk(module.getProject)
    .fold(m => throw new IllegalArgumentException(s"JDK for compiler process not found: $m"), _.executable)

  private def checkFilesToCompile(files: Seq[File]): Unit = {
    if (files.isEmpty)
      throw new IllegalArgumentException("Non-empty list of files expected")

    files.find(!_.exists()).foreach(f =>
      throw new IllegalArgumentException(s"File ${f.getCanonicalPath} does not exists" )
    )

    if (files.map(_.getParent).distinct.size != 1)
      throw new IllegalArgumentException("All files should be in the same directory")
  }
}
