package org.jetbrains.plugins.scala
package annotator

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.types.{Unit => UnitType, Any => AnyType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult}
import lang.psi.api.base.ScReferenceElement
import quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils._
import lang.psi.api.expr.{ScBlockExpr, ScCatchBlock, ScExpression, ScReturnStmt}

/**
 * Pavel.Fatin, 18.05.2010
 */

trait FunctionAnnotator {
  def annotateFunction(function: ScFunctionDefinition, holder: AnnotationHolder, highlightErrors: Boolean) {
    if (!function.hasExplicitType && !function.returnTypeIsDefined) {
      function.depthFirst.foreach {
        case ref: ScReferenceElement if ref.isReferenceTo(function) => {
          for (target <- ref.advancedResolve; if target.isApplicable) {
            val message = ScalaBundle.message("function.recursive.need.result.type", function.name)
            holder.createErrorAnnotation(ref, message)
          }
        }
        case _ =>
      }
    }

    checkImplicitParametersAndBounds(function, function.clauses, holder)

    for {
      functionType <- function.returnType
      if !function.isMacro
      usage <- function.getReturnUsages
      usageType <- typeOf(usage)
    } {
      val explicitType = function.hasExplicitType
      val unitType = functionType == UnitType

      val hasAssign = function.hasAssign
      val unitFunction = !hasAssign || unitType

      val explicitReturn = usage.isInstanceOf[ScReturnStmt]
      val emptyReturn = explicitReturn && usage.asInstanceOf[ScReturnStmt].expr.isEmpty
      val anyReturn = usageType == AnyType
      val underCatchBlock = usage.getContext.isInstanceOf[ScCatchBlock]

      if (explicitReturn && hasAssign && !explicitType) {
        needsTypeAnnotation()
      } else if (unitFunction && explicitReturn && !emptyReturn) {
        redundantReturnExpression()
      } else if (!unitFunction && !anyReturn && !underCatchBlock && !usageType.conforms(functionType)) {
        typeMismatch()
      }

      def needsTypeAnnotation() = {
        val message = ScalaBundle.message("function.must.define.type.explicitly", function.name)
        holder.createErrorAnnotation(usage.asInstanceOf[ScReturnStmt].returnKeyword, message)
      }

      def redundantReturnExpression() = {
        val message = ScalaBundle.message("return.expression.is.redundant", usageType.presentableText)
        holder.createWarningAnnotation(usage.asInstanceOf[ScReturnStmt].expr.get, message);
      }

      def typeMismatch() {
        if (highlightErrors) {
          val key = if (explicitReturn) "return.type.does.not.conform" else "return.expression.does.not.conform"
          val message = ScalaBundle.message(key, usageType.presentableText, functionType.presentableText)
          val returnExpression = if (explicitReturn) usage.asInstanceOf[ScReturnStmt].expr else None
          val expr = returnExpression.getOrElse(usage) match {
            case b: ScBlockExpr => b.getRBrace.map(_.getPsi).getOrElse(b)
            case expr => expr
          }
          val annotation = holder.createErrorAnnotation(expr, message)
          annotation.registerFix(ReportHighlightingErrorQuickFix)
        }
      }
    }
  }


  private def typeOf(element: PsiElement): TypeResult[ScType] = element match {
    case r: ScReturnStmt => r.expr match {
      case Some(e) => e.getTypeAfterImplicitConversion().tr
      case None => Success(UnitType, None)
    }
    case e: ScExpression => e.getTypeAfterImplicitConversion().tr
    case _ => Success(AnyType, None)
  }
}