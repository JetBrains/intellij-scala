package org.jetbrains.plugins.scala
package compiler

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.util.PathUtil
import org.jetbrains.plugins.scala.compiler.data._
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings

//noinspection SameParameterValue
abstract class RemoteServerConnectorBase(protected val module: Module, filesToCompile: Seq[File], outputDir: File, needCheck: Boolean = true) {

  implicit def projectContext: ProjectContext = module.getProject

  if (needCheck) checkFilesToCompile(filesToCompile)

  def this(module: Module, fileToCompile: File, outputDir: File) = {
    this(module, Seq(fileToCompile), outputDir)
  }

  private val libRoot = CompileServerLauncher.libRoot

  private val libCanonicalPath = PathUtil.getCanonicalPath(libRoot.getPath)

  private val sbtData = {
    val pluginJpsRoot = new File(libRoot, "jps")
    val javaClassVersion = System.getProperty("java.class.version")
    SbtData.from(pluginJpsRoot, javaClassVersion) match {
      case Left(msg)   => throw new IllegalArgumentException(msg)
      case Right(data) => data
    }
  }

  private val sourceRoot = filesToCompile.head.getAbsoluteFile.getParentFile

  protected def scalaParameters: Seq[String] = compilerSettings.toOptions ++ additionalScalaParameters

  private val javaParameters = Seq.empty[String]

  private val compilerClasspath: Seq[File] = module.scalaCompilerClasspath

  private val compilerSharedJar = new File(libCanonicalPath, "compiler-shared.jar")
  
  protected val runnersJar = new File(libCanonicalPath, "runners.jar")

  val additionalCp: Seq[File] = compilerClasspath :+ runnersJar :+ compilerSharedJar :+ outputDir

  protected def additionalScalaParameters: Seq[String] = Seq.empty

  protected def worksheetArgs: Seq[String] = Seq.empty

  private def classpath: Seq[File] = {
    val classesRoots = assemblyClasspath().toSeq map (f => new File(f.getCanonicalPath stripSuffix "!" stripSuffix "!/"))
    classesRoots ++ additionalCp
  }

  protected final val NoToken = "NO_TOKEN"

  protected def arguments: Seq[String] = Arguments(
    token = NoToken,
    sbtData = sbtData,
    compilerData = CompilerData(
      compilerJars = CompilerJarsFactory.fromFiles(compilerClasspath).toOption,
      javaHome = Some(findJdk),
      incrementalType = IncrementalityType.IDEA
    ),
    compilationData = CompilationData(
      sources = filesToCompile,
      classpath = classpath,
      output = outputDir,
      scalaOptions = scalaParameters,
      javaOptions = javaParameters,
      order = CompileOrder.valueOf(compilerSettings.compileOrder.name),
      cacheFile = new File(""),
      outputToCacheMap = Map.empty,
      outputGroups = Seq(sourceRoot -> outputDir),
      zincData = ZincData(
        allSources = Seq.empty,
        compilationStartDate = 0,
        isCompile = false
      )
    ),
    worksheetFiles = worksheetArgs
  ).asStrings.tail // without token

  protected def settings: ScalaCompileServerSettings = ScalaCompileServerSettings.getInstance()

  private def assemblyClasspath() = OrderEnumerator.orderEntries(module).compileOnly().getClassesRoots

  protected def compilerSettings: ScalaCompilerSettings = module.scalaCompilerSettings

  private def findJdk = CompileServerLauncher.compileServerJdk(module.getProject)
    .fold(throw new IllegalArgumentException("JDK for compiler process not found"))(_.executable)

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
