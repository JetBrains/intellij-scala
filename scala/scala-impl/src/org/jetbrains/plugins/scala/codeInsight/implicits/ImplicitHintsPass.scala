package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.{CodeInsightColors, EditorColors, EditorColorsScheme}
import com.intellij.openapi.editor.ex.util.CaretVisualPositionKeeper
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.pom.Navigatable
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.actions.implicitArguments.ShowImplicitArgumentsAction
import org.jetbrains.plugins.scala.annotator.ScalaAnnotator
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHintsPass._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
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
    Seq(Hint(presentationOf(conversion.element) :+ Text("("), e, suffix = false, menu = Some(menu.ImplicitConversion)),
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
      .option(missingImplicitArgument(argument))
      .getOrElse(presentationOf(argument.element) ++ collapsedPresentationOf(argument.implicitParameters))

  private def presentationOf(e: PsiNamedElement): Seq[Text] = e match {
    case member: ScMember => presentationOf(member)
    case (_: ScReferencePattern) && Parent(Parent(member: ScMember with PsiNamedElement)) => presentationOf(member)
    case it => Seq(Text(it.name, navigatable = it.asOptionOf[Navigatable], tooltip = Some(it.name)))
  }

  private def presentationOf(member: ScMember with PsiNamedElement): Seq[Text] = {
    val hint = Option(member.containingClass).map(it => it.name + ".").mkString + member.name
    Seq(Text(member.name, navigatable = Some(member), tooltip = Some(hint)))
  }

  //TODO: separate navigation for type?
  private def missingImplicitArgument(result: ScalaResolveResult)(implicit scheme: EditorColorsScheme): Seq[Text] =
    Seq(Text(result.name, navigatable = result.element.asOptionOf[Navigatable]),
      Text(" = "),
      Text("???", Some(scheme.getAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES))),
      Text(": " + result.implicitSearchState.map(_.tp.presentableText).getOrElse("NotInferred")))
}

