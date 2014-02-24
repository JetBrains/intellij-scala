package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings

import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.IndentType.IndentType

/**
 * @author Roman.Shein
 *         Date: 07.11.13
 */
class IndentInfo(val indentLength: Int, val indentRelativeToDirectParent: Boolean, val indentType: Option[IndentType]) {
  override def equals(other: Any): Boolean = {
    other match {
      case otherIndent: IndentInfo =>
        otherIndent.indentLength == indentLength &&
          otherIndent.indentRelativeToDirectParent == indentRelativeToDirectParent
      case _ => false
    }
  }
  override def hashCode: Int = indentLength.hashCode

  def setIndentType(indentType: IndentType) = new IndentInfo(indentLength, indentRelativeToDirectParent, Some(indentType))

  override def toString = "Indent: " + indentLength + " isRelative: " + indentRelativeToDirectParent + " type: " + indentType.map(_.toString).getOrElse("Unknown")
}

object IndentInfo {
  def apply(indentLength: Int, indentRelativeToDirectParent: Boolean): IndentInfo = new IndentInfo(indentLength, indentRelativeToDirectParent, None)
}