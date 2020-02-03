package org.jetbrains.plugins.scala
package compiler

import java.io.File
import java.net.{URL, URLClassLoader}

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import org.jetbrains.jps.incremental.scala.data.SbtData
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

  private val scalaParameters = compilerSettings.toOptions.toArray

  private val javaParameters = Array.empty[String]

  private val compilerClasspath: Seq[File] = module.scalaCompilerClasspath

  private val compilerSharedJar = new File(libCanonicalPath, "compiler-shared.jar")
  
  protected val runnersJar = new File(libCanonicalPath, "runners.jar")

  val additionalCp: Seq[File] = compilerClasspath :+ runnersJar :+ compilerSharedJar :+ outputDir

  protected def worksheetArgs: Array[String] = Array()

  protected def classpath: String = {
    val classesRoots = assemblyClasspath().toSeq map (f => new File(f.getCanonicalPath stripSuffix "!" stripSuffix "!/"))
    (classesRoots ++ additionalCp).mkString("\n")
  }

  import _root_.scala.language.implicitConversions

  implicit private def file2path(file: File): String = FileUtil.toCanonicalPath(file.getAbsolutePath)
  implicit private def option2string(opt: Option[String]): String = opt getOrElse ""
  implicit private def files2paths(files: Iterable[File]): String = files map file2path mkString "\n"
  implicit private def array2string(arr: Array[String]): String = arr mkString "\n"

  /**
   * parsed again in [[org.jetbrains.jps.incremental.scala.remote.Arguments#from(scala.collection.Seq)]]
   * TODO: maybe use some common shared jar and share the logic there?
   */
  def arguments: Seq[String] = Seq[String](
    sbtData.sbtInterfaceJar,
    sbtData.compilerInterfaceJar,
    sbtData.compilerBridges.scala._2_10,
    sbtData.compilerBridges.scala._2_11,
    sbtData.compilerBridges.scala._2_13,
    sbtData.compilerBridges.dotty._0_21,
    sbtData.interfacesHome,
    sbtData.javaClassVersion,
    compilerClasspath,
    findJdk,
    filesToCompile,
    classpath,
    outputDir,
    scalaParameters,
    javaParameters,
    compilerSettings.compileOrder.toString,
    "", //cache file
    "",
    "",
    IncrementalityType.IDEA.name(),
    sourceRoot,
    outputDir,
    worksheetArgs,
    "", //allSources, used in zinc only
    "0", //timestamp, used in zinc only
    "false" //isCompile
  )

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
