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
import com.intellij.psi.{PsiNamedElement, PsiElement}
import org.jetbrains.plugins.scala.extensions._

/**
 * Pavel.Fatin, 31.05.2010
 */

trait AssignmentAnnotator {
  private def isReadonly(e: PsiElement): Boolean = {
    if(e.isInstanceOf[ScClassParameter]) {
      return e.asInstanceOf[ScClassParameter].isVal
    }
      
    if(e.isInstanceOf[ScParameter]) {
      return true
    }

    val parent = e.getParent
    
    if(parent.isInstanceOf[ScGenerator] || 
            parent.isInstanceOf[ScEnumerator] || 
            parent.isInstanceOf[ScCaseClause]) {
      return true
    }
      
    e.parentsInFile.takeWhile(!_.isScope).findByType(classOf[ScPatternDefinition]).isDefined
  }

  def annotateAssignment(assignment: ScAssignStmt, holder: AnnotationHolder, advancedHighlighting: Boolean) {
    if (assignment.getContext.isInstanceOf[ScArgumentExprList]) return // named argument
    
    val l = assignment.getLExpression
    val r = assignment.getRExpression

    if (l.isInstanceOf[ScMethodCall]) return // map(x) = y

    val ref: Option[PsiElement] = l.asOptionOf[ScReferenceElement].flatMap(_.resolve.toOption)
    val reassignment = ref.find(isReadonly).isDefined

    for {
      lref <- l.asOptionOf[ScReferenceElement].toList
      result <- lref.multiResolve(false)
      sresult <- result.asOptionOf[ScalaResolveResult].toList
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