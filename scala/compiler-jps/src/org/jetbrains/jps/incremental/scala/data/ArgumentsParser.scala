package org.jetbrains.jps.incremental.scala.data

import java.io.File

import org.jetbrains.jps.incremental.scala.extractor
import org.jetbrains.plugins.scala.compiler.data.{Arguments, CompilationData, CompilerData, CompilerJarsFactory, SbtData, ZincData}
import org.jetbrains.plugins.scala.compiler.{CompileOrder, IncrementalityType}

trait ArgumentsParser {

  def parse(string: Seq[String]): Arguments
}

object ArgumentsParser
  extends ArgumentsParser {

  def parse(strings: Seq[String]): Arguments = strings match {
    case token +: Seq(
      PathToFile(sbtInterfaceJar),
      PathToFile(compilerInterfaceJar),
      PathToFile(scalaBridgeSourceJar_2_10),
      PathToFile(scalaBridgeSourceJar_2_11),
      PathToFile(scalaBridgeSourceJar_2_13),
      PathToFile(dottyBridgeJar_0_22),
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
        _0_22 = dottyBridgeJar_0_22
      )
      val compilerBridges = SbtData.CompilerBridges(scalaBridgeSources, dottyBridges)
      val sbtData = SbtData(sbtInterfaceJar, compilerInterfaceJar, compilerBridges, interfacesHome, javaClassVersion)

      val compilerJars = compilerJarPaths.map {
        case PathsToFiles(files) =>
          CompilerJarsFactory.fromFiles(files) match {
            case Left(error)  => throw new RuntimeException(s"Couldn't extract compiler jars from: ${files.mkString(";")}\n" + error.toString)
            case Right(value) => value
          }
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

  private val PathToFile = extractor[String, File] { path: String =>
    new File(path)
  }

  private val PathsToFiles = extractor[String, Seq[File]] { paths: String =>
    if (paths.isEmpty) Seq.empty else paths.split(Arguments.Delimiter).map(new File(_)).toSeq
  }

  private val StringToOption = extractor[String, Option[String]] { s: String =>
    if (s.isEmpty) None else Some(s)
  }

  private val StringToSequence = extractor[String, Seq[String]] { s: String =>
    if (s.isEmpty) Seq.empty else s.split(Arguments.Delimiter).toSeq
  }

  private val StringToBoolean = extractor[String, Boolean] { s: String =>
    s.toBoolean
  }
}
