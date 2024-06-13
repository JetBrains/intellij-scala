package org.jetbrains.plugins.scala.compiler.data

import org.jetbrains.jps.incremental.scala.Extractor
import org.jetbrains.plugins.scala.compiler.data.serialization.SerializationUtils

import java.io.File

final case class DocumentCompilationArguments(
  sbtData: SbtData,
  compilerData: CompilerData,
  compilationData: DocumentCompilationData
)

object DocumentCompilationArguments {
  import org.jetbrains.plugins.scala.compiler.data.serialization.SerializationUtils._

  def serialize(arguments: DocumentCompilationArguments): Seq[String] = {
    val DocumentCompilationArguments(sbtData, compilerData, compilationData) = arguments

    val compilerJarPaths = compilerData.compilerJars.map(jars => filesToPaths(jars.allJars))
    val customCompilerBridgeJarPath = compilerData.compilerJars.flatMap(_.customCompilerBridgeJar.map(fileToPath))
    val javaHomePath = compilerData.javaHome.map(fileToPath)

    Seq(
      fileToPath(sbtData.pluginJpsDirectory.toFile),
      fileToPath(sbtData.interfacesHome),
      sbtData.javaClassVersion,
      optionToString(compilerJarPaths),
      optionToString(customCompilerBridgeJarPath),
      optionToString(javaHomePath)
    ) ++ DocumentCompilationData.serialize(compilationData)
  }

  def deserialize(strings: Seq[String]): Option[DocumentCompilationArguments] = strings match {
    case pathToFile(pluginJpsDirectory) +:
      pathToFile(interfacesHome) +:
      javaClassVersion +:
      stringToOption(compilerJarPaths) +:
      stringToOption(customCompilerBridgeJarPath) +:
      stringToOption(javaHomePath) +:
      compilationData =>

      val SbtData.Jars(sbtInterfaceJar, compilerInterfaceJar, compilerBridges) =
        SbtData.Jars.fromPluginJpsDirectory(pluginJpsDirectory.toPath)
      val sbtData = SbtData(sbtInterfaceJar, compilerInterfaceJar, compilerBridges, interfacesHome, javaClassVersion)

      val compilerJars = compilerJarPaths.map {
        case pathsToFiles(files) =>
          val compilerBridgeJar = customCompilerBridgeJarPath.map(pathToFile)
          CompilerJarsFactory.fromFiles(files, compilerBridgeJar) match {
            case Left(_) => return None
            case Right(jars) => jars
          }
      }

      val javaHome = javaHomePath.map {
        case pathToFile(file) => file
      }

      val compilerData = CompilerData(compilerJars, javaHome, IncrementalityType.IDEA)

      DocumentCompilationData.deserialize(compilationData).map { compilationData =>
        DocumentCompilationArguments(sbtData, compilerData, compilationData)
      }
    case _ => None
  }

  private val pathToFile: Extractor[String, File] = new File(_)

  private val pathsToFiles: Extractor[String, Seq[File]] = { paths =>
    if (paths.isEmpty) Seq.empty else paths.split(SerializationUtils.Delimiter).map(pathToFile).toSeq
  }

  private val stringToOption: Extractor[String, Option[String]] = { s =>
    if (s.isEmpty) None else Some(s)
  }
}
