package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings

import com.intellij.formatting.WrapType

/**
 * @author Roman.Shein
 *         Date: 08.11.13
 */
class WrapSetting(val wrapDefined: Boolean, val wrapType: Option[WrapType]) {
  override def toString = "Wrap: " + wrapDefined + " wrap type: " + wrapType.fold("Unknown")(_.toString)

  override def equals(other: Any) = other match {
    case setting: WrapSetting => setting.wrapDefined == wrapDefined && setting.wrapType == wrapType
    case _ => false
  }
}

object WrapSetting {
  def apply(needWrap: Boolean) = new WrapSetting(needWrap, None)
}