package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import com.intellij.navigation.NavigationItem
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import statements.{ScFunctionDefinition, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import com.intellij.psi.PsiElement

trait ScBindingPattern extends ScPattern with ScNamedElement with ScTypedDefinition with NavigationItem {
  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  def isWildcard: Boolean

  override def getUseScope = {
    val func = PsiTreeUtil.getContextOfType(this, classOf[ScFunctionDefinition], true)
    if (func != null) new LocalSearchScope(func) else getManager.asInstanceOf[PsiManagerEx].getFileManager.getUseScope(this)
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