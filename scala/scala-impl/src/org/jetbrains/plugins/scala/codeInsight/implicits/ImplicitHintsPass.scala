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
              ImplicitHints.enabled ||
                (showNotFoundArgs && arguments.exists(p => p.isImplicitParameterProblem && !isAmbiguous(p)))

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
    Seq(Hint(namedBasicPresentation(conversion) :+ Text("("), e, suffix = false, menu = Some(menu.ImplicitConversion)),
      Hint(Text(")") +: collapsedPresentationOf(conversion.implicitParameters), e, suffix = true))

  private def implicitArgumentsHint(e: ScExpression, arguments: Seq[ScalaResolveResult])(implicit scheme: EditorColorsScheme): Seq[Hint] =
    Seq(Hint(expandedPresentationOf(arguments), e, suffix = true, menu = Some(menu.ImplicitArguments)))

  private def explicitImplicitArgumentsHint(args: ScArgumentExprList): Seq[Hint] =
    Seq(Hint(Seq(Text(".explicitly")), args, suffix = false, menu = Some(menu.ExplicitArguments)))

  private def collapsedPresentationOf(arguments: Seq[ScalaResolveResult])(implicit scheme: EditorColorsScheme): Seq[Text] =
    if (arguments.nonEmpty) {
      Seq(
        Text("("),
        Text(foldedString, attributes = Some(foldedAttributes(error = false)),
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
      .option(problemPresentation(parameter = argument))
      .getOrElse(namedBasicPresentation(argument) ++ collapsedPresentationOf(argument.implicitParameters))

  private def namedBasicPresentation(result: ScalaResolveResult): Seq[Text] = {
    val tooltip = ScalaDocumentationProvider.getQuickNavigateInfo(result)
    Text(result.name, navigatable = result.element.asOptionOf[Navigatable], tooltip = Some(tooltip)).seq
  }

  private def problemPresentation(parameter: ScalaResolveResult)
                                 (implicit scheme: EditorColorsScheme): Seq[Text] = {
    probableArgumentsFor(parameter) match {
      case Seq()                                                => noApplicableExpandedPresentation(parameter)
      case Seq((arg, result)) if arg.implicitParameters.isEmpty => presentationOfProbable(arg, result)
      case args                                                 => collapsedProblemPresentation(parameter, args)
    }
  }

  private def noApplicableExpandedPresentation(parameter: ScalaResolveResult)
                                              (implicit scheme: EditorColorsScheme) = {

    val presentationString = if (!ImplicitHints.enabled) "?" else "?" + typeSuffix(parameter)

      Text(
        presentationString,
        Some(errorAttributes),
        Some(notFoundTooltip(parameter)),
        parameter.element.asOptionOf[Navigatable],
        error = true
      ).seq
  }

  private def collapsedProblemPresentation(parameter: ScalaResolveResult, probableArgs: Seq[(ScalaResolveResult, ImplicitResult)])
                                          (implicit scheme: EditorColorsScheme): Seq[Text] = {
    val tooltip =
      if (probableArgs.size > 1) ambiguousTooltip(parameter)
      else notFoundTooltip(parameter)

    val presentationString = if (!ImplicitHints.enabled) "..." else "..." + typeSuffix(parameter)

    Text(
      presentationString,
      Some(foldedAttributes(error = parameter.isImplicitParameterProblem)),
      Some(tooltip),
      parameter.element.asOptionOf[Navigatable],
      error = true,
      expansion = Some(() => expandedProblemPresentation(parameter, probableArgs))
    ).seq
  }

  private def expandedProblemPresentation(parameter: ScalaResolveResult, arguments: Seq[(ScalaResolveResult, ImplicitResult)])
                                         (implicit scheme: EditorColorsScheme): Seq[Text] = {

    arguments match {
      case Seq((arg, result)) => presentationOfProbable(arg, result)
      case _                  => expandedAmbiguousPresentation(parameter, arguments)
    }
  }

  private def expandedAmbiguousPresentation(parameter: ScalaResolveResult, arguments: Seq[(ScalaResolveResult, ImplicitResult)])
                                           (implicit scheme: EditorColorsScheme) = {

    val likeWrongReference = Some(scheme.getAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES))
    val separator = Seq(Text(" | ", likeWrongReference)).withErrorTooltip(ambiguousTooltip(parameter))

    arguments
      .map { case (argument, result) => presentationOfProbable(argument, result) }
      .intersperse(separator)
      .flatten
  }

  private def presentationOfProbable(argument: ScalaResolveResult, result: ImplicitResult)
                                    (implicit scheme: EditorColorsScheme): Seq[Text] = {
    result match {
      case OkResult =>
        namedBasicPresentation(argument)
      case ImplicitParameterNotFoundResult =>
        val presentationOfParameters = argument.implicitParameters
          .map(parameter => presentationOf(parameter))
          .intersperse(Text(", ").seq).flatten
        namedBasicPresentation(argument) ++ (Text("(") +: presentationOfParameters :+ Text(")"))
      case DivergedImplicitResult          =>
        namedBasicPresentation(argument)
          .withErrorTooltip("Implicit is diverged")
          .withAttributes(errorAttributes)
      case CantInferTypeParameterResult    =>
        namedBasicPresentation(argument)
          .withErrorTooltip("Can't infer proper types for type parameters")
          .withAttributes(errorAttributes)
      case _ =>
        namedBasicPresentation(argument)
          .withAttributes(errorAttributes)

    }
  }

  private def isAmbiguous(parameter: ScalaResolveResult): Boolean =
    parameter.isImplicitParameterProblem && probableArgumentsFor(parameter).size > 1

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

  private def errorAttributes(implicit scheme: EditorColorsScheme) = scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)

  private def typeSuffix(parameter: ScalaResolveResult): String = {
    val paramType = parameter.implicitSearchState.map(_.tp.presentableText).getOrElse("NotInferred")
    s": $paramType"
  }

  private def paramWithType(parameter: ScalaResolveResult): String = parameter.name + typeSuffix(parameter)

  private def notFoundTooltip(parameter: ScalaResolveResult): String =
    "No implicits found for parameter " + paramWithType(parameter)

  private def ambiguousTooltip(parameter: ScalaResolveResult): String =
    "Ambiguous implicits for parameter " + paramWithType(parameter)

  private val foldedString: String = "..."

  private def foldedAttributes(error: Boolean)
                              (implicit scheme: EditorColorsScheme): TextAttributes = {
    val plainFolded = adjusted(scheme.getAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES))

    if (error) plainFolded + errorAttributes
    else plainFolded
  }

  private implicit class SeqTextExt(val parts: Seq[Text]) extends AnyVal {
    def withErrorTooltip(tooltip: String)   : Seq[Text] = parts.map(_.copy(tooltip = Some(tooltip), error = true))
    def withAttributes(attr: TextAttributes): Seq[Text] = parts.map(_.copy(attributes = Some(attr)))
  }

  private implicit class TextExt(val text: Text) extends AnyVal {
    def seq: Seq[Text] = Seq(text)
  }
}

