package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderEnumerator
import org.jetbrains.plugins.scala.compiler.data._
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgs
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PathExt}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectContext, VirtualFileExt}
import org.jetbrains.plugins.scala.util.ScalaPluginJars

import java.nio.file.Path

//noinspection SameParameterValue
abstract class RemoteServerConnectorBase(
  protected val module: Module,
  filesToCompile: Option[Seq[Path]],
  protected val outputDir: Path
) {
  filesToCompile.foreach(checkFilesToCompile)

  implicit def projectContext: ProjectContext = module.getProject

  protected val sbtData: SbtData = {
    val javaClassVersion = System.getProperty("java.class.version")
    val compileServerDir = CompileServerLauncher.scalaCompileServerSystemDir(module.getProject)
    SbtData.from(ScalaPluginJars.jpsRoot, javaClassVersion, compileServerDir) match {
      case Left(msg)   => throw new IllegalArgumentException(msg)
      case Right(data) => data
    }
  }

  private val sourceRoot: Option[Path] = {
    val fileToCompile = filesToCompile.flatMap(_.headOption)
    fileToCompile.flatMap(_.toCanonicalPath.getParent.toOption)
  }

  protected def scalaParameters: Seq[String] =
    compilerSettings.getOptionsAsStrings(module.hasScala3)

  private val javaParameters = Seq.empty[String]

  private val moduleCompilerClasspath: Seq[Path] = module.scalaCompilerClasspath
  protected var additionalCompilerClasspath: Seq[Path] = Nil
  def compilerClasspath: Seq[Path] = moduleCompilerClasspath ++ additionalCompilerClasspath

  private val additionalRuntimeClasspath: Seq[Path] =
    compilerClasspath :+
      ScalaPluginJars.runnersJar :+
      ScalaPluginJars.compilerSharedJar :+
      ScalaPluginJars.scalaJpsJar :+
      outputDir

  protected def worksheetArgs: Option[WorksheetArgs] = None

  protected def runtimeClasspath: Seq[Path] = {
    val classesRoots = assemblyRuntimeClasspath().map(stripJarPathSuffix).map(Path.of(_))
    classesRoots ++ additionalRuntimeClasspath
  }

  private def stripJarPathSuffix(p: Path): String =
    p.toCanonicalPath.toString.stripSuffix("!").stripSuffix("!/")

  protected def assemblyRuntimeClasspath(): Seq[Path] = {
    val enumerator = OrderEnumerator.orderEntries(module).compileOnly().recursively()
    enumerator.getClassesRoots.map(_.toPath).toSeq
  }

  protected final def arguments: Arguments = Arguments(
    sbtData = sbtData,
    compilerData = CompilerData(
      compilerJars = CompilerJarsFactory.fromFiles(compilerClasspath, module.customScalaCompilerBridgeJar, module.replClasspath.asPaths).toOption,
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
      cacheFile = Path.of(""),
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

  protected def compilerSettings: ScalaCompilerSettings = ScalaCompilerSettings.forModule(module)

  protected def findJdk: Path = CompileServerLauncher.compileServerJdk(module.getProject)
    .fold(m => throw new IllegalArgumentException(s"JDK for compiler process not found: $m"), _.executable)

  private def checkFilesToCompile(files: Seq[Path]): Unit = {
    if (files.isEmpty)
      throw new IllegalArgumentException("Non-empty list of files expected")

    files.find(!_.exists).foreach(f =>
      throw new IllegalArgumentException(s"File ${f.toCanonicalPath} does not exists" )
    )

    if (files.map(_.getParent).distinct.size != 1)
      throw new IllegalArgumentException("All files should be in the same directory")
  }
}
