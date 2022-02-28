package org.jetbrains.plugins.scala.debugger.evaluation.evaluator.compiling

import com.intellij.openapi.util.io.FileUtil

import java.io.File
import java.net.URI

class OutputFileObject(file: File, val origName: String) {
  private def getUri(name: String): URI = {
    URI.create("memo:///" + name.replace('.', '/') + ".class")
  }

  def getName: String = getUri(origName).getPath
  def toByteArray: Array[Byte] = FileUtil.loadFileBytes(file)
}
