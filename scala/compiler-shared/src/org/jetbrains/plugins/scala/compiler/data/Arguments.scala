package org.jetbrains.plugins.scala.compiler.data

import org.jetbrains.plugins.scala.compiler.data.serialization.{SerializationUtils, WorksheetArgsSerializer}
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgs

case class Arguments(token: String,
                     sbtData: SbtData,
                     compilerData: CompilerData,
                     compilationData: CompilationData,
                     // TODO: separate different kinds of requests: Compile / Run worksheet / Run Repl worksheet / (potentially run tests)
                     worksheetArgs: Option[WorksheetArgs]) {

  import SerializationUtils._

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
      sequenceToString(worksheetArgs.map(WorksheetArgsSerializer.serialize).getOrElse(Nil)),
      //sbtIncOptions
      filesToPaths(compilationData.zincData.allSources),
      compilationData.zincData.compilationStartDate.toString,
      compilationData.zincData.isCompile.toString
    )
  }
}