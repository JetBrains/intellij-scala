package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr
package xml

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.xml.XmlTokenType
import com.intellij.psi.PsiElement

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

  def getMatchedTag: ScXmlPairedTag = getClosingTag
}