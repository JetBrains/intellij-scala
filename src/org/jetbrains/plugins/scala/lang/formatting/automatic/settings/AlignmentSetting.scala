package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings

import com.intellij.formatting.Alignment

/**
 * @author Roman.Shein
 *         Date: 08.11.13
 */
class AlignmentSetting(val needAlignment: Boolean) {
  override def toString = "Need alignment: " + needAlignment

  override def equals(other: Any) = other match {
    case setting: AlignmentSetting => setting.needAlignment == needAlignment
    case _ => false
  }
}

object AlignmentSetting {
  def apply(needAlignment: Boolean) = new AlignmentSetting(needAlignment)
}
