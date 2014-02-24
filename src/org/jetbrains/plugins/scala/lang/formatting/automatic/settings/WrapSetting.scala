package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings

import com.intellij.formatting.WrapType

/**
 * @author Roman.Shein
 *         Date: 08.11.13
 */
class WrapSetting(val needWrap: Boolean, val wrapType: Option[WrapType]) {
  override def toString = "Wrap: " + needWrap + " wrap type: " + wrapType.map(_.toString).getOrElse("Unknown")

  override def equals(other: Any) = other match {
    case setting: WrapSetting => setting.needWrap == needWrap && setting.wrapType == wrapType
    case _ => false
  }
}

object WrapSetting {
  def apply(needWrap: Boolean) = new WrapSetting(needWrap, None)
}