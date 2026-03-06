package org.jetbrains.plugins.scala.compiler.data

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.incremental.scala.remote.{NioPathTranslator, PathTranslator}
import org.jetbrains.plugins.scala.compiler.data.serialization.{SerializationUtils, WorksheetArgsSerializer}
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgs


case class Arguments(sbtData: SbtData,
                     compilerData: CompilerData,
                     compilationData: CompilationData,
                     // TODO: separate different kinds of requests: Compile / Run worksheet / Run Repl worksheet / (potentially run tests)
                     worksheetArgs: Option[WorksheetArgs]) {

  import SerializationUtils.{pathToString, pathsToString, sequenceToString}

  @deprecated(message = "Use asStrings(PathTranslator). Kept for preserving binary compatibility.", since = "2026.1")
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.2")
  def asStrings: Seq[String] = asStrings(NioPathTranslator)

  /** @see `org.jetbrains.jps.incremental.scala.data.ArgumentsParser.parse` */
  def asStrings(translator: PathTranslator): Seq[String] = {
    val (outputs, caches) = compilationData.outputToCacheMap.toSeq.unzip
    val (sourceRoots, outputDirs) = compilationData.outputGroups.unzip

    SbtData.serialize(sbtData, translator) ++
      CompilerData.serialize(compilerData, translator) ++
      Seq(
        pathsToString(compilationData.sources, translator),
        pathsToString(compilationData.classpath, translator),
        pathToString(compilationData.output, translator),
        sequenceToString(compilationData.scalaOptions),
        sequenceToString(compilationData.javaOptions),
        compilationData.order.toString,
        pathToString(compilationData.cacheFile, translator),
        pathsToString(outputs, translator),
        pathsToString(caches, translator),
        pathsToString(sourceRoots, translator),
        pathsToString(outputDirs, translator),
        sequenceToString(worksheetArgs.map(WorksheetArgsSerializer.serialize(_, translator)).getOrElse(Nil)),
        //sbtIncOptions
        pathsToString(compilationData.zincData.allSources, translator),
        compilationData.zincData.compilationStartDate.toString,
        compilationData.zincData.isCompile.toString
      )
  }
}