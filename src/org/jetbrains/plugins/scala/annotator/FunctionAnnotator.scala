package org.jetbrains.plugins.scala
package annotator

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReturnStmt}
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.types.{Unit => UnitType, Any => AnyType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult}
import lang.psi.api.base.ScReferenceElement

/**
 * Pavel.Fatin, 18.05.2010
 */

trait FunctionAnnotator {
  def annotateFunction(function: ScFunctionDefinition, holder: AnnotationHolder, highlightErrors: Boolean) {
    var recursive = false

    if (function.hasAssign && !function.hasExplicitType) {
      function.depthFirst.foreach {
        case ref: ScReferenceElement if ref.isReferenceTo(function) => {
          val message = ScalaBundle.message("function.recursive.need.result.type", function.getName)
          holder.createErrorAnnotation(ref, message)
          recursive = true
        }
        case _ =>
      }
    }

    for (functionType <- function.returnType;
         usage <- function.getReturnUsages;
         usageType <- typeOf(usage)) {

      val explicitType = function.hasExplicitType
      val unitType = functionType == UnitType

      val hasAssign = function.hasAssign
      val unitFunction = !hasAssign || unitType

      val explicitReturn = usage.isInstanceOf[ScReturnStmt]
      val unitReturn = usageType == UnitType
      val anyReturn = usageType == AnyType

      if (explicitReturn && hasAssign && !explicitType) {
        needsTypeAnnotation()
      } else if (unitFunction && explicitReturn && !unitReturn) {
        redundantReturnExpression()
      } else if (!unitFunction && !anyReturn && !usageType.conforms(functionType)) {
        typeMismatch()
      }

      def needsTypeAnnotation() = {
        val message = ScalaBundle.message("function.must.define.type.explicitly", function.getName)
        holder.createErrorAnnotation(usage.asInstanceOf[ScReturnStmt].returnKeyword, message)
      }

      def redundantReturnExpression() = {
        val message = ScalaBundle.message("return.expression.is.redundant", usageType.presentableText)
        holder.createWarningAnnotation(usage.asInstanceOf[ScReturnStmt].expr.get, message);
      }

      def typeMismatch() = {
        if (highlightErrors) {
          val key = if (explicitReturn) "return.type.does.not.conform" else "return.expression.does.not.conform"
          val message = ScalaBundle.message(key, usageType.presentableText, functionType.presentableText)
          val returnExpression = if (explicitReturn) usage.asInstanceOf[ScReturnStmt].expr else None
          holder.createErrorAnnotation(returnExpression.getOrElse(usage), message)
        }
      }
    }
  }

  private def typeOf(element: PsiElement): TypeResult[ScType] = element match {
    case r: ScReturnStmt => r.expr match {
      case Some(e) => e.getTypeAfterImplicitConversion()._1
      case None => Success(UnitType, None)
    }
    case e: ScExpression => e.getTypeAfterImplicitConversion()._1
    case _ => Success(AnyType, None)
  }
}