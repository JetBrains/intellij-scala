package org.jetbrains.plugins.scala
package compiler

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderEnumerator
import org.jetbrains.plugins.scala.compiler.data._
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgs
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.{ModuleExt,ProjectContext,VirtualFileExt}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings
import org.jetbrains.plugins.scala.util.ScalaPluginJars

//noinspection SameParameterValue
abstract class RemoteServerConnectorBase(
  protected val module: Module,
  filesToCompile: Option[Seq[File]],
  protected val outputDir: File
) {
  filesToCompile.foreach(checkFilesToCompile)

  implicit def projectContext: ProjectContext = module.getProject

  private val sbtData = {
    val javaClassVersion = System.getProperty("java.class.version")
    SbtData.from(ScalaPluginJars.jpsRoot, javaClassVersion) match {
      case Left(msg)   => throw new IllegalArgumentException(msg)
      case Right(data) => data
    }
  }

  private val sourceRoot: Option[File] = {
    val fileToCompile = filesToCompile.flatMap(_.headOption)
    fileToCompile.flatMap(_.getAbsoluteFile.getParentFile.toOption)
  }

  protected def scalaParameters: Seq[String] = compilerSettings.toOptions ++ additionalScalaParameters

  private val javaParameters = Seq.empty[String]

  private val compilerClasspath: collection.Seq[File] = module.scalaCompilerClasspath

  private val additionalCp: collection.Seq[File] = compilerClasspath :+ ScalaPluginJars.runnersJar :+ ScalaPluginJars.compilerSharedJar :+ outputDir

  protected def additionalScalaParameters: Seq[String] = Seq.empty

  protected def worksheetArgs: Option[WorksheetArgs] = None

  private def classpath: Seq[File] = {
    val classesRoots = assemblyClasspath().map(f => new File(f.getCanonicalPath.stripSuffix("!").stripSuffix("!/")))
    classesRoots ++ additionalCp
  }

  protected final def arguments = Arguments(
    sbtData = sbtData,
    compilerData = CompilerData(
      compilerJars = CompilerJarsFactory.fromFiles(compilerClasspath).toOption,
      javaHome = Some(findJdk),
      incrementalType = IncrementalityType.IDEA
    ),
    compilationData = CompilationData(
      sources = filesToCompile.toSeq.flatten,
      classpath = classpath,
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

  protected def settings: ScalaCompileServerSettings = ScalaCompileServerSettings.getInstance()

  private def assemblyClasspath(): Seq[File] = {
    val extensionCp = WorksheetCompilerExtension.worksheetClasspath(module)
    extensionCp.getOrElse {
      val enumerator = OrderEnumerator.orderEntries(module).compileOnly()
      enumerator.getClassesRoots.map(_.toFile).toSeq
    }
  }

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
