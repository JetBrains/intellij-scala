package org.jetbrains.plugins.scala.extensions.implementation

import com.intellij.psi.{PsiType, PsiMethod}

/**
 * Pavel Fatin
 */

class PsiMethodExt(repr: PsiMethod) {
  private val AccessorNamePattern =
    """(?-i)(?:get|is|can|could|has|have|to)\p{Lu}.*""".r

  private val MutatorNamePattern =
    """(?-i)(?:do|set|add|remove|insert|delete|aquire|release|update)(?:\p{Lu}.*)""".r

  def isAccessor: Boolean = {
    hasQueryLikeName && !hasVoidReturnType
  }

  def isMutator: Boolean = {
    hasVoidReturnType || hasMutatorLikeName
  }

  private def getNameIdentifierText: String = {
    val id = repr.getNameIdentifier
    if (id != null) id.getText else ""
  }

  def hasQueryLikeName = {
    getNameIdentifierText match {
      case "getInstance" => false // TODO others?
      case AccessorNamePattern() => true
      case _ => false
    }
  }

  def hasMutatorLikeName = getNameIdentifierText match {
    case MutatorNamePattern() => true
    case _ => false
  }

  def hasVoidReturnType = repr.getReturnType == PsiType.VOID
}