package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class ScForBindingImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScForBinding with ScEnumeratorImpl with ScPatternedEnumeratorImpl {

  override def toString: String = "ForBinding"

  override def enumeratorToken: Option[PsiElement] =
    Option(findFirstChildByType(ScalaTokenTypes.tASSIGN))
}