package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.ex.util.CaretVisualPositionKeeper
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.util.DocumentUtil
import org.jetbrains.plugins.scala.actions.ShowImplicitArgumentsAction
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
            hints ++:= implicitConversionHint(e, conversion)
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
                  val errorAttributes = editor.getColorsScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)
                  hints ++:= implicitArgumentsHint(owner, args, errorAttributes)
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

  def implicitConversionHint(e: ScExpression, conversion: ScalaResolveResult): Seq[Hint] =
    Seq(Hint(presentationOf(conversion.element) :+ Text("("), e, suffix = false, rightGap = false, menu = Some(menu.ImplicitConversion)),
      Hint(if (conversion.implicitParameters.nonEmpty) Seq(Text(")"), Text("(...)")) else Seq(Text(")")), e, suffix = true, leftGap = false))

  private def presentationOf(e: PsiNamedElement): Seq[Text] = e match {
    case member: ScMember => presentationOf(member)
    case (_: ScReferencePattern) && Parent(Parent(member: ScMember with PsiNamedElement)) => presentationOf(member)
    case it => Seq(Text(it.name))
  }

  private def presentationOf(member: ScMember with PsiNamedElement): Seq[Text] =
    Option(member.containingClass).map(it => Seq(Text(it.name), Text("."))).getOrElse(Seq.empty) :+ Text(member.name)

  def implicitArgumentsHint(e: ScExpression, arguments: Seq[ScalaResolveResult], errorAttributes: TextAttributes): Seq[Hint] = {
    val text = Text("(") +: arguments.map(it => presentationOf(it, errorAttributes)).intersperse(Seq(Text(", "))).flatten :+ Text(")")
    Seq(Hint(text, e, suffix = true, leftGap = false, menu = Some(menu.ImplicitArguments)))
  }

  // TODO Show missing implicit parameter name?
  private def presentationOf(argument: ScalaResolveResult, errorAttributes: TextAttributes): Seq[Text] = {
    ShowImplicitArgumentsAction.missingImplicitArgumentIn(argument)
      .map(it => Seq(Text("?: " + it.map(_.presentableText).getOrElse("NotInferred"), Some(errorAttributes))))
      .getOrElse {
        val name = presentationOf(argument.element)
        if (argument.implicitParameters.nonEmpty) name :+ Text("(...)") else name
      }
  }

  def explicitImplicitArgumentsHint(args: ScArgumentExprList): Seq[Hint] =
    Seq(Hint(Seq(Text(".explicitly")), args, suffix = false, leftGap = false, rightGap = false, menu = Some(menu.ExplicitArguments)))
}

