package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.quickfix.modifiers.AddModifierQuickFix
import org.jetbrains.plugins.scala.annotator.quickfix.{AddReturnTypeFix, RemoveElementQuickFix, ReportHighlightingErrorQuickFix}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScTypeExt, ScTypesExt}

/**
 * Pavel.Fatin, 18.05.2010
 */

trait FunctionAnnotator {
  self: ScalaAnnotator =>

  def annotateFunction(function: ScFunctionDefinition, holder: AnnotationHolder, typeAware: Boolean) {
    if (!function.hasExplicitType && function.definedReturnType.isLeft) {
      function.recursiveReferences.foreach { ref =>
          val message = ScalaBundle.message("function.recursive.need.result.type", function.name)
          holder.createErrorAnnotation(ref.element, message)
      }
    }

    val tailrecAnnotation = function.annotations.find {
      _.typeElement.`type`().toOption
        .map(_.canonicalText).contains("_root_.scala.annotation.tailrec")
    }

    tailrecAnnotation.foreach { it =>
      if (!function.canBeTailRecursive) {
        val annotation = holder.createErrorAnnotation(function.nameId,
          "Method annotated with @tailrec is neither private nor final (so can be overridden)")
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

    for {
      functionType <- function.returnType
      usage <- function.returnUsages
      usageType <- typeOf(usage)
    } {

      val explicitType = function.hasExplicitType
      val unitType = functionType == Unit

      val hasAssign = function.hasAssign
      val unitFunction = !hasAssign || unitType

      val explicitReturn = usage.isInstanceOf[ScReturn]
      val emptyReturn = explicitReturn && usage.asInstanceOf[ScReturn].expr.isEmpty
      val anyReturn = usageType.isAny
      val underCatchBlock = usage.getContext.isInstanceOf[ScCatchBlock]

      if (explicitReturn && hasAssign && !explicitType) {
        needsTypeAnnotation()
      } else if (unitFunction && explicitReturn && !emptyReturn) {
        redundantReturnExpression()
      } else if (!unitFunction && !anyReturn && !underCatchBlock && !usageType.conforms(functionType)) {
        typeMismatch()
      }

      def needsTypeAnnotation(): Unit = {
        val message = ScalaBundle.message("function.must.define.type.explicitly", function.name)
        val returnTypes = function.returnUsages.collect {
          case retStmt: ScReturn => retStmt.expr.flatMap(_.`type`().toOption).getOrElse(Any)
          case expr: ScExpression => expr.`type`().getOrAny
        }
        val annotation = holder.createErrorAnnotation(usage.asInstanceOf[ScReturn].returnKeyword, message)
        annotation.registerFix(new AddReturnTypeFix(function, returnTypes.toSeq.lub()))
      }

      def redundantReturnExpression() = {
        val message = ScalaBundle.message("return.expression.is.redundant", usageType.presentableText)
        holder.createWarningAnnotation(usage.asInstanceOf[ScReturn].expr.get, message)
      }

      def typeMismatch() {
        if (typeAware) {
          val (usageTypeText, functionTypeText) = ScTypePresentation.different(usageType, functionType)
          val message = ScalaBundle.message("type.mismatch.found.required", usageTypeText, functionTypeText)
          val returnExpression = if (explicitReturn) usage.asInstanceOf[ScReturn].expr else None
          val expr = returnExpression.getOrElse(usage) match {
            case b: ScBlockExpr => b.getRBrace.map(_.getPsi).getOrElse(b)
            case b: ScBlock =>
              b.getParent match {
                case t: ScTryBlock =>
                  t.getRBrace match {
                    case Some(brace) => brace.getPsi
                    case _ => b
                  }
                case _ => b
              }
            case e => e
          }
          val annotation = holder.createErrorAnnotation(expr, message)
          annotation.registerFix(ReportHighlightingErrorQuickFix)
        }
      }
    }
  }

  private def typeOf(element: PsiElement) = (element match {
    case returnStmt: ScReturn => (returnStmt.expr, Unit)
    case _ => (Some(element), Any)
  }) match {
    case (Some(expression: ScExpression), _) => expression.getTypeAfterImplicitConversion().tr
    case (_, default) => Right(default)
  }
}