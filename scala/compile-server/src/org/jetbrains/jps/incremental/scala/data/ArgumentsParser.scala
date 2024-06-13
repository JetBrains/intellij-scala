package org.jetbrains.jps.incremental.scala.data

import org.jetbrains.jps.incremental.scala.Extractor
import org.jetbrains.jps.incremental.scala.data.ArgumentsParser.ArgumentsParserError
import org.jetbrains.plugins.scala.compiler.data.Extractors.{PathToFile, PathsToFiles, StringToSequence}
import org.jetbrains.plugins.scala.compiler.data._
import org.jetbrains.plugins.scala.compiler.data.serialization.WorksheetArgsSerializer
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgs

// TODO: move to compiler-shared
//  unify with serializers org.jetbrains.plugins.scala.compiler.data.serialization.ArgListSerializer
trait ArgumentsParser {
  /** @see [[org.jetbrains.plugins.scala.compiler.data.Arguments.asStrings]] */
  def parse(string: Seq[String]): Either[ArgumentsParserError, Arguments]
}

object ArgumentsParser
  extends ArgumentsParser {

  case class ArgumentsParserError(message: String) extends RuntimeException(message)

  override def parse(strings: Seq[String]): Either[ArgumentsParserError, Arguments] =
    SbtData.deserialize(strings).flatMap { case (sbtData, tail1) =>
      CompilerData.deserialize(tail1).flatMap { case (compilerData, tail2) =>
        tail2 match {
          case Seq(
            PathsToFiles(sources),
            PathsToFiles(classpath),
            PathToFile(output),
            StringToSequence(scalaOptions),
            StringToSequence(javaOptions),
            order,
            PathToFile(cacheFile),
            PathsToFiles(outputs),
            PathsToFiles(caches),
            PathsToFiles(sourceRoots),
            PathsToFiles(outputDirs),
            StringToSequence(worksheetArgsRaw),
            PathsToFiles(allSources),
            startDate,
            StringToBoolean(isCompile)
          ) =>
            val outputToCacheMap = outputs.zip(caches).toMap
            val outputGroups = sourceRoots zip outputDirs
            val zincData = ZincData(allSources, startDate.toLong, isCompile)
            val compilationData = CompilationData(
              sources,
              classpath,
              output,
              scalaOptions,
              javaOptions,
              CompileOrder.valueOf(order),
              cacheFile,
              outputToCacheMap,
              outputGroups,
              zincData
            )

            // this is actually not the best solution because we can't distinguish between empty sequence and absence of sequence,
            //  but will do for now, until we refactor communication protocol between client and server
            val worksheetArgs: Either[String, Option[WorksheetArgs]] =
              if (worksheetArgsRaw.isEmpty) Right(None)
              else WorksheetArgsSerializer.deserialize(worksheetArgsRaw) match {
                case Right(value) => Right(Option(value))
                case Left(errors) => Left(s"Couldn't parse worksheet arguments:\n${errors.mkString("\n")}")
              }

            worksheetArgs.map { args =>
              Arguments(sbtData, compilerData, compilationData, args)
            }
        }
      }
    }.left.map(ArgumentsParserError.apply)

  private val StringToBoolean: Extractor[String, Boolean] = _.toBoolean
}
