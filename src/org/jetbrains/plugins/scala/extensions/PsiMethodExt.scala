package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiMethod

/**
 * Pavel Fatin
 */

class PsiMethodExt(repr: PsiMethod) {
  private val QueryNamePattern = """(?-i)(?:get|is)\p{Lu}.*""".r

  def isQuery: Boolean = {
    repr.getNameIdentifier.getText match {
      case QueryNamePattern() => true
      case _ => false
    }
  }
}