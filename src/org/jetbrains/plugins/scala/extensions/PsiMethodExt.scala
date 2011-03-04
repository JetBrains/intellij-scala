package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiType, PsiMethod}

/**
 * Pavel Fatin
 */

class PsiMethodExt(repr: PsiMethod) {
  private val AccessorNamePattern =
    """(?-i)(?:get|is|can|could|has|have|to)\p{Lu}.*""".r

  private val MutatorNamePattern =
    """(?-i)(?:do|add|remove|insert|delete|open|close|aquire|release)(?:\p{Lu}.*)""".r

  def isAccessor: Boolean = {
    hasQueryLikeName && !hasVoidReturnType
  }

  def isMutator: Boolean = {
    hasVoidReturnType || hasMutatorLikeName
  }

  def hasQueryLikeName = repr.getNameIdentifier.getText match {
    case AccessorNamePattern() => true
    case _ => false
  }

  def hasMutatorLikeName = repr.getNameIdentifier.getText match {
    case MutatorNamePattern() => true
    case _ => false
  }

  def hasVoidReturnType = repr.getReturnType() == PsiType.VOID
}