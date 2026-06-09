package org.jetbrains.plugins.scala.project

import com.intellij.openapi.util.io

import java.io.File

package object template {

  import io.FileUtil._

  // TODO: SCL-23312
  def usingTempDirectory[T](prefix: String)(block: File => T): T = {
    val directory = createTempDirectory(prefix, null, true)
    try {
      block(directory)
    } finally {
      delete(directory)
    }
  }
}
