package org.jetbrains.plugins.scala.compiler.data

import org.jetbrains.jps.incremental.scala.Extractor
import org.jetbrains.plugins.scala.compiler.data.Extractors.{PathToFile, PathsToFiles}

import java.io.File

case class CompilerData(compilerJars: Option[CompilerJars],
                        javaHome: Option[File],
                        incrementalType: IncrementalityType)

object CompilerData {
  import serialization.SerializationUtils._

  def serialize(data: CompilerData): Seq[String] = {
    val compilerJarPaths = data.compilerJars.map(jars => filesToPaths(jars.allJars))
    val customCompilerBridgeJarPath = data.compilerJars.flatMap(_.customCompilerBridgeJar.map(fileToPath))
    val javaHomePath = data.javaHome.map(fileToPath)

    Seq(
      optionToString(compilerJarPaths),
      optionToString(customCompilerBridgeJarPath),
      optionToString(javaHomePath),
      data.incrementalType.name()
    )
  }

  def deserialize(strings: Seq[String]): Either[String, (CompilerData, Seq[String])] = strings match {
    case StringToOption(compilerJarPaths) +:
      StringToOption(customCompilerBridgeJarPath) +:
      StringToOption(javaHomePath) +:
      incrementalTypeName +:
      tail =>
      val compilerJars = compilerJarPaths.map {
        case PathsToFiles(files) =>
          val compilerBridgeJar = customCompilerBridgeJarPath.map(PathToFile)
          CompilerJarsFactory.fromFiles(files, compilerBridgeJar) match {
            case Left(resolveError) => return Left(s"Couldn't extract compiler jars from: ${files.mkString(";")}\n$resolveError")
            case Right(jars) => jars
          }
      }
      val javaHome = javaHomePath.map {
        case PathToFile(file) => file
      }
      val incrementalType = IncrementalityType.valueOf(incrementalTypeName)
      Right(CompilerData(compilerJars, javaHome, incrementalType) -> tail)

    case args => Left(s"The arguments don't match the expected shape of CompilerData: ${args.mkString("[", ",", "]")}")
  }

  private val StringToOption: Extractor[String, Option[String]] = { s =>
    if (s.isEmpty) None else Some(s)
  }
}
