package org.jetbrains.plugins.scala.codeInspection.catchAll

import com.intellij.modcommand.{ActionContext, ModCommand, PsiBasedModCommandAction}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.types.AddOnlyStrategy
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScPattern, ScReferencePattern, ScWildcardPattern}

final class ReplaceDangerousCatchAllQuickFix(caseClause: ScCaseClause)
  extends PsiBasedModCommandAction[ScCaseClause](caseClause) {
  override def getFamilyName: String = ScalaBundle.message("specify.type.of.exception")

  override def perform(context: ActionContext, cc: ScCaseClause): ModCommand = cc.pattern match {
    case None => ModCommand.nop()
    case Some(pattern) =>
      ModCommand.psiUpdate(pattern, (pattern: ScPattern) => {
        val strategy = new AddOnlyStrategy
        pattern match {
          case p: ScWildcardPattern => strategy.patternWithoutType(p)
          case p: ScReferencePattern => strategy.patternWithoutType(p)
          //if the pattern has another type, it's a bug
        }
        ()
      })
  }
}
