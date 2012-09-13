package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.types.{Unit => UnitType, Any => AnyType}
import quickfix.modifiers.AddModifierQuickFix
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils._
import lang.psi.types.result.{TypingContext, Success, TypeResult}
import lang.psi.api.statements.ScFunctionDefinition
import lang.psi.api.expr._
import com.intellij.psi.PsiElement
import quickfix.{ReportHighlightingErrorQuickFix, RemoveElementQuickFix}

/**
 * Pavel.Fatin, 18.05.2010
 */

trait FunctionAnnotator {
  def annotateFunction(function: ScFunctionDefinition, holder: AnnotationHolder, typeAware: Boolean) {
    if (!function.hasExplicitType && !function.returnTypeIsDefined) {
      function.recursiveReferences.foreach { ref =>
          val message = ScalaBundle.message("function.recursive.need.result.type", function.name)
          holder.createErrorAnnotation(ref.element, message)
      }
    }

    val tailrecAnnotation = function.annotations.find(_.typeElement.getType(TypingContext.empty)
            .map(_.canonicalText).filter(_ == "_root_.scala.annotation.tailrec").isDefined)

    tailrecAnnotation.foreach { it =>
      if (!function.canBeTailRecursive) {
        val annotation = holder.createErrorAnnotation(function.nameId,
          "Method annotated with @tailrec is neither private nor final (so can be overriden)")
        annotation.registerFix(new AddModifierQuickFix(function, "private"))
        annotation.registerFix(new AddModifierQuickFix(function, "final"))
        annotation.registerFix(new RemoveElementQuickFix(it, "Remove @tailrec annotation"))
      }

      if (typeAware) {
        val recursiveReferences = function.recursiveReferences

        if (recursiveReferences.isEmpty) {
          val annotation = holder.createErrorAnnotation(function.nameId,
            "Method annotated with @tailrec contains no recursive calls")
          annotation.registerFix(new RemoveElementQuickFix(it, "Remove @tailrec annotation"))
        } else {
          recursiveReferences.filter(!_.isTailCall).foreach {
            ref =>
              val target = ref.element.getParent match {
                case call: ScMethodCall => call
                case _                  => ref.element
              }
              val annotation = holder.createErrorAnnotation(target,
                "Recursive call not in tail position (in @tailrec annotated method)")
              annotation.registerFix(new RemoveElementQuickFix(it, "Remove @tailrec annotation"))
          }
        }
      }
    }

    checkImplicitParametersAndBounds(function, function.clauses, holder)

    for (functionType <- function.returnType;
         usage <- function.getReturnUsages;
         usageType <- typeOf(usage)) {

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
        if (typeAware) {
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