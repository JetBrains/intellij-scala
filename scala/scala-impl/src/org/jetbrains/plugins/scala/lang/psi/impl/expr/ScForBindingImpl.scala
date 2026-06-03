package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScForBindingImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScForBinding with ScEnumeratorImpl with ScPatternedEnumeratorImpl {

  override def toString: String = "ForBinding"

  override def enumeratorToken: Option[PsiElement] =
    findFirstChildByType(ScalaTokenTypes.tASSIGN)
}