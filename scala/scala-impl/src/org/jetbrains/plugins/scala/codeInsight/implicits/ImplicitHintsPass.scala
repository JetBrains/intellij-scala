package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.CaretVisualPositionKeeper
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiNamedElement
import com.intellij.util.DocumentUtil
import org.jetbrains.plugins.scala.actions.ShowImplicitArgumentsAction
import org.jetbrains.plugins.scala.annotator.ScalaAnnotator
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHintsPass._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression, ScMethodCall, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

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
    if (!ImplicitHints.enabled && !ScalaAnnotator.isAdvancedHighlightingEnabled(rootElement))
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

          case owner@(_: ImplicitParametersOwner | _: ScNewTemplateDefinition) if e.implicitConversion().isEmpty =>
            ShowImplicitArgumentsAction.implicitParams(owner).foreach { arguments =>
              val typeAware = ScalaAnnotator.isAdvancedHighlightingEnabled(e)
              def argumentsMissing = arguments.exists(ShowImplicitArgumentsAction.missingImplicitArgumentIn(_).isDefined)
              if (ImplicitHints.enabled || (typeAware && argumentsMissing)) {
                hints ++:= implicitArgumentsHint(owner, arguments)
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
  private final val MissingImplicitArgument = "?: "

  def implicitConversionHint(e: ScExpression, conversion: ScalaResolveResult): Seq[Hint] =
    Seq(Hint(nameOf(conversion.element) + "(", e, suffix = false, rightGap = false),
      Hint(if (conversion.implicitParameters.nonEmpty) ")(...)" else ")", e, suffix = true, leftGap = false))

  private def nameOf(e: PsiNamedElement): String = e match {
    case member: ScMember => nameOf(member)
    case (_: ScReferencePattern) && Parent(Parent(member: ScMember with PsiNamedElement)) => nameOf(member)
    case it => it.name
  }

  private def nameOf(member: ScMember with PsiNamedElement) =
    Option(member.containingClass).map(_.name + ".").mkString + member.name

  def implicitArgumentsHint(e: ScExpression, arguments: Seq[ScalaResolveResult]): Seq[Hint] = {
    val text = arguments.map(presentationOf).mkString("(", ", ", ")")
    Seq(Hint(text, e, suffix = true, leftGap = false, underlined = text.contains(MissingImplicitArgument)))
  }

  // TODO Show missing implicit parameter name?
  private def presentationOf(argument: ScalaResolveResult): String = {
    ShowImplicitArgumentsAction.missingImplicitArgumentIn(argument)
      .map(MissingImplicitArgument + _.map(_.presentableText).getOrElse("NotInferred"))
      .getOrElse {
        val name = nameOf(argument.element)
        if (argument.implicitParameters.nonEmpty) name + "(...)" else name
      }
  }

  def explicitImplicitArgumentsHint(args: ScArgumentExprList): Seq[Hint] =
    Seq(Hint(".explicitly", args, suffix = false, leftGap = false, rightGap = false))
}

