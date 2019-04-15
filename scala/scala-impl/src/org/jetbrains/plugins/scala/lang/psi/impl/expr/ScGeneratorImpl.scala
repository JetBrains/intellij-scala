package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.annotator.ScGeneratorAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
  * @author Alexander Podkhalyuzin
  *         Date: 07.03.2008
  */
class ScGeneratorImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScGenerator with ScGeneratorAnnotator {
  override def pattern: ScPattern = findChildByClass(classOf[ScPattern])

  override def expr: Option[ScExpression] = Option(findChildByClass(classOf[ScExpression]))

  override def valKeyword: Option[PsiElement] =
    Option(getNode.findChildByType(ScalaTokenTypes.kVAL)).map(_.getPsi)

  override def enumeratorToken: Option[PsiElement] =
    Option(findFirstChildByType(ScalaTokenTypes.tCHOOSE))

  override def toString: String = "Generator"
}