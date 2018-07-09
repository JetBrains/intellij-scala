package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.{CodeInsightColors, EditorColors, EditorColorsScheme}
import com.intellij.openapi.editor.ex.util.CaretVisualPositionKeeper
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.actions.implicitArguments.ShowImplicitArgumentsAction
import org.jetbrains.plugins.scala.annotator.ScalaAnnotator
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHintsPass._
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

private class ImplicitHintsPass(editor: Editor, rootElement: ScalaPsiElement)
  extends EditorBoundHighlightingPass(editor, rootElement.getContainingFile, true) {

  private var hints: Seq[Hint] = Seq.empty

  override def doCollectInformation(indicator: ProgressIndicator): Unit = {
    hints = Seq.empty

    if (myDocument != null && rootElement.containingVirtualFile.isDefined) {
      collectConversionsAndArguments()
    }
  }

  private def collectConversionsAndArguments(): Unit = {
    val settings = ScalaProjectSettings.getInstance(rootElement.getProject)
    val showNotFoundImplicitForFile = ScalaAnnotator.isAdvancedHighlightingEnabled(rootElement) && settings.isShowNotFoundImplicitArguments

    def showNotFoundImplicits(element: PsiElement) =
      settings.isShowNotFoundImplicitArguments && ScalaAnnotator.isAdvancedHighlightingEnabled(element)

    if (!ImplicitHints.enabled && !showNotFoundImplicitForFile)
      return

    rootElement.depthFirst().foreach {
      case e: ScExpression =>
        if (ImplicitHints.enabled) {
          e.implicitConversion().foreach { conversion =>
            hints ++:= implicitConversionHint(e, conversion)(editor.getColorsScheme)
          }
        }

        e match {
          case call: ScMethodCall if isExplicitImplicit(call) =>
            if (ImplicitHints.enabled) {
              hints ++:= explicitImplicitArgumentsHint(call.args)
            }

          case owner: ImplicitParametersOwner =>
            val showNotFoundArgs = showNotFoundImplicits(e)
            val shouldSearch = ImplicitHints.enabled || showNotFoundArgs

            //todo: cover ambiguous implicit case (right now it is not always correct)
            def shouldShow(arguments: Seq[ScalaResolveResult]) =
              ImplicitHints.enabled || (showNotFoundArgs && arguments.exists(_.isNotFoundImplicitParameter))

            if (shouldSearch) {
              ShowImplicitArgumentsAction.implicitParams(owner) match {
                case Some(args) if shouldShow(args) =>
                  hints ++:= implicitArgumentsHint(owner, args)(editor.getColorsScheme)
                case _                              =>
              }
            }

          case _ =>
        }
      case _ =>
    }
  }

  private def isExplicitImplicit(call: ScMethodCall): Boolean = {
    val matchedParameters = call.matchedParameters

    matchedParameters.nonEmpty && matchedParameters.forall {
      case (_, parameter) => parameter.psiParam match {
        case Some(Parent(clause: ScParameterClause)) => clause.isImplicit
        case _ => false
      }
      case _ => false
    }
  }

  override def doApplyInformationToEditor(): Unit = {
    val caretKeeper = new CaretVisualPositionKeeper(myEditor)
    regenerateHints()
    caretKeeper.restoreOriginalLocation(false)

    if (rootElement == myFile) {
      ImplicitHints.setUpToDate(myEditor, myFile)
    }
  }

  private def regenerateHints(): Unit = {
    val inlayModel = myEditor.getInlayModel
    val existingInlays = inlayModel.inlaysIn(rootElement.getTextRange)

    val bulkChange = existingInlays.length + hints.length  > BulkChangeThreshold

    DocumentUtil.executeInBulk(myEditor.getDocument, bulkChange, () => {
      existingInlays.foreach(Disposer.dispose)
      hints.foreach(inlayModel.add(_))
    })
  }
}

private object ImplicitHintsPass {
  private final val BulkChangeThreshold = 1000

  private def implicitConversionHint(e: ScExpression, conversion: ScalaResolveResult)(implicit scheme: EditorColorsScheme): Seq[Hint] =
    Seq(Hint(basicPresentation(conversion) :+ Text("("), e, suffix = false, menu = Some(menu.ImplicitConversion)),
      Hint(Text(")") +: collapsedPresentationOf(conversion.implicitParameters), e, suffix = true))

  private def implicitArgumentsHint(e: ScExpression, arguments: Seq[ScalaResolveResult])(implicit scheme: EditorColorsScheme): Seq[Hint] =
    Seq(Hint(expandedPresentationOf(arguments), e, suffix = true, menu = Some(menu.ImplicitArguments)))

  private def explicitImplicitArgumentsHint(args: ScArgumentExprList): Seq[Hint] =
    Seq(Hint(Seq(Text(".explicitly")), args, suffix = false, menu = Some(menu.ExplicitArguments)))

  private def collapsedPresentationOf(arguments: Seq[ScalaResolveResult])(implicit scheme: EditorColorsScheme): Seq[Text] =
    if (arguments.nonEmpty) {
      Seq(Text("("),
        Text("...", attributes = Some(adjusted(scheme.getAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES))),
          expansion = Some(() => expandedPresentationOf(arguments).drop(1).dropRight(1))),
        Text(")"))
    } else {
      Seq.empty
    }

  // Add custom colors for folding inside inlay hints (SCL-13996)?
  private def adjusted(attributes: TextAttributes): TextAttributes = {
    val result = attributes.clone()
    if (UIUtil.isUnderDarcula) {
      result.setBackgroundColor(result.getBackgroundColor.brighter.brighter)
      result.setForegroundColor(result.getForegroundColor.brighter)
    }
    result
  }

  private def expandedPresentationOf(arguments: Seq[ScalaResolveResult])(implicit scheme: EditorColorsScheme): Seq[Text] =
    Text("(") +: arguments.map(it => presentationOf(it)).intersperse(Seq(Text(", "))).flatten :+ Text(")")

  private def presentationOf(argument: ScalaResolveResult)(implicit scheme: EditorColorsScheme): Seq[Text] =
    argument.isImplicitParameterProblem
      .option(detailedPresentationOfProbableArgumentsFor(parameter = argument))
      .getOrElse(basicPresentation(argument) ++ collapsedPresentationOf(argument.implicitParameters))

  private def basicPresentation(result: ScalaResolveResult): Seq[Text] = {
    val tooltip = ScalaDocumentationProvider.getQuickNavigateInfo(result)
    Seq(Text(result.name, navigatable = result.element.asOptionOf[Navigatable], tooltip = Some(tooltip)))
  }

  private def detailedPresentationOfProbableArgumentsFor(parameter: ScalaResolveResult)(implicit scheme: EditorColorsScheme): Seq[Text] = {
    Text(parameter.name, navigatable = parameter.element.asOptionOf[Navigatable]) +:
      Text(" = ") +:
      presentationOfProbableArgumentsFor(parameter) :+
      Text(": " + parameter.implicitSearchState.map(_.tp.presentableText).getOrElse("NotInferred"))
  }

  private def presentationOfProbableArgumentsFor(parameter: ScalaResolveResult)(implicit scheme: EditorColorsScheme): Seq[Text] = {
    probableArgumentsFor(parameter) match {
      case Seq() => Seq(Text("???",
        Some(scheme.getAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)),
        tooltip = Some("Implicit argument not found"),
        error = true))
      case arguments => collapsedPresentationOfProbable(arguments, parameter.isImplicitParameterProblem)
    }
  }

  private def collapsedPresentationOfProbable(arguments: Seq[(ScalaResolveResult, ImplicitResult)], problem: Boolean)(implicit scheme: EditorColorsScheme): Seq[Text] = {
    val attributes = adjusted(scheme.getAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES))

    Seq(Text("...",
      attributes = Some(if (problem) attributes + scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES) else attributes),
      expansion = Some(() => expandedPresentationOfProbable(arguments))))
  }

  private def expandedPresentationOfProbable(arguments: Seq[(ScalaResolveResult, ImplicitResult)])(implicit scheme: EditorColorsScheme): Seq[Text] =
    arguments.reverse
      .map { case (argument, result) => presentationOfProbable(argument, result) }
      .intersperse(Seq(Text(" | ",
        attributes = Some(scheme.getAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)),
        tooltip = Some("Ambiguous implicit arguments"),
        error = true)))
      .flatten

  private def presentationOfProbable(argument: ScalaResolveResult, result: ImplicitResult)(implicit scheme: EditorColorsScheme): Seq[Text] = {
    result match {
      case OkResult =>
        basicPresentation(argument)
      case ImplicitParameterNotFoundResult =>
        val presentationOfParameters = argument.implicitParameters
          .map(parameter => presentationOfProbableArgumentsFor(parameter))
          .intersperse(Seq(Text(", "))).flatten
        basicPresentation(argument) ++ (Text("(") +: presentationOfParameters :+ Text(")"))
      case _ =>
        withAttributes(scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES), basicPresentation(argument))
    }
  }

  private def probableArgumentsFor(parameter: ScalaResolveResult): Seq[(ScalaResolveResult, ImplicitResult)] = {
    parameter.implicitSearchState.map { state =>
      val collector = new ImplicitCollector(state.copy(fullInfo = true))
      collector.collect().flatMap { r =>
        r.implicitReason match {
          case reason @ (OkResult | DivergedImplicitResult | CantInferTypeParameterResult | ImplicitParameterNotFoundResult) => Seq((r, reason))
          case _ => Seq.empty
        }
      }
    } getOrElse {
      Seq.empty
    }
  }

  private def withAttributes(attributes: TextAttributes, parts: Seq[Text]): Seq[Text] =
    parts.map(_.withAttributes(attributes))
}

