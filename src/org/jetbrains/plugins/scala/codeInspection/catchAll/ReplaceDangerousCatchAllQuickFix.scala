package org.jetbrains.plugins.scala.codeInspection.catchAll

import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInsight.intention.types.Update
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScReferencePattern, ScWildcardPattern}

/**
 * @author Ksenia.Sautina
 * @since 6/25/12
 */

class ReplaceDangerousCatchAllQuickFix(caseClause: ScCaseClause) extends LocalQuickFix {
  def getName = "Specify type of exception"

  def getFamilyName = "Specify type of exception"

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!caseClause.isValid) return

    val pattern = caseClause.pattern.getOrElse(null)
    if (pattern == null) return

    pattern match {
      case p: ScWildcardPattern => Update.addToWildcardPattern(p)
      case p: ScReferencePattern => Update.addToPattern(p)
      //if pattern has another type - it's a bug
    }
  }
}
