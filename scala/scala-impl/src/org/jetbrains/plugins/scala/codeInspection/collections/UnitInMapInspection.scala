package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScExpression, ScFunctionExpr}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Nikolay.Tropin
  */
final class UnitInMapInspection extends OperationOnCollectionInspection {

  import UnitInMapInspection._

  override protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case MethodRepr(call, _, Some(ref), Seq(arg@lambdaWithBody(body))) if ref.refName == "map" &&
      checkResolve(ref, getLikeCollectionClasses) =>

      for {
        expression <- ScExpression.calculateReturns(body)
        argumentType = arg.`type`().getOrAny

        quickFixes = if (isFixable(call)) Seq(new ChangeReferenceNameQuickFix(ref))
        else Seq.empty

        if hasUnitReturnType(expression, argumentType)(call.projectContext)
      } holder.registerProblem(
        expression,
        InspectionBundle.message("expression.unit.return.in.map"),
        highlightType,
        quickFixes: _*
      )
  }

  override def possibleSimplificationTypes: Array[SimplificationType] = Array.empty
}

object UnitInMapInspection {

  private object lambdaWithBody {

    def unapply(expression: ScExpression): Option[ScExpression] = expression match {
      case ScBlock(ScFunctionExpr(_, result)) => result
      case ScFunctionExpr(_, result) => result
      case _ => Some(expression)
    }
  }

  private def isFixable(call: ScExpression) = call.getParent match {
    case _: ScBlock |
         _: ScTemplateBody |
         _: ScEarlyDefinitions |
         _: ScalaFile => true
    case _ => false
  }

  private def hasUnitReturnType(expression: ScExpression,
                                argumentType: ScType)
                               (implicit context: ProjectContext) =
    expression.getTextLength > 0 &&
      expression.isPhysical &&
      expression.`type`().exists {
        case scType@api.FunctionType(returnType, _) =>
          returnType.isUnit &&
            argumentType.equiv(scType)
        case scType => scType.isUnit
      }

  private class ChangeReferenceNameQuickFix(reference: ScReferenceElement)
    extends AbstractFixOnPsiElement(
      InspectionBundle.message("use.foreach.instead.of.map"),
      reference
    ) {

    override protected def doApplyFix(reference: ScReferenceElement)
                                     (implicit project: Project): Unit =
      reference.handleElementRename("foreach")
  }

}