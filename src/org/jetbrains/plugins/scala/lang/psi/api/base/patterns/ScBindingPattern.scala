package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import com.intellij.extapi.psi.ASTDelegatePsiElement
import com.intellij.navigation.NavigationItem
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.tree.{IElementType, TokenSet}
import lexer.ScalaTokenTypes
import com.intellij.psi.util.PsiTreeUtil
import statements.{ScFunctionDefinition, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTyped}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.types._

trait ScBindingPattern extends ScPattern with ScNamedElement with ScTyped with NavigationItem {
  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def calcType: ScType

  def isWildcard: Boolean

  override def getUseScope = {
    val func = PsiTreeUtil.getContextOfType(this, classOf[ScFunctionDefinition], true)
    if (func != null) new LocalSearchScope(func) else getUseScope
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