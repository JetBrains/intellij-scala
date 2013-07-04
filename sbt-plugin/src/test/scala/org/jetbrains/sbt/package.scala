package org.jetbrains

import scala.io.Source
import java.io.File

/**
 * @author Pavel Fatin
 */
package object sbt {
  def read(file: File): Seq[String] = {
    val source = Source.fromFile(file)
    try {
      source.getLines().toList
    } finally {
      source.close()
    }
  }
}
