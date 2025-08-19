package org.jetbrains.plugins.scala.structuralSearch.filter

import com.intellij.dupLocator.util.NodeFilter
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

class MatchingVariableFilter extends NodeFilter {
  // add all elements that should be matched by a variable
  override def accepts(element: PsiElement): Boolean = element.is[ScTypedDefinition, ScExpression, ScLiteral, ScTypeDefinition]
}
