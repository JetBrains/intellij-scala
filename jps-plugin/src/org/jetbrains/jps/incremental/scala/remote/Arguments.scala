package org.jetbrains.jps.incremental.scala
package remote

import data._
import java.io.File
import Arguments._
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugin.scala.compiler.{NameHashing, CompileOrder, IncrementalType}

/**
 * @author Pavel Fatin
 */
case class Arguments(sbtData: SbtData, compilerData: CompilerData, compilationData: CompilationData, worksheetFiles: Seq[String]) {
  def asStrings: Seq[String] = {
    val (outputs, caches) = compilationData.outputToCacheMap.toSeq.unzip

    val (sourceRoots, outputDirs) = compilationData.outputGroups.unzip

    val compilerJarPaths = compilerData.compilerJars.map(jars => filesToPaths(jars.library +: jars.compiler +: jars.extra))

    val javaHomePath = compilerData.javaHome.map(fileToPath)

    val incrementalType = compilerData.incrementalType

    val nameHashing = compilationData.nameHashing

    Seq(
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
  }
}

object Arguments {
  private val Delimiter = "\n"

  def from(strings: Seq[String]): Arguments = strings match {
    case Seq(
    PathToFile(interfaceJar),
    PathToFile(sourceJar),
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
    PathsToFiles(outputDirs), 
    StringToSequence(worksheetClass),
    nameHashingName) =>

      val sbtData = SbtData(interfaceJar, sourceJar, interfacesHome, javaClassVersion)

      val compilerJars = compilerJarPaths.map {
        case PathsToFiles(Seq(libraryJar, compilerJar, extraJars @ _*)) =>
          CompilerJars(libraryJar, compilerJar, extraJars)
      }

      val javaHome = javaHomePath.map {
        case PathToFile(file) => file
      }

      val incrementalType = IncrementalType.valueOf(incrementalTypeName)

      val compilerData = CompilerData(compilerJars, javaHome, incrementalType)

      val outputToCacheMap = outputs.zip(caches).toMap

      val outputGroups = sourceRoots zip outputDirs

      val nameHashing = NameHashing.valueOf(nameHashingName)

      val compilationData = CompilationData(sources, classpath, output, scalaOptions, javaOptions, CompileOrder.valueOf(order), cacheFile, outputToCacheMap, outputGroups, nameHashing)

      Arguments(sbtData, compilerData, compilationData, worksheetClass)
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
}
