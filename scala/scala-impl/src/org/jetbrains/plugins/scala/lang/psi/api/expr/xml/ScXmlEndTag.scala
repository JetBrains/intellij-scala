package org.jetbrains.plugins.scala.lang.psi.api.expr
package xml

trait ScXmlEndTag extends ScXmlPairedTag {
  def getOpeningTag: ScXmlStartTag = {
    val parent = getParent
    if (parent != null) {
      parent.getFirstChild match {
        case tag: ScXmlStartTag => tag
        case _ => null
      }
    } else null
  }

  override def getMatchedTag: ScXmlPairedTag = getOpeningTag
}
