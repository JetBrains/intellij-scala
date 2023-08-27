package org.jetbrains.plugins.scala.annotator.element

import com.intellij.psi.{PsiClass, PsiField, PsiMethod}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.registerTypeMismatchError
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.codeInspection.varCouldBeValInspection.ValToVarQuickFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.types.{ExpectedTypeMismatch, MissedValueParameter, TypeMismatch, UnresolvedParameter, WrongTypeParameterInferred}
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor

object ScAssignmentAnnotator extends ElementAnnotator[ScAssignment] {

  override def annotate(element: ScAssignment, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {

    val left = element.leftExpression
    val right = element.rightExpression

    element.leftExpression match {
      case _: ScMethodCall =>
      case ref: ScReferenceExpression =>
        ref.bind() match {
          case Some(r) if r.isDynamic && r.name == DynamicResolveProcessor.UPDATE_DYNAMIC => //ignore
          case Some(r) if !r.isNamedParameter =>
            def checkVariable(): Unit = {
              left.`type`().foreach { lType =>
                right.foreach { expression =>
                  expression.getTypeAfterImplicitConversion().tr.foreach { rType =>
                    if(!ScalaPsiUtil.isUnderscoreEq(element, rType)) {
                      registerTypeMismatchError(rType, lType, expression)
                    }
                  }
                }
              }
            }
            r.element.nameContext match {
              case _: ScVariable =>
                if (!typeAware) return
                checkVariable()
              case c: ScClassParameter if c.isVar =>
                if (!typeAware) return
                checkVariable()
              case f: PsiField if !f.hasModifierProperty("final") =>
                if (!typeAware) return
                checkVariable()
              case fun: ScFunction if ScalaPsiUtil.isViableForAssignmentFunction(fun) =>
                if (!typeAware) return
                element.resolveAssignment match {
                  case Some(ra) =>
                    ra.problems.foreach {
                      case TypeMismatch(expression, expectedType) =>
                        expression.`type`().foreach {
                          registerTypeMismatchError(_, expectedType, expression)
                        }
                      case MissedValueParameter(_) => // simultaneously handled above
                      case UnresolvedParameter(_) => // don't show function inapplicability, unresolved
                      case WrongTypeParameterInferred => //todo: ?
                      case ExpectedTypeMismatch => // will be reported later
                      case _ => holder.createErrorAnnotation(element, ScalaBundle.message("annotator.error.wrong.right.assignment.side"))
                    }
                  case _ => holder.createErrorAnnotation(element, reassignementToVal)
                }
              case _: ScFunction => holder.createErrorAnnotation(element, reassignementToVal)
              case method: PsiMethod if method.getParameterList.getParametersCount == 0 =>
                method.containingClass match {
                  case c: PsiClass if c.isAnnotationType => //do nothing
                  case _ => holder.createErrorAnnotation(element, reassignementToVal)
                }
              case v: ScValue =>
                holder.createErrorAnnotation(element, reassignementToVal, new ValToVarQuickFix(v))
              case _ => holder.createErrorAnnotation(element, reassignementToVal)
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
