package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import api.base.patterns.{ScPatternArgumentList, ScPattern, ScBindingPattern}
import api.statements.{ScFunctionDefinition, ScVariable}
import com.intellij.psi.{PsiElement}
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import lang.TokenSets
import lexer.ScalaTokenTypes
import com.intellij.lang.ASTNode

abstract class ScBindingPatternImpl(node: ASTNode) extends ScPatternImpl(node) with ScBindingPattern {
  def nameId = findChildByType(TokenSets.ID_SET)

  def isWildcard = findChildByType(ScalaTokenTypes.tUNDER) != null

  override def getUseScope = {
    val func = PsiTreeUtil.getContextOfType(this, classOf[ScFunctionDefinition], true)
    if (func != null) new LocalSearchScope(func) else super.getUseScope
  }

  protected def getEnclosingVariable = {
    def goUpper(e: PsiElement): Option[ScVariable] = e match {
      case _ : ScPattern => goUpper(e.getParent)
      case _ : ScPatternArgumentList => goUpper(e.getParent)
      case v: ScVariable => Some(v)
      case _ => None
    }

    goUpper(this)
  }

  override def isStable = getEnclosingVariable match {
    case None => true
    case _ => false
  }
}