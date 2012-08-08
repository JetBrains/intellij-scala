package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.types._
import quickfix.ReportHighlightingErrorQuickFix
import result.TypingContext
import lang.psi.api.statements.{ScVariable, ScValue, ScFunction}
import lang.psi.api.statements.params.ScClassParameter
import lang.psi.api.expr._
import codeInspection.varCouldBeValInspection.ValToVarQuickFix
import lang.psi.ScalaPsiUtil
import com.intellij.psi.{PsiClass, PsiMethod, PsiField}
import extensions.toPsiMemberExt

/**
 * Pavel.Fatin, 31.05.2010
 */

trait AssignmentAnnotator {
  def annotateAssignment(assignment: ScAssignStmt, holder: AnnotationHolder, advancedHighlighting: Boolean) {
    val left = assignment.getLExpression
    val right = assignment.getRExpression

    assignment.getLExpression match {
      case call: ScMethodCall =>
      case ref: ScReferenceExpression =>
        ref.bind() match {
          case Some(r) if !r.isNamedParameter =>
            def checkVariable() {
              left.getType(TypingContext.empty).foreach { lType =>
                right.foreach { expression =>
                  expression.getTypeAfterImplicitConversion().tr.foreach { rType =>
                    if(!rType.conforms(lType)) {
                      val annotation = holder.createErrorAnnotation(expression,
                        "Type mismatch, expected: %s, actual: %s".format(lType.presentableText, rType.presentableText))
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
              case fun: ScFunction if fun.paramClauses.clauses.length == 0 =>
                if (!advancedHighlighting) return
                assignment.resolveAssignment match {
                  case Some(r) =>
                    r.problems.foreach {
                      case TypeMismatch(expression, expectedType) =>
                        if (expression != null)
                          for (t <- expression.getType(TypingContext.empty)) {
                            //TODO show parameter name
                            val annotation = holder.createErrorAnnotation(expression,
                              "Type mismatch, expected: " + expectedType.presentableText + ", actual: " + t.presentableText)
                            annotation.registerFix(ReportHighlightingErrorQuickFix)
                          }
                        else {
                          //TODO investigate case when expression is null. It's possible when new Expression(ScType)
                        }
                      case MissedValueParameter(_) => // simultaneously handled above
                      case UnresolvedParameter(_) => // don't show function inapplicability, unresolved
                      case WrongTypeParameterInferred => //todo: ?
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