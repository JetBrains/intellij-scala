package org.jetbrains.plugins.scala.format

trait StringFormatter {
  def format(parts: Seq[StringPart]): String
}
