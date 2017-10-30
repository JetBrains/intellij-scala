package org.jetbrains.plugins.scala
package compiler

import java.io.File
import java.net.{URL, URLClassLoader}

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.{PathUtil, PlatformUtils}
import org.jetbrains.jps.incremental.scala.data.SbtData
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings

/**
 * Nikolay.Tropin
 * 2014-10-07
 */

abstract class RemoteServerConnectorBase(module: Module, filesToCompile: Seq[File], outputDir: File, needCheck: Boolean = true) {

  implicit def projectContext: ProjectContext = module.getProject

  if (needCheck) checkFilesToCompile(filesToCompile)

  def this(module: Module, fileToCompile: File, outputDir: File) = {
    this(module, Seq(fileToCompile), outputDir)
  }

  private val libRoot = {
    if (ApplicationManager.getApplication.isUnitTestMode) {
      val pluginPath = System.getProperty("plugin.path")
      new File(pluginPath, "lib")
    }
    else new File(PathUtil.getJarPathForClass(getClass)).getParentFile
  }

  private val libCanonicalPath = PathUtil.getCanonicalPath(libRoot.getPath)

  private val sbtData = SbtData.from(
    new URLClassLoader(Array(new URL("jar:file:" + (if (libCanonicalPath startsWith "/") "" else "/" ) + libCanonicalPath + "/jps/sbt-interface.jar!/")), getClass.getClassLoader),
    new File(libRoot, "jps"),
    System.getProperty("java.class.version")
  ) match {
    case Left(msg) => throw new IllegalArgumentException(msg)
    case Right(data) => data
  }

  private val sourceRoot = filesToCompile.head.getAbsoluteFile.getParentFile

  private val scalaParameters = compilerSettings.toOptions.toArray

  private val javaParameters = Array.empty[String]

  private val compilerClasspath = scalaSdk.compilerClasspath

  private val compilerSharedJar = new File(libCanonicalPath, "compiler-shared.jar")
  
  protected val runnersJar = new File(libCanonicalPath, "runners.jar")

  val additionalCp = compilerClasspath :+ runnersJar :+ compilerSharedJar :+ outputDir

  protected def worksheetArgs: Array[String] = Array()

  protected def classpath: String = {
    val classesRoots = assemblyClasspath().toSeq map (f => new File(f.getCanonicalPath stripSuffix "!" stripSuffix "!/"))
    (classesRoots ++ additionalCp).mkString("\n")
  }

  import _root_.scala.language.implicitConversions

  implicit def file2path(file: File): String = FileUtil.toCanonicalPath(file.getAbsolutePath)
  implicit def option2string(opt: Option[String]): String = opt getOrElse ""
  implicit def files2paths(files: Iterable[File]): String = files map file2path mkString "\n"
  implicit def array2string(arr: Array[String]): String = arr mkString "\n"

  def arguments = Seq[String](
    sbtData.sbtInterfaceJar,
    sbtData.compilerInterfaceJar,
    sbtData.sourceJars._2_10,
    sbtData.sourceJars._2_11,
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
  
  protected def configurationError(message: String) = throw new IllegalArgumentException(message)

  protected def settings = ScalaCompileServerSettings.getInstance()

  private def assemblyClasspath() = OrderEnumerator.orderEntries(module).compileOnly().getClassesRoots

  private def compilerSettings: ScalaCompilerSettings = module.scalaCompilerSettings

  private def scalaSdk = module.scalaSdk.getOrElse(
          configurationError("No Scala SDK configured for module: " + module.getName))

  private def findJdk = CompileServerLauncher.compileServerJdk(module.getProject) match {
    case Some(jdk) => jdk.executable
    case None => configurationError("JDK for compiler process not found")
  }

  private def checkFilesToCompile(files: Seq[File]) = {
    if (files.isEmpty)
      throw new IllegalArgumentException("Non-empty list of files expected")

    files.find(!_.exists()).foreach(f =>
      throw new IllegalArgumentException(s"File ${f.getCanonicalPath} does not exists" )
    )

    if (files.map(_.getParent).distinct.size != 1)
      throw new IllegalArgumentException("All files should be in the same directory")
  }
}
