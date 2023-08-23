package org.jetbrains.plugins.scala.lang.psi.api.expr
package xml

trait ScXmlEndTag extends ScXmlPairedTag {
  def getOpeningTag: ScXmlStartTag = {
    if (getParent != null && getParent.getFirstChild.isInstanceOf[ScXmlStartTag]) {
      return getParent.getFirstChild.asInstanceOf[ScXmlStartTag]
    }
    null
  }

  override def getMatchedTag: ScXmlPairedTag = getOpeningTag
}