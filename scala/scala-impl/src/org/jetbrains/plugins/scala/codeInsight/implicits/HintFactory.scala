package org.jetbrains.plugins.scala.codeInsight.implicits

import java.awt.event.MouseEvent
import java.awt.{Component, Point}

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.editor.colors.{CodeInsightColors, EditorColors, EditorFontType}
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.codeInsight.implicits.presentation.{Button, Presentation, PresentationFactory}
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

private class HintFactory(editor: EditorImpl) {
  private val Ellipsis: String = "..."

  private val factory = new PresentationFactory(editor)
  private val font = editor.getColorsScheme.getFont(EditorFontType.PLAIN)
  private val scheme = editor.getColorsScheme

  import factory._

  // TODO use single Hint?
  def implicitConversionHint(e: ScExpression, conversion: ScalaResolveResult): Seq[Hint] = {
    val (leftParen, rightParen) = parentheses
    Seq(Hint(sequence(namedBasicPresentation(conversion), leftParen), e, suffix = false, menu = Some(menu.ImplicitConversion)),
      Hint(sequence(rightParen, collapsedPresentationOf(conversion.implicitParameters)), e, suffix = true, menu = Some(menu.ImplicitArguments)))
  }

  def implicitArgumentsHint(e: ImplicitArgumentsOwner, arguments: Seq[ScalaResolveResult]): Seq[Hint] =
    Seq(Hint(presentationOf(arguments), e, suffix = true, menu = Some(menu.ImplicitArguments)))

  def explicitImplicitArgumentsHint(args: ScArgumentExprList): Seq[Hint] =
    Seq(Hint(text(".explicitly"), args, suffix = false, menu = Some(menu.ExplicitArguments)))

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
      val textAttributes = if (problems.nonEmpty) foldedAttributes + errorAttributes else foldedAttributes
      val folding = expansion(expandedPresentationOf(arguments, parentheses = false),
        attributes(_ + textAttributes, text(Ellipsis)))

      inParentheses(withTooltip(notFoundTooltip(problems), folding))
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
    val delegate = result.element.asOptionOf[ScFunction].flatMap(_.getSyntheticNavigationElement).getOrElse(result.element)
    val tooltip = ScalaDocumentationProvider.getQuickNavigateInfo(delegate, result.substitutor)
    withNavigation(delegate, Some(tooltip), text(result.name))
  }

  private def contextMenu(id: String, presentation: Presentation) = {
    val handler = (e: MouseEvent) => {
      CustomActionsSchema.getInstance.getCorrectedAction(id) match {
        case group: ActionGroup =>
          val popupMenu = ActionManager.getInstance.createActionPopupMenu(ActionPlaces.EDITOR_POPUP, group)
          val point = locationAt(e, editor.getContentComponent)
          popupMenu.getComponent.show(editor.getContentComponent, point.x, point.y)
          e.consume()
        case _ =>
      }
    }
    onClick(handler, Button.Right, presentation)
  }

  private def locationAt(e: MouseEvent, component: Component): Point = {
    val pointOnScreen = component.getLocationOnScreen
    new Point(e.getXOnScreen - pointOnScreen.x, e.getYOnScreen - pointOnScreen.y)
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
          attributes(_ + likeWrongReference, text("?"))),
        text(typeAnnotation(parameter))))
  }

  private def collapsedProblemPresentation(parameter: ScalaResolveResult, probableArgs: Seq[(ScalaResolveResult, FullInfoResult)]): Presentation = {
    val errorTooltip =
      if (probableArgs.size > 1) ambiguousTooltip(parameter)
      else notFoundTooltip(parameter)

    val ellipsis = {
      val result = text(Ellipsis)
      if (parameter.isImplicitParameterProblem) attributes(_ + errorAttributes, result) else result
    }

    val presentation =
      if (!ImplicitHints.enabled) ellipsis else sequence(ellipsis, text(typeAnnotation(parameter)))

    expansion(expandedProblemPresentation(parameter, probableArgs),
      withTooltip(errorTooltip, attributes(_ + foldedAttributes, presentation)))
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
      .intersperse(attributes(_ + likeWrongReference, text(" | "))): _*))
  }

  private def presentationOfProbable(argument: ScalaResolveResult, result: FullInfoResult): Presentation = {
    result match {
      case OkResult =>
        namedBasicPresentation(argument)

      case ImplicitParameterNotFoundResult =>
        val (leftParen, rightParen) = parentheses
        sequence(namedBasicPresentation(argument) +:
          leftParen +:
          argument.implicitParameters.map(parameter => presentationOf(parameter)).intersperse(text(", ")) :+
          rightParen: _*)

      case DivergedImplicitResult =>
        withTooltip("Implicit is diverged", attributes(_ + errorAttributes, namedBasicPresentation(argument)))

      case CantInferTypeParameterResult =>
        withTooltip("Can't infer proper types for type parameters", attributes(_ + errorAttributes, namedBasicPresentation(argument)))
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

  private def errorAttributes = scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)

  private def likeWrongReference =
    Option(scheme.getAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)).getOrElse(new TextAttributes())

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

  // Add custom colors for folding inside inlay hints (SCL-13996)?
  private def adjusted(attributes: TextAttributes): TextAttributes = {
    val result = attributes.clone()
    if (UIUtil.isUnderDarcula && result.getBackgroundColor != null) {
      result.setBackgroundColor(result.getBackgroundColor.brighter)
    }
    result
  }

  // TODO
  private def foldedAttributes: TextAttributes =
    Option(scheme.getAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES))
      .map(adjusted)
      .getOrElse(new TextAttributes())

  private def inParentheses(presentation: Presentation): Presentation = {
    val (left, right) = parentheses
    sequence(left, presentation, right)
  }

  // TODO
  private def parentheses: (Presentation, Presentation) = {
    val asMatch = (it: Presentation) => attributes(_ + scheme.getAttributes(CodeInsightColors.MATCHED_BRACE_ATTRIBUTES), it)
    synchronous(asMatch, text("("), text(")"))
  }

  // TODO
  private def text(s: String): Presentation = factory.text(s, scheme.getFont)
}
