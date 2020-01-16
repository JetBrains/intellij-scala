package org.jetbrains.jps.incremental.scala
package remote

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.incremental.scala.data._
import org.jetbrains.jps.incremental.scala.model.{CompileOrder, IncrementalityType}
import org.jetbrains.jps.incremental.scala.remote.Arguments._

/**
 * @author Pavel Fatin
  *         
  * @param worksheetFiles see org.jetbrains.plugins.scala.worksheet.server.RemoteServerConnector.worksheetArgs         
 */
case class Arguments(token: String, sbtData: SbtData, compilerData: CompilerData, compilationData: CompilationData, worksheetFiles: Seq[String]) {
  def asStrings: Seq[String] = {
    val (outputs, caches) = compilationData.outputToCacheMap.toSeq.unzip

    val (sourceRoots, outputDirs) = compilationData.outputGroups.unzip

    val compilerJarPaths = compilerData.compilerJars.map(jars => filesToPaths(jars.library +: jars.compiler +: jars.extra))

    val javaHomePath = compilerData.javaHome.map(fileToPath)

    val incrementalType = compilerData.incrementalType

    Seq(
      token,
      fileToPath(sbtData.sbtInterfaceJar),
      fileToPath(sbtData.compilerInterfaceJar),
      fileToPath(sbtData.compilerBridges.scala._2_10),
      fileToPath(sbtData.compilerBridges.scala._2_11),
      fileToPath(sbtData.compilerBridges.scala._2_13),
      fileToPath(sbtData.compilerBridges.dotty._0_21),
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
      //sbtIncOptions
      filesToPaths(compilationData.zincData.allSources),
      compilationData.zincData.compilationStartDate.toString,
      compilationData.zincData.isCompile.toString
    )
  }
}

object Arguments {
  private val Delimiter = "\n"

  def from(strings: Seq[String]): Arguments = strings match {
    case token +: Seq(
      PathToFile(sbtInterfaceJar),
      PathToFile(compilerInterfaceJar),
      PathToFile(scalaBridgeSourceJar_2_10),
      PathToFile(scalaBridgeSourceJar_2_11),
      PathToFile(scalaBridgeSourceJar_2_13),
      PathToFile(dottyBridgeJar_0_21),
      PathToFile(interfacesHome),
      javaClassVersion,
      StringToOption(compilerJarPaths),
      StringToOption(javaHomePath),
      PathsToFiles(sources),
      PathsToFiles(classpath),
      PathToFile(output),
      StringToSequence(scalaOptions),
      StringToSequence(javaOptions),
      order,
      PathToFile(cacheFile),
      PathsToFiles(outputs),
      PathsToFiles(caches),
      incrementalTypeName,
      PathsToFiles(sourceRoots),
      PathsToFiles(outputDirs)
    ) :+ StringToSequence(worksheetClass)
      :+ PathsToFiles(allSources)
      :+ startDate
      :+ StringToBoolean(isCompile)
     =>

      val scalaBridgeSources = SbtData.ScalaSourceJars(
        _2_10 = scalaBridgeSourceJar_2_10,
        _2_11 = scalaBridgeSourceJar_2_11,
        _2_13 = scalaBridgeSourceJar_2_13
      )
      val dottyBridges = SbtData.DottyJars(
        _0_21 = dottyBridgeJar_0_21
      )
      val compilerBridges = SbtData.CompilerBridges(scalaBridgeSources, dottyBridges)
      val sbtData = SbtData(sbtInterfaceJar, compilerInterfaceJar, compilerBridges, interfacesHome, javaClassVersion)

      val compilerJars = compilerJarPaths.map {
        case PathsToFiles(Seq(libraryJar, compilerJar, extraJars @ _*)) =>
          CompilerJars(libraryJar, compilerJar, extraJars)
      }

      val javaHome = javaHomePath.map {
        case PathToFile(file) => file
      }

      val incrementalType = IncrementalityType.valueOf(incrementalTypeName)

      val compilerData = CompilerData(compilerJars, javaHome, incrementalType)

      val outputToCacheMap = outputs.zip(caches).toMap

      val outputGroups = sourceRoots zip outputDirs

      val zincData = ZincData(allSources, startDate.toLong, isCompile)

      val compilationData = CompilationData(sources, classpath, output, scalaOptions, javaOptions, CompileOrder.valueOf(order), cacheFile, outputToCacheMap, outputGroups, zincData)

      Arguments(token, sbtData, compilerData, compilationData, worksheetClass)
  }

  private def fileToPath(file: File): String = FileUtil.toCanonicalPath(file.getPath)

  private def filesToPaths(files: Iterable[File]): String = sequenceToString(files.map(fileToPath))

  private def optionToString(s: Option[String]): String = s.getOrElse("")

  private def sequenceToString(strings: Iterable[String]): String = strings.mkString(Delimiter)

  private val PathToFile = extractor[String, File] { path: String =>
    new File(path)
  }

  private val PathsToFiles = extractor[String, Seq[File]] { paths: String =>
    if (paths.isEmpty) Seq.empty else paths.split(Delimiter).map(new File(_)).toSeq
  }

  private val StringToOption = extractor[String, Option[String]] { s: String =>
    if (s.isEmpty) None else Some(s)
  }

  private val StringToSequence = extractor[String, Seq[String]] { s: String =>
    if (s.isEmpty) Seq.empty else s.split(Delimiter).toSeq
  }

  private val StringToBoolean = extractor[String, Boolean] { s: String =>
    s.toBoolean
  }
}
