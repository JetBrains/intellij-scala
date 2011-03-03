package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiMethod

/**
 * Pavel Fatin
 */

class PsiMethodExt(repr: PsiMethod) {
  private val QueryNamePattern = """(?:get|is)\p{Lu}.*""".r

  def isQuery: Boolean = {
    repr.getName match {
      case QueryNamePattern => true
      case _ => false
    }
  }
}