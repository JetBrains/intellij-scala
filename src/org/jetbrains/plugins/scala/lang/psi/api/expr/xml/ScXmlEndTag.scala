package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr
package xml

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.xml.XmlTokenType
import com.intellij.psi.{PsiElement, PsiElementVisitor}

/**
* @author Alexander Podkhalyuzin
* Date: 21.04.2008
*/

trait ScXmlEndTag extends ScXmlPairedTag {
  def getOpeningTag: ScXmlStartTag = {
    if (getParent != null && getParent.getFirstChild.isInstanceOf[ScXmlStartTag]) {
      return getParent.getFirstChild.asInstanceOf[ScXmlStartTag]
    }
    null
  }

  def getMatchedTag: ScXmlPairedTag = getOpeningTag
}