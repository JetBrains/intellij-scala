package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.{PsiClass, PsiField, PsiMethod}
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.codeInspection.varCouldBeValInspection.ValToVarQuickFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{ScTypePresentation, TypeSystem}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.resolve.ResolvableReferenceExpression

/**
 * Pavel.Fatin, 31.05.2010
 */

trait AssignmentAnnotator {
  def annotateAssignment(assignment: ScAssignStmt, holder: AnnotationHolder, advancedHighlighting: Boolean)
                        (implicit typeSystem: TypeSystem = assignment.typeSystem) {
    val left = assignment.getLExpression
    val right = assignment.getRExpression

    assignment.getLExpression match {
      case call: ScMethodCall =>
      case ref: ScReferenceExpression =>
        ref.bind() match {
          case Some(r) if r.isDynamic && r.name == ResolvableReferenceExpression.UPDATE_DYNAMIC => //ignore
          case Some(r) if !r.isNamedParameter =>
            def checkVariable() {
              left.getType(TypingContext.empty).foreach { lType =>
                right.foreach { expression =>
                  expression.getTypeAfterImplicitConversion().tr.foreach { rType =>
                    if(!rType.conforms(lType)) {
                      val (expectedText, actualText) = ScTypePresentation.different(lType, rType)
                      val message = ScalaBundle.message("type.mismatch.expected.actual", expectedText, actualText)
                      val annotation = holder.createErrorAnnotation(expression, message)
                      annotation.registerFix(ReportHighlightingErrorQuickFix)
                    }
                  }
                }
              }
            }
            ScalaPsiUtil.nameContext(r.element) match {
              case v: ScVariable =>
                if (!advancedHighlighting) return
                checkVariable()
              case c: ScClassParameter if c.isVar =>
                if (!advancedHighlighting) return
                checkVariable()
              case f: PsiField if !f.hasModifierProperty("final") =>
                if (!advancedHighlighting) return
                checkVariable()
              case fun: ScFunction if ScalaPsiUtil.isViableForAssignmentFunction(fun) =>
                if (!advancedHighlighting) return
                assignment.resolveAssignment match {
                  case Some(ra) =>
                    ra.problems.foreach {
                      case TypeMismatch(expression, expectedType) =>
                        if (expression != null)
                          for (t <- expression.getType(TypingContext.empty)) {
                            //TODO show parameter name
                            val (expectedText, actualText) = ScTypePresentation.different(expectedType, t)
                            val message = ScalaBundle.message("type.mismatch.expected.actual", expectedText, actualText)
                            val annotation = holder.createErrorAnnotation(expression, message)
                            annotation.registerFix(ReportHighlightingErrorQuickFix)
                          }
                        else {
                          //TODO investigate case when expression is null. It's possible when new Expression(ScType)
                        }
                      case MissedValueParameter(_) => // simultaneously handled above
                      case UnresolvedParameter(_) => // don't show function inapplicability, unresolved
                      case WrongTypeParameterInferred => //todo: ?
                      case ExpectedTypeMismatch => // will be reported later
                      case _ => holder.createErrorAnnotation(assignment, "Wrong right assignment side")
                    }
                  case _ => holder.createErrorAnnotation(assignment, "Reassignment to val")
                }
              case f: ScFunction => holder.createErrorAnnotation(assignment, "Reassignment to val")
              case method: PsiMethod if method.getParameterList.getParametersCount == 0 =>
                method.containingClass match {
                  case c: PsiClass if c.isAnnotationType => //do nothing
                  case _ => holder.createErrorAnnotation(assignment, "Reassignment to val")
                }
              case v: ScValue =>
                val annotation = holder.createErrorAnnotation(assignment, "Reassignment to val")
                annotation.registerFix(new ValToVarQuickFix(ScalaPsiUtil.nameContext(r.element).asInstanceOf[ScValue]))
              case _ => holder.createErrorAnnotation(assignment, "Reassignment to val")
            }
          case _ =>
        }
      case _ =>
    }
  }
}