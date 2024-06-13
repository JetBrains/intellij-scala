package org.jetbrains.plugins.scala.compiler.data

import org.jetbrains.jps.incremental.scala.Extractor
import org.jetbrains.plugins.scala.compiler.data.serialization.SerializationUtils

import java.io.File
import java.nio.file.{Path, Paths}

object Extractors {
  val StringToPath: Extractor[String, Path] = Paths.get(_)

  val StringToPaths: Extractor[String, Seq[Path]] = { paths =>
    if (paths.isEmpty) Seq.empty
    else paths.split(SerializationUtils.Delimiter).map(StringToPath).toSeq
  }

  val PathToFile: Extractor[String, File] = new File(_)

  val PathsToFiles: Extractor[String, Seq[File]] = { paths =>
    if (paths.isEmpty) Seq.empty else paths.split(SerializationUtils.Delimiter).map(PathToFile).toSeq
  }

  val StringToSequence: Extractor[String, Seq[String]] = SerializationUtils.stringToSequence
}
