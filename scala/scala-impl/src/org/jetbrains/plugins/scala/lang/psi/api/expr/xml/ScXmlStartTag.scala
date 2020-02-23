package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr
package xml

/**
* @author Alexander Podkhalyuzin
* Date: 21.04.2008
*/

trait ScXmlStartTag extends ScXmlPairedTag {
  def getClosingTag: ScXmlEndTag = {
    if (getParent != null && getParent.getLastChild.isInstanceOf[ScXmlEndTag]) {
      return getParent.getLastChild.asInstanceOf[ScXmlEndTag]
    }
    null
  }

  override def getMatchedTag: ScXmlPairedTag = getClosingTag
}