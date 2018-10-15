package org.jetbrains.plugins.scala.codeInsight.implicits

import java.awt._

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.CaretVisualPositionKeeper
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.util.DocumentUtil
import org.jetbrains.plugins.scala.annotator.ScalaAnnotator
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHintsPass._
import org.jetbrains.plugins.scala.codeInsight.implicits.presentation._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
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

    val factory = new HintFactory(editor.asInstanceOf[EditorImpl])

    def implicitArgumentsOrErrorHints(owner: ImplicitArgumentsOwner): Seq[Hint] = {
      val showNotFoundArgs = showNotFoundImplicits(owner)
      val shouldSearch = ImplicitHints.enabled || showNotFoundArgs

      //todo: cover ambiguous implicit case (right now it is not always correct)
      def shouldShow(arguments: Seq[ScalaResolveResult]) =
        ImplicitHints.enabled ||
          (showNotFoundArgs && arguments.exists(p => p.isImplicitParameterProblem && !factory.isAmbiguous(p)))

      if (shouldSearch) {
        owner.findImplicitArguments.toSeq.flatMap {
          case args if shouldShow(args) =>
            factory.implicitArgumentsHint(owner, args)
          case _ => Seq.empty
        }
      }
      else Seq.empty
    }

    def explicitArgumentHint(e: ImplicitArgumentsOwner): Seq[Hint] = {
      if (!ImplicitHints.enabled) return Seq.empty

      e.explicitImplicitArgList.toSeq
        .flatMap(factory.explicitImplicitArgumentsHint)
    }

    def implicitConversionHints(e: ScExpression): Seq[Hint] = {
      if (!ImplicitHints.enabled) return Seq.empty

      e.implicitConversion().toSeq.flatMap { conversion =>
        factory.implicitConversionHint(e, conversion)
      }
    }

    rootElement.depthFirst().foreach {
      case e: ScExpression =>
        hints ++:= implicitConversionHints(e)
        hints ++:= explicitArgumentHint(e)
        hints ++:= implicitArgumentsOrErrorHints(e)
      case c: ScConstructor =>
        hints ++:= explicitArgumentHint(c)
        hints ++:= implicitArgumentsOrErrorHints(c)
      case _ =>
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
      hints.foreach { hint =>
        inlayModel.add(hint).foreach { inlay =>
          hint.presentation.addPresentationListener(new PresentationListener { // TODO
            override def contentChanged(area: Rectangle): Unit = inlay.repaint()

            override def sizeChanged(previous: Dimension, current: Dimension): Unit = inlay.updateSize()
          })
        }
      }
    })
  }
}

private object ImplicitHintsPass {
  private final val BulkChangeThreshold = 1000
}

