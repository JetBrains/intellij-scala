package org.jetbrains.plugins.scala.compiler.data

import java.io.File

import com.intellij.openapi.util.io.FileUtil

/**
  * @author Pavel Fatin
  */
case class Arguments(token: String,
                     sbtData: SbtData,
                     compilerData: CompilerData,
                     compilationData: CompilationData,
                     worksheetFiles: Seq[String]) {

  import Arguments._

  def asStrings: Seq[String] = {
    val (outputs, caches) = compilationData.outputToCacheMap.toSeq.unzip

    val (sourceRoots, outputDirs) = compilationData.outputGroups.unzip

    val compilerJarPaths = compilerData.compilerJars.map(jars => filesToPaths(jars.allJars))

    val javaHomePath = compilerData.javaHome.map(fileToPath)

    val incrementalType = compilerData.incrementalType

    Seq(
      token,
      fileToPath(sbtData.sbtInterfaceJar),
      fileToPath(sbtData.compilerInterfaceJar),
      fileToPath(sbtData.compilerBridges.scala._2_10),
      fileToPath(sbtData.compilerBridges.scala._2_11),
      fileToPath(sbtData.compilerBridges.scala._2_13),
      fileToPath(sbtData.compilerBridges.dotty._0_22),
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
  final val Delimiter = "\n"

  private def fileToPath(file: File): String = FileUtil.toCanonicalPath(file.getPath)

  private def filesToPaths(files: Iterable[File]): String = sequenceToString(files.map(fileToPath))

  private def optionToString(s: Option[String]): String = s.getOrElse("")

  private def sequenceToString(strings: Iterable[String]): String = strings.mkString(Delimiter)
}
