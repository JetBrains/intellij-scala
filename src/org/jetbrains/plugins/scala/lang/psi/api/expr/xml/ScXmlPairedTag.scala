package org.jetbrains.plugins.scala
package lang.psi.api.expr.xml

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/**
 * User: Dmitry Naydanov
 * Date: 4/9/12
 */

trait ScXmlPairedTag extends ScalaPsiElement{
  def getTagName = findChildrenByType(ScalaXmlTokenTypes.XML_NAME).headOption.map(_.getText).orNull
  def getTagNameElement: PsiElement = findChildrenByType(ScalaXmlTokenTypes.XML_NAME).headOption.orNull
  def getMatchedTag: ScXmlPairedTag
}
