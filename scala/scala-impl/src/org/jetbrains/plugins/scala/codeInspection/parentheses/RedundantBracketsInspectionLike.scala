package org.jetbrains.plugins.scala
package codeInspection
package parentheses

import com.intellij.codeInspection.{LocalQuickFix, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait RedundantBracketsInspectionLike {

  def registerRedundantParensProblem(description: String, element: ScalaPsiElement, quickFix: LocalQuickFix)
                                    (implicit holder: ProblemsHolder, isOnTheFly: Boolean): Unit = {
    if (isOnTheFly) {
      val left = TextRange.create(0, 1)
      val right = TextRange.create(element.getTextLength - 1, element.getTextLength)
      holder.registerProblem(element, description, ProblemHighlightType.INFORMATION, quickFix)
      holder.registerProblem(element, description, ProblemHighlightType.LIKE_UNUSED_SYMBOL, left)
      holder.registerProblem(element, description, ProblemHighlightType.LIKE_UNUSED_SYMBOL, right)
    } else {
      holder.registerProblem(element, description, ProblemHighlightType.WEAK_WARNING, quickFix)
    }
  }

}
