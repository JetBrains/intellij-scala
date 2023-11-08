package org.jetbrains.plugins.scala.compiler.data

import org.jetbrains.jps.incremental.scala.Extractor
import org.jetbrains.plugins.scala.compiler.data.serialization.SerializationUtils

import java.nio.file.{Path, Paths}

private object Extractors {
  val stringToPath: Extractor[String, Path] = Paths.get(_)

  val stringToPaths: Extractor[String, Seq[Path]] = { paths =>
    if (paths.isEmpty) Seq.empty
    else paths.split(SerializationUtils.Delimiter).map(stringToPath).toSeq
  }
}
