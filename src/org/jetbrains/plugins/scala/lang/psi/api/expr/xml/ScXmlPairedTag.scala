package org.jetbrains.plugins.scala
package lang.psi.api.expr.xml

import lang.psi.ScalaPsiElement
import com.intellij.psi.xml.XmlTokenType
import com.intellij.psi.PsiElement

/**
 * User: Dmitry Naydanov
 * Date: 4/9/12
 */

trait ScXmlPairedTag extends ScalaPsiElement{
  def getTagName = findChildrenByType(XmlTokenType.XML_NAME).headOption.map(_.getText).getOrElse(null)
  def getTagNameElement: PsiElement = findChildrenByType(XmlTokenType.XML_NAME).headOption.getOrElse(null)
  def getMatchedTag: ScXmlPairedTag
}
