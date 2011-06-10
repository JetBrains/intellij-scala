package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.types._
import quickfix.ReportHighlightingErrorQuickFix
import result.TypingContext
import lang.psi.api.statements.{ScPatternDefinition, ScValue, ScFunction}
import lang.psi.api.toplevel.typedef.ScClass
import lang.psi.api.statements.params.{ScClassParameter, ScParameterClause, ScParameter}
import lang.psi.api.base.patterns.ScCaseClause
import lang.psi.api.expr._
import codeInspection.varCouldBeValInspection.ValToVarQuickFix
import lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.extensions._
import collection.Iterable
import com.intellij.psi.{ResolveResult, PsiNamedElement, PsiElement}

/**
 * Pavel.Fatin, 31.05.2010
 */

trait AssignmentAnnotator {

  def annotateAssignment(assignment: ScAssignStmt, holder: AnnotationHolder, advancedHighlighting: Boolean) {
    val l = assignment.getLExpression
    val r = assignment.getRExpression

    if (l.isInstanceOf[ScMethodCall]) return // map(x) = y

    val ref: Option[PsiElement] = l.asOptionOf[ScReferenceElement].flatMap(_.resolve().toOption)
    val results: Iterable[ScalaResolveResult] =
      l.asOptionOf[ScReferenceElement].toSeq.flatMap(_.multiResolve(false)).flatMap(_.asOptionOf[ScalaResolveResult])
    val namedParam = results.find(_.isNamedParameter) != None
    if (namedParam) return
    val reassignment = ref.find(ScalaPsiUtil.isReadonly).isDefined

    if(reassignment) {
      val annotation = holder.createErrorAnnotation(assignment, "Reassignment to val")
      ref.get match {
        case named: PsiNamedElement if ScalaPsiUtil.nameContext(named).isInstanceOf[ScValue] =>
          annotation.registerFix(new ValToVarQuickFix(ScalaPsiUtil.nameContext(named).asInstanceOf[ScValue]))
        case _ =>
      }
      return
    }

    if(!advancedHighlighting) 
      return

    for {
      sresult <- results
      if sresult.isSetterFunction
    } {
      val problems = sresult.problems
      problems.foreach {
        case TypeMismatch(expression, expectedType) =>
          for (t <- expression.getType(TypingContext.empty)) {
            //TODO show parameter name
            val annotation = holder.createErrorAnnotation(expression,
              "Type mismatch, expected: " + expectedType.presentableText + ", actual: " + t.presentableText)
            annotation.registerFix(ReportHighlightingErrorQuickFix)
          }
        case _ =>
      }
      return
    }

    l.getType(TypingContext.empty).foreach { lType =>
      r.foreach { expression =>
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
}