package org.jetbrains.plugins.scala.codeInspection.catchAll

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.types.AddOnlyStrategy
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScReferencePattern, ScWildcardPattern}

/**
 * @author Ksenia.Sautina
 * @since 6/25/12
 */

class ReplaceDangerousCatchAllQuickFix(caseClause: ScCaseClause)
        extends AbstractFixOnPsiElement(ScalaBundle.message("specify.type.of.exception"), caseClause) {

  override protected def doApplyFix(cc: ScCaseClause)
                                   (implicit project: Project): Unit = {
    val pattern = cc.pattern.orNull
    if (pattern == null) return

    val strategy = new AddOnlyStrategy
    pattern match {
      case p: ScWildcardPattern => strategy.wildcardPatternWithoutType(p)
      case p: ScReferencePattern => strategy.patternWithoutType(p)
      //if pattern has another type - it's a bug
    }
  }
}
