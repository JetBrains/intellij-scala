package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScGuardImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScGuard with ScEnumeratorImpl {

  override def toString: String = "Guard"

  override def enumeratorToken: Option[PsiElement] =
    findFirstChildByType(ScalaTokenTypes.kIF)
}