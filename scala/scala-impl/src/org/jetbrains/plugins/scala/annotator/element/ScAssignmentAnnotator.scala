package org.jetbrains.plugins.scala.annotator.element

import com.intellij.psi.{PsiClass, PsiField, PsiMethod}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.codeInspection.varCouldBeValInspection.ValToVarQuickFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScMethodCall, ScPrefixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.types.{ExpectedTypeMismatch, MissedValueParameter, TypeMismatch, UnresolvedParameter, WrongTypeParameterInferred}
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor
import org.jetbrains.plugins.scala.project.ProjectContext

object ScAssignmentAnnotator extends ElementAnnotator[ScAssignment] {

  override def annotate(element: ScAssignment, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    implicit val ctx: ProjectContext = element

    def annotateAssignmentResolve(): Unit =
      element.resolveAssignment match {
        case Some(ra) =>
          ra.problems.foreach {
            case TypeMismatch(_, _) => // Handled by ScExpressionAnnotator
            case MissedValueParameter(_) => // simultaneously handled above
            case UnresolvedParameter(_) => // don't show function inapplicability, unresolved
            case WrongTypeParameterInferred => //todo: ?
            case ExpectedTypeMismatch => // will be reported later
            case _ => holder.createErrorAnnotation(element, ScalaBundle.message("annotator.error.wrong.right.assignment.side"))
          }
        case _ => holder.createErrorAnnotation(element, reassignementToVal)
      }

    element.leftExpression match {
      case _: ScMethodCall =>
      case ref: ScReferenceExpression =>
        if (!typeAware)
          return

        ref.bind() match {
          case Some(r) if r.isDynamic && r.name == DynamicResolveProcessor.UPDATE_DYNAMIC => //ignore
          case Some(r) if !r.isNamedParameter =>
            r.element.nameContext match {
              case _: ScVariable =>
              case c: ScClassParameter if c.isVar =>
              case f: PsiField if !f.hasModifierProperty("final") =>
              case fun: ScFunction if ScalaPsiUtil.isViableForAssignmentFunction(fun) =>
                if (!typeAware) return
                annotateAssignmentResolve()
              case _: ScFunction => holder.createErrorAnnotation(element, reassignementToVal)
              case method: PsiMethod if method.getParameterList.getParametersCount == 0 =>
                method.containingClass match {
                  case c: PsiClass if c.annotationType => //do nothing
                  case _ => holder.createErrorAnnotation(element, reassignementToVal)
                }
              case v: ScValue =>
                holder.createErrorAnnotation(element, reassignementToVal, new ValToVarQuickFix(v))
              case _ => holder.createErrorAnnotation(element, reassignementToVal)
            }
          case _ =>
        }
      case ScPrefixExpr(operation, _) =>
        if (!typeAware)
          return

        operation.bind() match {
          case Some(r) =>
            r.element.nameContext match {
              case fun: ScFunction if ScalaPsiUtil.isViableForAssignmentFunction(fun) =>
                annotateAssignmentResolve()
              case _ =>
            }
          case _ =>
        }
      case _ =>
        element.assignmentToken.foreach(token =>
          holder.createErrorAnnotation(token, ScalaBundle.message("illegal.assignment.target"))
        )
    }
  }

  private def reassignementToVal: String = ScalaBundle.message("annotator.error.reassignment.to.val")
}
