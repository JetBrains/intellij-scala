package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import lang.resolve.ScalaResolveResult
import lang.psi.types.result.TypingContext
import quickfix.ReportHighlightingErrorQuickFix
import lang.psi.types._
import lang.psi.api.expr.ScConstrBlock
import com.intellij.codeInspection.ProblemHighlightType
import lang.psi.api.base.{ScPrimaryConstructor, ScConstructor}
import lang.psi.api.statements.ScFunction
import lang.psi.api.ScalaFile

trait ConstructorAnnotator {
  // TODO duplication with application annotator.
  def annotateConstructor(constructor: ScConstructor, holder: AnnotationHolder) {
    //in case if constructor is function
    constructor.reference match {
      case None => return
      case _ =>
    }
    val resolved = constructor.reference.toList.flatMap(_.resolveAllConstructors)

    resolved match {
      case List() =>
        holder.createErrorAnnotation(constructor.typeElement, "Cannot resolve constructor")
      case List(r: ScalaResolveResult) =>

        val missed = for (MissedValueParameter(p) <- r.problems) yield p.name + ": " + p.paramType.presentableText
        val argsElement = constructor.args.getOrElse(constructor.typeElement)
        if (!missed.isEmpty)
          holder.createErrorAnnotation(argsElement,
            "Unspecified value parameters: " + missed.mkString(", "))

        r.problems.foreach {
          case ExcessArgument(argument) =>
            holder.createErrorAnnotation(argument, "Too many arguments for constructor")
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
          case MalformedDefinition() =>
            holder.createErrorAnnotation(constructor.typeElement, "Constructor has malformed definition")
          case ExpansionForNonRepeatedParameter(expression) =>
            holder.createErrorAnnotation(expression, "Expansion for non-repeated parameter")
          case PositionalAfterNamedArgument(argument) =>
            holder.createErrorAnnotation(argument, "Positional after named argument")
          case ParameterSpecifiedMultipleTimes(assignment) =>
            holder.createErrorAnnotation(assignment.getLExpression, "Parameter specified multiple times")
          case WrongTypeParameterInferred => //todo: ?
          case _ => holder.createErrorAnnotation(argsElement, "Not applicable." /* TODO + signatureOf(f)*/)
        }
      case results =>
        holder.createErrorAnnotation(constructor.typeElement, "Cannot resolve constructor")
    }
  }

  def annotateAuxiliaryConstructor(constr: ScConstrBlock, holder: AnnotationHolder) {
    val selfInvocation = constr.selfInvocation
    selfInvocation match {
      case Some(self) =>
        self.bind match {
          case Some(c: ScPrimaryConstructor) => //it's ok
          case Some(fun: ScFunction) =>
            //check order
            if (fun.getTextRange.getStartOffset > constr.getTextRange.getStartOffset) {
              val annotation = holder.createErrorAnnotation(self,
                ScalaBundle.message("called.constructor.definition.must.precede"))
              annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            }
          case None =>
        }
      case None =>
        constr.getContainingFile match {
          case file: ScalaFile if !file.isCompiled=>
            val annotation = holder.createErrorAnnotation(constr,
              ScalaBundle.message("constructor.invocation.expected"))
            annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          case _ => //nothing to do in decompiled stuff
        }
    }
  }
}