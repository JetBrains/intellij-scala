package org.jetbrains.plugins.scala
package lang.psi.api.expr.xml

import org.jetbrains.plugins.scala.lang.psi.api._


import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

/**
 * User: Dmitry Naydanov
 * Date: 4/9/12
 */

trait ScXmlPairedTagBase extends ScalaPsiElementBase { this: ScXmlPairedTag =>
  def getTagName: String = findChildrenByType(ScalaXmlTokenTypes.XML_NAME).headOption.map(_.getText).orNull
  def getTagNameElement: PsiElement = findChildrenByType(ScalaXmlTokenTypes.XML_NAME).headOption.orNull
  def getMatchedTag: ScXmlPairedTag
}