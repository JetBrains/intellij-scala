package org.jetbrains.plugins.scala
package format

/**
 * Pavel Fatin
 */

trait StringFormatter {
  def format(parts: Seq[StringPart]): String
}
