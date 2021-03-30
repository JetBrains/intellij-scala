package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInspection.{LocalQuickFix, ProblemsHolder}
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

package object parentheses {

  private[codeInspection]
  def registerRedundantParensProblem(@Nls description: String, element: ScalaPsiElement, quickfix: LocalQuickFix)
                                    (implicit holder: ProblemsHolder, isOnTheFly: Boolean): Unit = {
    import com.intellij.codeInspection.ProblemHighlightType._

    if (isOnTheFly) {
      val left = TextRange.create(0, 1)
      val right = TextRange.create(element.getTextLength - 1, element.getTextLength)
      // TODO: shouldn't the highlighting level be taken from the inspection settings?
      holder.registerProblem(element, description, LIKE_UNUSED_SYMBOL, left, quickfix)
      holder.registerProblem(element, description, LIKE_UNUSED_SYMBOL, right, quickfix)
      if (element.getTextLength >= 4) { // for `(1)` it is enough to add quickfix only to parentheses
        val middle = TextRange.create(2, element.getTextLength - 2)
        holder.registerProblem(element, description, INFORMATION, middle, quickfix)
      }
    } else {
      holder.registerProblem(element, description, WEAK_WARNING, quickfix)
    }
  }

}
