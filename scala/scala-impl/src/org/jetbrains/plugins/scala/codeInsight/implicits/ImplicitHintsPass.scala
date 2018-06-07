package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.openapi.editor.ex.util.CaretVisualPositionKeeper
import com.intellij.openapi.editor.{Editor, Inlay, InlayModel}
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.{Disposer, Key, TextRange}
import com.intellij.util.DocumentUtil
import org.jetbrains.plugins.scala.actions.ShowImplicitArgumentsAction
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHintsPass._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScNewTemplateDefinition}

import scala.collection.JavaConverters._

private class ImplicitHintsPass(editor: Editor, rootElement: ScalaPsiElement)
  extends EditorBoundHighlightingPass(editor, rootElement.getContainingFile, true) {

  private var conversions: Seq[(ScExpression, String)] = Seq.empty
  private var parameters: Seq[(ScExpression, String)] = Seq.empty

  override def doCollectInformation(indicator: ProgressIndicator): Unit = {
    conversions = Seq.empty
    parameters = Seq.empty

    if (ImplicitHints.enabled && myDocument != null && rootElement.containingVirtualFile.isDefined) {
      collectConversionsAndParameters()
    }
  }

  private def collectConversionsAndParameters(): Unit = {
    rootElement.depthFirst().foreach {
      case e: ScExpression =>
        e.implicitConversion().foreach(it => conversions +:= (e, nameOf(it.element)))

        e match {
          case owner@(_: ImplicitParametersOwner | _: ScNewTemplateDefinition) =>
            ShowImplicitArgumentsAction.implicitParams(owner).foreach(results =>
              parameters +:= (owner, results.map(it => nameOf(it.element)).mkString("(", ", ", ")")))
          case _ =>
        }
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

    val bulkChange = existingInlays.length + conversions.length + parameters.length > BulkChangeThreshold

    DocumentUtil.executeInBulk(myEditor.getDocument, bulkChange, () => {
      existingInlays.foreach(Disposer.dispose)

      conversions.foreach { case (e, s) =>
        inlayModel.addInlay(new InlayInfo(s + "(", e.getTextRange.getStartOffset, false, true, false))
        inlayModel.addInlay(new InlayInfo(")", e.getTextRange.getEndOffset, false, true, true))
      }

      parameters.foreach { case (e, s) =>
        inlayModel.addInlay(new InlayInfo(s, e.getTextRange.getEndOffset, false, true, true))
      }
    })
  }
}

private object ImplicitHintsPass {
  private final val BulkChangeThreshold = 1000

  private val ScalaImplicitHintKey = Key.create[Boolean]("SCALA_IMPLICIT_HINT")

  implicit class Model(val model: InlayModel) extends AnyVal {
    def inlaysIn(range: TextRange): Seq[Inlay] =
      model.getInlineElementsInRange(range.getStartOffset + 1, range.getEndOffset - 1)
        .asScala
        .filter(ScalaImplicitHintKey.isIn)

    def addInlay(info: InlayInfo): Unit = {
      val inlay = model.addInlineElement(info.getOffset, info.getRelatesToPrecedingText, new Renderer(info.getText))
      Option(inlay).foreach(_.putUserData(ScalaImplicitHintKey, true))
    }
  }

  private class Renderer(text: String) extends HintRenderer(text) {
    override def getContextMenuGroupId: String = "ToogleImplicits"
  }
}

