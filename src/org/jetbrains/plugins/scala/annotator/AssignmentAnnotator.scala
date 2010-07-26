package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.types._
import result.TypingContext
import lang.psi.api.statements.{ScPatternDefinition, ScValue, ScFunction}
import com.intellij.psi.PsiElement
import lang.psi.api.toplevel.typedef.ScClass
import lang.psi.api.statements.params.{ScClassParameter, ScParameterClause, ScParameter}
import lang.psi.api.base.patterns.ScCaseClause
import lang.psi.api.expr._

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
      
    e.parentsInFile.findByType(classOf[ScPatternDefinition]).isDefined
  }

  def annotateAssignment(assignment: ScAssignStmt, holder: AnnotationHolder, advancedHighlighting: Boolean) {
    if (assignment.getContext.isInstanceOf[ScArgumentExprList]) return // named argument
    
    val l = assignment.getLExpression
    val r = assignment.getRExpression

    if (l.isInstanceOf[ScMethodCall]) return // map(x) = y

    val reassignment = l.asOptionOf(classOf[ScReferenceElement]).flatMap(_.resolve.toOption).find(isReadonly).isDefined            
    
    if(reassignment) {
      holder.createErrorAnnotation(assignment, "Reassignment to val")
      return
    }

    if(!advancedHighlighting) 
      return

    l.getType(TypingContext.empty).foreach { lType =>
      r.foreach { expression =>
        expression.getTypeAfterImplicitConversion().tr.foreach { rType =>
          if(!rType.conforms(lType)) 
            holder.createErrorAnnotation(expression, 
              "Type mismatch, expected: %s, actual: %s".format(lType.presentableText, rType.presentableText))
        }
      }
    }
  }
}