package org.jetbrains.jps.incremental.scala.remote

import java.nio.file.Path

/**
 * A canonical path translator.
 */
object NioPathTranslator extends PathTranslator {
  override def translate(path: Path): String =
    path.toAbsolutePath.normalize().toString
}
