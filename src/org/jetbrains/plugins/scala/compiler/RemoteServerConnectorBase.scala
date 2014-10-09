package org.jetbrains.plugins.scala
package compiler

import java.io.File
import java.net.{URL, URLClassLoader}

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import org.jetbrains.jps.incremental.scala.data.SbtData
import org.jetbrains.plugin.scala.compiler.{IncrementalType, NameHashing}
import org.jetbrains.plugins.scala
import org.jetbrains.plugins.scala.config.ScalaFacet

/**
 * Nikolay.Tropin
 * 2014-10-07
 */

abstract class RemoteServerConnectorBase(module: Module, fileToCompile: File, outputDir: File) {
  private val libRoot = {
    if (ApplicationManager.getApplication.isUnitTestMode)
      new File("../out/cardea/artifacts/Scala/lib") else new File(PathUtil.getJarPathForClass(getClass)).getParentFile
  }

  private val libCanonicalPath = PathUtil.getCanonicalPath(libRoot.getPath)

  private val sbtData = SbtData.from(
    new URLClassLoader(Array(new URL("jar:file:" + (if (libCanonicalPath startsWith "/") "" else "/" ) + libCanonicalPath + "/jps/sbt-interface.jar!/")), getClass.getClassLoader),
    new File(libRoot, "jps"),
    new File(System.getProperty("user.home"), ".idea-build"),
    System.getProperty("java.class.version")
  ) match {
    case Left(msg) => throw new IllegalArgumentException(msg)
    case Right(data) => data
  }

  private val scalaParameters = facet.compilerParameters

  private val javaParameters = Array.empty[String]

  private val compilerJar = facetCompiler
  private val libraryJar = facetLibrary

  private val extraJar = facetFiles filter {
    case a => a != compilerJar && a != libraryJar
  }
  private val compilerSettingsJar = new File(libCanonicalPath, "compiler-settings.jar")
  
  protected val runnersJar = new File(libCanonicalPath, "scala-plugin-runners.jar")

  protected val additionalCp = facetFiles :+ runnersJar :+ compilerSettingsJar :+ outputDir

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

  /**
   *     Seq(
      fileToPath(sbtData.interfaceJar),
      fileToPath(sbtData.sourceJar),
      fileToPath(sbtData.interfacesHome),
      sbtData.javaClassVersion,
      optionToString(compilerJarPaths),
      optionToString(javaHomePath),
      filesToPaths(compilationData.sources),
      filesToPaths(compilationData.classpath),
      fileToPath(compilationData.output),
      sequenceToString(compilationData.scalaOptions),
      sequenceToString(compilationData.javaOptions),
      compilationData.order.toString,
      fileToPath(compilationData.cacheFile),
      filesToPaths(outputs),
      filesToPaths(caches),
      incrementalType.name,
      filesToPaths(sourceRoots),
      filesToPaths(outputDirs),
      sequenceToString(worksheetFiles),
      nameHashing.name
    )
   */
  def arguments = Seq[String](
    sbtData.interfaceJar,
    sbtData.sourceJar,
    sbtData.interfacesHome,
    sbtData.javaClassVersion,
    Seq(libraryJar, compilerJar) ++ extraJar,
    findJdk,
    fileToCompile,
    classpath,
    outputDir,
    scalaParameters,
    javaParameters,
    settings.COMPILE_ORDER.toString,
    "", //cache file
    "",
    "",
    IncrementalType.IDEA.name(),
    fileToCompile.getParentFile,
    outputDir,
    worksheetArgs,
    NameHashing.DEFAULT.name()
  )
  
  protected def configurationError(message: String) = throw new IllegalArgumentException(message)

  protected def settings = ScalaApplicationSettings.getInstance()

  private def assemblyClasspath() = OrderEnumerator.orderEntries(module).compileOnly().getClassesRoots

  private def facet =
    ScalaFacet.findIn(module) getOrElse configurationError("No Scala facet configured for module: " + module.getName)

  private def facetCompiler =
    facet.compiler flatMap (_.jar) getOrElse configurationError("No compiler jar for Scala Facet in module: " + module.getName)

  private def facetLibrary =
    facet.compiler.flatMap {
      case c => c.files find {
        case file if file.getName contains "library" => true
        case _ => false
      }
    } getOrElse configurationError("No library jar for Scala Facet in module: " + module.getName)

  private def facetFiles = facet.compiler flatMap {
    case compiler => compiler.jar map {
      case j => compiler.files
    }
  } getOrElse Seq.empty

  private def findJdk = scala.compiler.findJdkByName(settings.COMPILE_SERVER_SDK) match {
    case Right(jdk) => jdk.executable
    case Left(msg) =>
      configurationError(s"Cannot find jdk ${settings.COMPILE_SERVER_SDK} for compile server, underlying message: $msg" )
  }
}
