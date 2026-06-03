package org.jetbrains.plugins.scala.lang.psi.api.expr
package xml

trait ScXmlStartTag extends ScXmlPairedTag {
  def getClosingTag: ScXmlEndTag = {
    val parent = getParent
    if (parent != null) {
      parent.getLastChild match {
        case tag: ScXmlEndTag => tag
        case _ => null
      }
    } else null
  }

  override def getMatchedTag: ScXmlPairedTag = getClosingTag
}
