package org.jetbrains.plugins.scala.codeInspection.catchAll

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.types.Update
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScReferencePattern, ScWildcardPattern}

/**
 * @author Ksenia.Sautina
 * @since 6/25/12
 */

class ReplaceDangerousCatchAllQuickFix(caseClause: ScCaseClause)
        extends AbstractFixOnPsiElement(ScalaBundle.message("specify.type.of.exception"), caseClause) {
  def doApplyFix(project: Project) {
    val cc = getElement
    if (!cc.isValid) return

    val pattern = cc.pattern.orNull
    if (pattern == null) return

    pattern match {
      case p: ScWildcardPattern => Update.addToWildcardPattern(p)
      case p: ScReferencePattern => Update.addToPattern(p)
      //if pattern has another type - it's a bug
    }
  }
}
