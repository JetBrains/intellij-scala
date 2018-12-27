package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/**
  * @author Alexander Podkhalyuzin
  */

trait ScGuard extends ScEnumerator {
  def expr: Option[ScExpression]

  def ifKeyword: PsiElement = findFirstChildByType(ScalaTokenTypes.kIF)

  override def accept(visitor: ScalaElementVisitor): Unit = visitor.visitGuard(this)
}