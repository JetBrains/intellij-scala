package org.jetbrains.plugins.scala.debugger.evaluation.evaluator.compiling

import com.intellij.platform.eel.fs.EelFiles

import java.nio.file.Path

class OutputFileObject(file: Path, val origName: String) {
  //noinspection ApiStatus,UnstableApiUsage
  def toByteArray: Array[Byte] = EelFiles.readAllBytes(file)
}
