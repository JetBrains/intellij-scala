package org.jetbrains.plugins.scala.debugger.evaluation.evaluator.compiling

import org.jetbrains.annotations.ApiStatus

import java.net.URI
import java.nio.file.{Files, Path}

class OutputFileObject(file: Path, val origName: String) {
  private def getUri(name: String): URI = {
    URI.create("memo:///" + name.replace('.', '/') + ".class")
  }

  @deprecated(message = "Use origName.", since = "2026.1")
  @Deprecated(since = "2026.1", forRemoval = true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.2")
  def getName: String = getUri(origName).getPath

  def toByteArray: Array[Byte] = Files.readAllBytes(file)
}
