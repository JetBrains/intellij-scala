package org.jetbrains.sbt

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.{Files, Path}
import scala.util.Using

private object PathTestUtil {
  private def deleteRecursively(path: Path): Unit = {
    if (path.isDirectory)
      path.children().foreach(deleteRecursively)
    Files.deleteIfExists(path)
  }

  implicit val tempPathReleasable: Using.Releasable[Path] = deleteRecursively
}
