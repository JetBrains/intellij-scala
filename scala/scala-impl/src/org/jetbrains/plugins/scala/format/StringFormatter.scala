package org.jetbrains.plugins.scala
package format

trait StringFormatter {
  def format(parts: Seq[StringPart]): String
}
