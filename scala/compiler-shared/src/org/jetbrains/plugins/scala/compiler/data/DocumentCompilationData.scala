package org.jetbrains.plugins.scala.compiler.data

import org.jetbrains.jps.incremental.scala.Extractor
import org.jetbrains.plugins.scala.compiler.data.serialization.SerializationUtils

import java.nio.file.Path

final case class DocumentCompilationData(
  sourcePath: Path,
  sourceContent: String,
  output: Path,
  classpath: Seq[Path],
  scalacOptions: Seq[String]
)

object DocumentCompilationData {

  import Extractors.{stringToPath, stringToPaths}

  def serialize(data: DocumentCompilationData): Seq[String] = {
    val DocumentCompilationData(sourcePath, sourceContent, output, classpath, scalacOptions) = data

    import serialization.SerializationUtils._

    Seq(
      pathToPathString(sourcePath),
      sourceContent,
      pathToPathString(output),
      pathsToPathStrings(classpath),
      sequenceToString(scalacOptions)
    )
  }

  def deserialize(strings: Seq[String]): Option[DocumentCompilationData] = strings match {
    case Seq(
      stringToPath(sourcePath),
      sourceContent,
      stringToPath(output),
      stringToPaths(classpath),
      stringToSequence(scalacOptions)
    ) => Some(DocumentCompilationData(sourcePath, sourceContent, output, classpath, scalacOptions))
    case _ => None
  }

  private val stringToSequence: Extractor[String, Seq[String]] = SerializationUtils.stringToSequence
}
