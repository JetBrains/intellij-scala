package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.element.ScExpressionAnnotator.checkExpressionType
import org.jetbrains.plugins.scala.annotator.element.ScTypedExpressionAnnotator.mismatchRangesIn
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScFunctionExpr, ScParenthesisedExpr, ScTypedExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType.isFunctionType
import org.jetbrains.plugins.scala.util.SAMUtil.toSAMType

object ScFunctionExprAnnotator extends ElementAnnotator[ScFunctionExpr] {

  override def annotate(literal: ScFunctionExpr, typeAware: Boolean)(implicit holder: ScalaAnnotationHolder): Unit = {
    if (!typeAware || isImplicitlyConverted(literal)) {
      return
    }

    val parameters = literal.parameters

    val problemWithParameters = expectedFunctionTypeOf(literal).exists {
      case FunctionType(_, expectedTypes) =>
        missingParametersIn(literal, parameters, expectedTypes) ||
          tooManyParametersIn(literal, parameters, expectedTypes) ||
          parameterTypeMismatchIn(parameters, expectedTypes)
      case _ => false
    } || missingParameterTypeIn(parameters)

    if (!problemWithParameters) {
      resultTypeMismatchIn(literal)
    }
  }

  private def isImplicitlyConverted(literal: ScFunctionExpr) =
    (literal.`type`().toOption, literal.getTypeWithoutImplicits().toOption) match {
      case (Some(t1), Some(t2)) if t1.equiv(t2) => false
      case _ => true
    }

  private def expectedFunctionTypeOf(literal: ScFunctionExpr) = literal.expectedType() match {
    case Some(t @ FunctionType(_, _)) => Some(t)
    case Some(t) => toSAMType(t, literal)
    case _ => None
  }

  private def missingParametersIn(literal: ScFunctionExpr, parameters: Seq[ScParameter], expectedTypes: Seq[ScType])(implicit holder: ScalaAnnotationHolder): Boolean = {
    val missing = parameters.length < expectedTypes.length
    if (missing) {
      val startElement = if (parameters.isEmpty) literal.leftParen.getOrElse(literal.params) else parameters.last

      val errorRange = startElement.nextElementNotWhitespace
        .map(nextElement => new TextRange(startElement.getTextRange.getEndOffset - 1, nextElement.getTextOffset + 1))
        .getOrElse(startElement.getTextRange)

      val message = (if (expectedTypes.length - parameters.length == 1) "Missing parameter: " else "Missing parameters: ") +
        expectedTypes.drop(parameters.length).map(_.presentableText(literal)).mkString(", ")

      holder.createErrorAnnotation(errorRange, message)
    }
    missing
  }

  private def tooManyParametersIn(literal: ScFunctionExpr, parameters: Seq[ScParameter], expectedTypes: Seq[ScType])(implicit holder: ScalaAnnotationHolder): Boolean = {
    val tooMany = parameters.length > expectedTypes.length
    if (tooMany) {
      val message = ScalaBundle.message("annotator.error.too.many.parameters")
      if (!literal.hasParentheses) {
        holder.createErrorAnnotation(parameters.head, message)
      } else {
        val firstExcessiveParameter = parameters(expectedTypes.length)

        val range = new TextRange(
          firstExcessiveParameter.prevElementNotWhitespace.getOrElse(literal.params).getTextRange.getEndOffset - 1,
          firstExcessiveParameter.getTextOffset + 1)

        holder.createErrorAnnotation(range, message)
      }
    }
    tooMany
  }

  private def parameterTypeMismatchIn(parameters: Seq[ScParameter], expectedTypes: Seq[ScType])(implicit holder: ScalaAnnotationHolder): Boolean = {
    var typeMismatch = false
    parameters.zip(expectedTypes).iterator.takeWhile(_ => !typeMismatch).foreach { case (parameter, expectedType) =>
      parameter.typeElement.flatMap(_.`type`().toOption).filter(!expectedType.conforms(_)).foreach { _ =>
        val message = ScalaBundle.message("type.mismatch.expected", expectedType.presentableText(parameter), parameter.typeElement.get.getText)
        val ranges = mismatchRangesIn(parameter.typeElement.get, expectedType)(parameter)
        ranges.foreach(holder.createErrorAnnotation(_, message).registerFix(ReportHighlightingErrorQuickFix))
        typeMismatch = true
      }
    }
    typeMismatch
  }

  private def missingParameterTypeIn(parameters: Seq[ScParameter])(implicit holder: ScalaAnnotationHolder): Boolean = {
    var missing = false
    parameters.iterator.takeWhile(_ => !missing).foreach { parameter =>
      if (parameter.typeElement.isEmpty && parameter.expectedParamType.isEmpty) {
        holder.createErrorAnnotation(parameter, ScalaBundle.message("annotator.error.missing.parameter.type"))
        missing = true
      }
    }
    missing
  }

  private def resultTypeMismatchIn(literal: ScFunctionExpr)(implicit holder: ScalaAnnotationHolder): Unit = {
    val typeAscription = literal match {
      case Parent((_: ScParenthesisedExpr | _: ScBlockExpr) && Parent(ta: ScTypedExpression)) => Some(ta)
      case _ => None
    }

    typeAscription match {
      case Some(ascription) => ScTypedExpressionAnnotator.doAnnotate(ascription)
      case None =>
        val inMultilineBlock = literal match {
          case Parent(b: ScBlockExpr) => b.textContains('\n')
          case _ => false
        }
        if (!inMultilineBlock && literal.expectedType().exists(isFunctionType)) {
          literal.result.foreach(checkExpressionType(_, typeAware = true, fromFunctionLiteral = true))
        } else {
          checkExpressionType(literal, typeAware = true, fromFunctionLiteral = true)
        }
    }
  }
}
