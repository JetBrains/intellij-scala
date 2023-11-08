package org.jetbrains.plugins.scala.compiler.data

import org.jetbrains.plugins.scala.compiler.data.serialization.SerializationUtils

import java.nio.file.Path

final case class ComputeStampsArguments(
  outputFiles: Seq[Path],
  analysisFile: Path
) {
  def asStrings: Seq[String] = {
    Seq(
      SerializationUtils.pathsToPathStrings(outputFiles),
      SerializationUtils.pathToPathString(analysisFile)
    )
  }
}

object ComputeStampsArguments {
  import Extractors.{stringToPath, stringToPaths}

  def parse(arguments: Seq[String]): Option[ComputeStampsArguments] = arguments match {
    case Seq(stringToPaths(outputFiles), stringToPath(analysisFile)) =>
      Some(ComputeStampsArguments(outputFiles, analysisFile))
    case _ =>
      None
  }
}
