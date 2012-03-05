package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr
package xml

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.xml.XmlTokenType

/**
* @author Alexander Podkhalyuzin
* Date: 21.04.2008
*/

trait ScXmlStartTag extends ScalaPsiElement {
  def getTagName = findChildrenByType(XmlTokenType.XML_NAME).headOption.map(_.getText).getOrElse(null)

  def getClosingTag: ScXmlEndTag = {
    if (getParent != null && getParent.getLastChild.isInstanceOf[ScXmlEndTag]) {
      return getParent.getLastChild.asInstanceOf[ScXmlEndTag]
    }
    null
  }
}