package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiType, PsiMethod}

/**
 * Pavel Fatin
 */

class PsiMethodExt(repr: PsiMethod) {
  private val QueryNamePattern = """(?-i)(?:get|is|can|has)\p{Lu}.*""".r

  def isQuery: Boolean = {
    hasQueryLikeName && !hasVoidReturnType
  }

  def isModifier: Boolean = {
    !hasQueryLikeName && hasVoidReturnType
  }

  private def hasQueryLikeName = repr.getNameIdentifier.getText match {
    case QueryNamePattern() => true
    case _ => false
  }

  def hasVoidReturnType = repr.getReturnType() == PsiType.VOID
}