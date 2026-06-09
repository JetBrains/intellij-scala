package org.jetbrains.plugins.scala.project

import com.intellij.openapi.util.io

import java.io._
import scala.util.Using

package object template {

  import io.FileUtil._

  // TODO: SCL-23312
  def usingTempFile[T](prefix: String, suffix: String = null)(block: File => T): T = {
    val file = createTempFile(prefix, suffix, true)
    try {
      block(file)
    } finally {
      file.delete()
    }
  }

  // TODO: SCL-23312
  def usingTempDirectory[T](prefix: String)(block: File => T): T = {
    val directory = createTempDirectory(prefix, null, true)
    try {
      block(directory)
    } finally {
      delete(directory)
    }
  }

  // TODO: SCL-23312
  def writeLinesTo(file: File)
                  (lines: String*): Unit = {
    Using.resource(new PrintWriter(new FileWriter(file))) { writer =>
      lines.foreach(writer.println)
      writer.flush()
    }
  }
}
