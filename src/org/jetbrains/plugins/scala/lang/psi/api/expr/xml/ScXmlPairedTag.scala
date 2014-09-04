package org.jetbrains.plugins.scala
package lang.psi.api.expr.xml

import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTokenType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/**
 * User: Dmitry Naydanov
 * Date: 4/9/12
 */

trait ScXmlPairedTag extends ScalaPsiElement{
  def getTagName = findChildrenByType(XmlTokenType.XML_NAME).headOption.map(_.getText).getOrElse(null)
  def getTagNameElement: PsiElement = findChildrenByType(XmlTokenType.XML_NAME).headOption.getOrElse(null)
  def getMatchedTag: ScXmlPairedTag
}
