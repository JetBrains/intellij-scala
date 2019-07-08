package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInspection.{LocalQuickFix, ProblemsHolder}
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

package object parentheses {

  private[codeInspection]
  def registerRedundantParensProblem(description: String, element: ScalaPsiElement, quickfix: LocalQuickFix)
                                    (implicit holder: ProblemsHolder, isOnTheFly: Boolean): Unit = {
    import com.intellij.codeInspection.ProblemHighlightType._

    if (isOnTheFly) {
      val left = TextRange.create(0, 1)
      val right = TextRange.create(element.getTextLength - 1, element.getTextLength)
      holder.registerProblem(element, description, INFORMATION, quickfix)
      holder.registerProblem(element, description, LIKE_UNUSED_SYMBOL, left)
      holder.registerProblem(element, description, LIKE_UNUSED_SYMBOL, right)
    } else {
      holder.registerProblem(element, description, WEAK_WARNING, quickfix)
    }
  }

}
