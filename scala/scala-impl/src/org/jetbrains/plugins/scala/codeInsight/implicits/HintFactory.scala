package org.jetbrains.plugins.scala.codeInsight.implicits

import org.jetbrains.plugins.scala.codeInsight.implicits.presentation.{Presentation, PresentationFactory}
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

private class HintFactory(factory: PresentationFactory) {
  private val Ellipsis: String = "..."

  import factory._

  def implicitConversionHint(e: ScExpression, conversion: ScalaResolveResult): Seq[Hint] = {
    val (leftParen, rightParen) = parentheses
    Seq(Hint(contextMenu(menu.implicitConversion(e), sequence(namedBasicPresentation(conversion), leftParen)), e, suffix = false),
      Hint(contextMenu(menu.implicitArguments(e), sequence(rightParen, collapsedPresentationOf(conversion.implicitParameters))), e, suffix = true))
  }

  def implicitArgumentsHint(e: ImplicitArgumentsOwner, arguments: Seq[ScalaResolveResult]): Seq[Hint] =
    Seq(Hint(contextMenu(menu.implicitArguments(e), presentationOf(arguments)), e, suffix = true))

  def explicitImplicitArgumentsHint(e: ScArgumentExprList): Seq[Hint] =
    Seq(Hint(contextMenu(menu.explicitArguments(e), text(".explicitly")), e, suffix = false))

  private def presentationOf(arguments: Seq[ScalaResolveResult]): Presentation = {
    if (!ImplicitHints.enabled)
      collapsedPresentationOf(arguments)
    else
      expandedPresentationOf(arguments, parentheses = true)
  }

  private def collapsedPresentationOf(arguments: Seq[ScalaResolveResult]): Presentation =
    if (arguments.isEmpty) empty
    else {
      val problems = arguments.filter(_.isImplicitParameterProblem)
      inParentheses(
        withTooltip(notFoundTooltip(problems),
          withFolding(
            if (problems.isEmpty) text(Ellipsis) else asError(text(Ellipsis)),
            expandedPresentationOf(arguments, parentheses = false))))
    }

  private def expandedPresentationOf(arguments: Seq[ScalaResolveResult], parentheses: Boolean): Presentation =
    if (arguments.isEmpty) empty
    else {
      val presentation = sequence(arguments.map(it => presentationOf(it)).intersperse(text(", ")): _*)
      if (parentheses) inParentheses(presentation) else presentation
    }

  private def presentationOf(argument: ScalaResolveResult): Presentation =
    argument.isImplicitParameterProblem
      .option(problemPresentation(parameter = argument))
      .getOrElse(sequence(namedBasicPresentation(argument), collapsedPresentationOf(argument.implicitParameters)))

  private def namedBasicPresentation(result: ScalaResolveResult): Presentation = {
    val delegate = result.element match {
      case f: ScFunction => Option(f.syntheticNavigationElement).getOrElse(f)
      case element => element
    }
    val tooltip = ScalaDocumentationProvider.getQuickNavigateInfo(delegate, result.substitutor)
    withNavigation(delegate, Some(tooltip), text(result.name))
  }

  private def problemPresentation(parameter: ScalaResolveResult): Presentation = {
    probableArgumentsFor(parameter) match {
      case Seq() => noApplicableExpandedPresentation(parameter)
      case Seq((arg, result)) if arg.implicitParameters.isEmpty => presentationOfProbable(arg, result)
      case args => collapsedProblemPresentation(parameter, args)
    }
  }

  private def noApplicableExpandedPresentation(parameter: ScalaResolveResult): Presentation = {
    withTooltip(notFoundTooltip(parameter),
      sequence(
        withNavigation(parameter.element, None,
          asWrongReference(text("?"))),
        text(typeAnnotation(parameter))))
  }

  private def collapsedProblemPresentation(parameter: ScalaResolveResult, probableArgs: Seq[(ScalaResolveResult, FullInfoResult)]): Presentation = {
    val ellipsis =
      if (parameter.isImplicitParameterProblem) asError(text(Ellipsis)) else text(Ellipsis)

    withFolding(
      withTooltip(if (probableArgs.size > 1) ambiguousTooltip(parameter) else notFoundTooltip(parameter),
        if (ImplicitHints.enabled) sequence(ellipsis, text(typeAnnotation(parameter))) else ellipsis),
      expandedProblemPresentation(parameter, probableArgs))
  }

  private def expandedProblemPresentation(parameter: ScalaResolveResult, arguments: Seq[(ScalaResolveResult, FullInfoResult)]): Presentation = {
    arguments match {
      case Seq((arg, result)) => presentationOfProbable(arg, result)
      case _ => expandedAmbiguousPresentation(parameter, arguments)
    }
  }

  private def expandedAmbiguousPresentation(parameter: ScalaResolveResult, arguments: Seq[(ScalaResolveResult, FullInfoResult)]): Presentation = {
    withTooltip(ambiguousTooltip(parameter),
      sequence(arguments.map { case (argument, result) => presentationOfProbable(argument, result) }
      .intersperse(asWrongReference(text(" | "))): _*))
  }

  private def presentationOfProbable(argument: ScalaResolveResult, result: FullInfoResult): Presentation = {
    result match {
      case OkResult =>
        namedBasicPresentation(argument)

      case ImplicitParameterNotFoundResult =>
        sequence(
          namedBasicPresentation(argument),
          inParentheses(
            sequence(argument.implicitParameters.map(parameter => presentationOf(parameter)).intersperse(text(", ")): _*)))

      case DivergedImplicitResult =>
        withTooltip("Implicit is diverged", asError(namedBasicPresentation(argument)))

      case CantInferTypeParameterResult =>
        withTooltip("Can't infer proper types for type parameters", asError(namedBasicPresentation(argument)))
    }
  }

  //todo
  def isAmbiguous(parameter: ScalaResolveResult): Boolean =
    parameter.isImplicitParameterProblem && probableArgumentsFor(parameter).size > 1

  private def probableArgumentsFor(parameter: ScalaResolveResult): Seq[(ScalaResolveResult, FullInfoResult)] = {
    parameter.implicitSearchState.map { state =>
      val collector = new ImplicitCollector(state.copy(fullInfo = true))
      collector.collect().flatMap { r =>
        r.implicitReason match {
          case reason: FullInfoResult => Seq((r, reason))
          case _ => Seq.empty
        }
      }
    } getOrElse {
      Seq.empty
    }
  }

  private def typeAnnotation(parameter: ScalaResolveResult): String = {
    val paramType = parameter.implicitSearchState.map(_.presentableTypeText).getOrElse("NotInferred")
    s": $paramType"
  }

  private def paramWithType(parameter: ScalaResolveResult): String = parameter.name + typeAnnotation(parameter)

  private def notFoundTooltip(parameter: ScalaResolveResult): String =
    "No implicits found for parameter " + paramWithType(parameter)

  private def notFoundTooltip(parameters: Seq[ScalaResolveResult]): String = {
    parameters match {
      case Seq() => ""
      case Seq(p) => notFoundTooltip(p)
      case ps => "No implicits found for parameters " + ps.map(paramWithType).mkString(", ")
    }
  }

  private def ambiguousTooltip(parameter: ScalaResolveResult): String =
    "Ambiguous implicits for parameter " + paramWithType(parameter)

  private def inParentheses(presentation: Presentation): Presentation = {
    val (leftParen, rightParen) = parentheses
    sequence(leftParen, presentation, rightParen)
  }

  private def parentheses: (Presentation, Presentation) = {
    val Seq(leftParen, rightParen) = withMatching(text("("), text(")"))
    (leftParen, rightParen)
  }
}
