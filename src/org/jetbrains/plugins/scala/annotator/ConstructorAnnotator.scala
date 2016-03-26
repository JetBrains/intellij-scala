package org.jetbrains.plugins.scala
package annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScConstrBlock
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

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
        if (missed.nonEmpty)
          holder.createErrorAnnotation(argsElement,
            "Unspecified value parameters: " + missed.mkString(", "))

        r.problems.foreach {
          case ExcessArgument(argument) =>
            holder.createErrorAnnotation(argument, "Too many arguments for constructor")
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
          case MalformedDefinition() =>
            holder.createErrorAnnotation(constructor.typeElement, "Constructor has malformed definition")
          case ExpansionForNonRepeatedParameter(expression) =>
            holder.createErrorAnnotation(expression, "Expansion for non-repeated parameter")
          case PositionalAfterNamedArgument(argument) =>
            holder.createErrorAnnotation(argument, "Positional after named argument")
          case ParameterSpecifiedMultipleTimes(assignment) =>
            holder.createErrorAnnotation(assignment.getLExpression, "Parameter specified multiple times")
          case WrongTypeParameterInferred => //todo: ?
          case ExpectedTypeMismatch => //will be reported later
          case DefaultTypeParameterMismatch(expected, actual) => constructor.typeArgList match {
            case Some(tpArgList) =>
              val message: String = ScalaBundle.message("type.mismatch.default.args.expected.actual", expected, actual)
              holder.createErrorAnnotation(tpArgList, message)
            case _ =>
          }
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
          case _ =>
        }
      case None =>
        constr.getContainingFile match {
          case file: ScalaFile if !file.isCompiled =>
            val annotation = holder.createErrorAnnotation(constr,
              ScalaBundle.message("constructor.invocation.expected"))
            annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          case _ => //nothing to do in decompiled stuff
        }
    }
  }
}