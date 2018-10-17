package org.jetbrains.plugins.scala.codeInsight.implicits

import java.awt.{Component, Point}
import java.awt.event.MouseEvent

import com.intellij.codeInsight.hint.{HintManager, HintManagerImpl, HintUtil}
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.{CodeInsightColors, EditorColors, EditorFontType}
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.{EffectType, TextAttributes}
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.ui.LightweightHint
import com.intellij.util.ui.{JBUI, UIUtil}
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
    Seq(Hint(text(".explicitly", font), args, suffix = false, menu = Some(menu.ExplicitArguments)))

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
        attributes(_ + textAttributes, text(Ellipsis, font)))

      inParentheses(folding.withErrorTooltipIfEmpty(notFoundTooltip(problems)))
    }

  private def expandedPresentationOf(arguments: Seq[ScalaResolveResult], parentheses: Boolean): Presentation =
    if (arguments.isEmpty) empty
    else {
      val presentation = sequence(arguments.map(it => presentationOf(it)).intersperse(text(", ", font)): _*)
      if (parentheses) inParentheses(presentation) else presentation
    }

  private def presentationOf(argument: ScalaResolveResult): Presentation =
    argument.isImplicitParameterProblem
      .option(problemPresentation(parameter = argument))
      .getOrElse(sequence(namedBasicPresentation(argument), collapsedPresentationOf(argument.implicitParameters)))

  private def namedBasicPresentation(result: ScalaResolveResult): Presentation = {
    val delegate = result.element.asOptionOf[ScFunction].flatMap(_.getSyntheticNavigationElement).getOrElse(result.element)
    val tooltip = ScalaDocumentationProvider.getQuickNavigateInfo(delegate, result.substitutor)
    val tooltipHander = tooltipHandler(tooltip)
    navigation(asHyperlink, tooltipHander, _ => navigateTo(delegate), text(result.name, font))
  }

  private def asHyperlink(presentation: Presentation): Presentation = {
    val as = scheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR).clone()
    as.setEffectType(EffectType.LINE_UNDERSCORE)
    attributes(_ + as, presentation)
  }

  private def navigateTo(e: PsiElement): Unit = {
    e.asOptionOf[Navigatable].filter(_.canNavigate).foreach { navigatable =>
      CommandProcessor.getInstance.executeCommand(e.getProject, navigatable.navigate(true), null, null)
    }
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

  private var hint: Option[LightweightHint] = None

  private def tooltipHandler(tooltip: String): Option[MouseEvent] => Unit = {
    case Some(e) =>
      if (hint.forall(!_.isVisible)) {
        hint = Some(showTooltip(editor, e, tooltip))
      }
    case None => hint.foreach { it =>
      it.hide()
      hint = None
    }
  }

  private def withTooltip(tooltip: String, presentation: Presentation): Presentation = {
    factory.onHover(tooltipHandler(tooltip), presentation)
  }

  private def showTooltip(editor: Editor, e: MouseEvent, text: String): LightweightHint = {
    val hint = {
      val label = HintUtil.createInformationLabel(text)
      label.setBorder(JBUI.Borders.empty(6, 6, 5, 6))
      new LightweightHint(label)
    }

    val constraint = HintManager.ABOVE

    val pointOnEditor = locationAt(e, editor.getContentComponent)

    val point = {
      val p = HintManagerImpl.getHintPosition(hint, editor, editor.xyToVisualPosition(pointOnEditor), constraint)
      p.x = e.getXOnScreen - editor.getContentComponent.getTopLevelAncestor.getLocationOnScreen.x
      p
    }

    val manager = HintManagerImpl.getInstanceImpl

    manager.showEditorHint(hint, editor, point,
      HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0, false,
      HintManagerImpl.createHintHint(editor, point, hint, constraint).setContentActive(false))

    hint
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
    val qMarkText = navigation(identity, _ => (), _ => navigateTo(parameter.element), attributes(_ + likeWrongReference, text("?", font)))
    val paramTypeSuffix = text(typeAnnotation(parameter), font)

    sequence(qMarkText, paramTypeSuffix)
      .withErrorTooltipIfEmpty(notFoundTooltip(parameter))
  }

  private def collapsedProblemPresentation(parameter: ScalaResolveResult, probableArgs: Seq[(ScalaResolveResult, FullInfoResult)]): Presentation = {
    val errorTooltip =
      if (probableArgs.size > 1) ambiguousTooltip(parameter)
      else notFoundTooltip(parameter)

    val ellipsis = {
      val result = text(Ellipsis, font)
      if (parameter.isImplicitParameterProblem) attributes(_ + errorAttributes, result) else result
    }

    val presentationString =
      if (!ImplicitHints.enabled) ellipsis else sequence(ellipsis, text(typeAnnotation(parameter), font))

    // navigation?
    expansion(expandedProblemPresentation(parameter, probableArgs),
      attributes(_ + foldedAttributes, presentationString).withErrorTooltipIfEmpty(errorTooltip))
  }

  private def expandedProblemPresentation(parameter: ScalaResolveResult, arguments: Seq[(ScalaResolveResult, FullInfoResult)]): Presentation = {
    arguments match {
      case Seq((arg, result)) => presentationOfProbable(arg, result)
      case _ => expandedAmbiguousPresentation(parameter, arguments)
    }
  }

  private def expandedAmbiguousPresentation(parameter: ScalaResolveResult, arguments: Seq[(ScalaResolveResult, FullInfoResult)]): Presentation = {

    val separator = attributes(_ + likeWrongReference, text(" | ", font))

    sequence(arguments
      .map { case (argument, result) => presentationOfProbable(argument, result) }
      .intersperse(separator): _*)
      .withErrorTooltipIfEmpty(ambiguousTooltip(parameter))
  }

  private def presentationOfProbable(argument: ScalaResolveResult, result: FullInfoResult): Presentation = {
    result match {
      case OkResult =>
        namedBasicPresentation(argument)

      case ImplicitParameterNotFoundResult =>
        val (leftParen, rightParen) = parentheses
        sequence(namedBasicPresentation(argument) +: leftParen +:
          argument.implicitParameters.map(parameter => presentationOf(parameter)).intersperse(text(", ", font)) :+ rightParen: _*)

      case DivergedImplicitResult =>
        attributes(_ + errorAttributes, namedBasicPresentation(argument))
          .withErrorTooltipIfEmpty("Implicit is diverged")

      case CantInferTypeParameterResult =>
        attributes(_ + errorAttributes, namedBasicPresentation(argument))
          .withErrorTooltipIfEmpty("Can't infer proper types for type parameters")
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

  private def notFoundTooltip(parameters: Seq[ScalaResolveResult]): Option[String] = {
    parameters match {
      case Seq() => None
      case Seq(p) => Some(notFoundTooltip(p))
      case ps => Some("No implicits found for parameters " + ps.map(paramWithType).mkString(", "))
    }
  }


  private def ambiguousTooltip(parameter: ScalaResolveResult): String =
    "Ambiguous implicits for parameter " + paramWithType(parameter)

  private val Ellipsis: String = "..."

  // Add custom colors for folding inside inlay hints (SCL-13996)?
  private def adjusted(attributes: TextAttributes): TextAttributes = {
    val result = attributes.clone()
    if (UIUtil.isUnderDarcula && result.getBackgroundColor != null) {
      result.setBackgroundColor(result.getBackgroundColor.brighter)
    }
    result
  }

  private def foldedAttributes: TextAttributes =
    Option(scheme.getAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES))
      .map(adjusted)
      .getOrElse(new TextAttributes())

  def inParentheses(presentation: Presentation): Presentation = {
    val (left, right) = parentheses
    sequence(left, presentation, right)
  }

  def parentheses: (Presentation, Presentation) = {
    val asMatch = (it: Presentation) => attributes(_ + scheme.getAttributes(CodeInsightColors.MATCHED_BRACE_ATTRIBUTES), it)
    synchronous(asMatch, text("(", font), text(")", font))
  }

  // remove
  private implicit class PresentationExt(val presentation: Presentation)  {
    //nested text may have more specific tooltip
    def withErrorTooltipIfEmpty(tooltip: String): Presentation = withTooltip(tooltip, presentation)

    //    parts.map { p =>
    //      if (p.errorTooltip.isEmpty) p.withErrorTooltip(tooltip) else p
    //    }

    def withErrorTooltipIfEmpty(tooltip: Option[String]): Presentation = tooltip.map(withErrorTooltipIfEmpty).getOrElse(presentation)

    //tooltip.map(parts.withErrorTooltipIfEmpty).getOrElse(parts)

  }
}
