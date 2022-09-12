package org.jetbrains.plugins.scala.lang.psi.api.expr
package xml

trait ScXmlStartTag extends ScXmlPairedTag {
  def getClosingTag: ScXmlEndTag = {
    if (getParent != null && getParent.getLastChild.isInstanceOf[ScXmlEndTag]) {
      return getParent.getLastChild.asInstanceOf[ScXmlEndTag]
    }
    null
  }

  override def getMatchedTag: ScXmlPairedTag = getClosingTag
}