package org.jetbrains.plugins.scala.compiler.data

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.incremental.scala.remote.{NioPathTranslator, PathTranslator}

import java.nio.file.Path

final case class ComputeStampsArguments(
  outputFiles: Seq[Path],
  analysisFile: Path
) {
  @deprecated(message = "Use asStrings(PathTranslator). Kept for preserving binary compatibility.", since = "2026.1")
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.2")
  def asStrings: Seq[String] = asStrings(NioPathTranslator)

  def asStrings(translator: PathTranslator): Seq[String] = {
    import org.jetbrains.plugins.scala.compiler.data.serialization.SerializationUtils.sequenceToString
    val pathToString: Path => String = translator.translate
    val pathsToString: Seq[Path] => String = paths => sequenceToString(paths.map(pathToString))

    Seq(
      pathsToString(outputFiles),
      pathToString(analysisFile)
    )
  }
}

object ComputeStampsArguments {
  import Extractors.{StringToPath, StringToPaths}

  def parse(arguments: Seq[String]): Option[ComputeStampsArguments] = arguments match {
    case Seq(StringToPaths(outputFiles), StringToPath(analysisFile)) =>
      Some(ComputeStampsArguments(outputFiles, analysisFile))
    case _ =>
      None
  }
}
