package org.jetbrains.plugins.scala.codeInspection.syntacticClarification

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.syntacticClarification.VariableNullInitializerInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api._

class VariableNullInitializerInspection extends AbstractInspection(Name) {
  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Unit] = {
    case definition: ScVariableDefinition if !definition.isLocal =>
      if (definition.declaredType.exists(isApplicable)) {
        definition.expr.filter(e => e.isValid && isNull(e)).foreach { expression =>
          holder.registerProblem(expression, Name, new NullToUnderscoreQuickFix(definition))
        }
      }
  }
}

private object VariableNullInitializerInspection {
  private[syntacticClarification] val Name = ScalaInspectionBundle.message("convert.null.initializer.to.underscore")

  private def isApplicable(`type`: ScType): Boolean = {
    `type` match {
      case t if t.isUnit => true
      case _: ValType => false
      case _ => true
    }
  }

  private def isNull(expr: ScExpression): Boolean =
    Option(expr)
      .flatMap(_.firstChild)
      .flatMap(element => Option(element.getNode))
      .map(_.getElementType)
      .exists {
        case ScalaTokenTypes.kNULL => true
        case _ => false
      }

  private class NullToUnderscoreQuickFix(definition: ScVariableDefinition) extends AbstractFixOnPsiElement(Name, definition) {
    override protected def doApplyFix(element: ScVariableDefinition)(implicit project: Project): Unit =
      element.expr
        .filter(isNull)
        .foreach(_.replace(createExpressionFromText("_")))
  }
}
