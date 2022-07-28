package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScExpression, ScFunctionExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExpressionExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.immutable.ArraySeq

final class UnitInMapInspection extends OperationOnCollectionInspection {

  import UnitInMapInspection._

  override protected def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any] = {
    case MethodRepr(call, _, Some(ref), Seq(arg@lambdaWithBody(body))) if ref.refName == "map" &&
      checkResolve(ref, getLikeCollectionClasses) && expressionResultIsNotUsed(call) =>

      for {
        expression <- body.calculateTailReturns
        argumentType = arg.`type`().getOrAny

        quickFixes = if (isFixable(call)) Seq(new ChangeReferenceNameQuickFix(ref))
        else Seq.empty

        if hasUnitReturnType(expression, argumentType)(call.projectContext)
      } holder.registerProblem(
        expression,
        ScalaInspectionBundle.message("expression.unit.return.in.map"),
        highlightType,
        quickFixes: _*
      )
  }

  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq.empty
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
          isUnitLike(returnType) &&
            argumentType.equiv(scType)
        case scType => isUnitLike(scType)
      }

  private def isUnitLike(ty: ScType): Boolean =
    ty.isUnit || isUnitObjectType(ty)

  private def isUnitObjectType(ty: ScType): Boolean =
    ty.canonicalText == "_root_.scala.Unit.type"

  private class ChangeReferenceNameQuickFix(reference: ScReference)
    extends AbstractFixOnPsiElement(
      ScalaInspectionBundle.message("use.foreach.instead.of.map"),
      reference
    ) {

    override protected def doApplyFix(reference: ScReference)
                                     (implicit project: Project): Unit =
      reference.handleElementRename("foreach")
  }

}