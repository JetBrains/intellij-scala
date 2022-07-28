package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr
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