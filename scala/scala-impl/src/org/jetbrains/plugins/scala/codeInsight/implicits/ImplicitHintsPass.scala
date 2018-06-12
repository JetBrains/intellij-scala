package org.jetbrains.plugins.scala.codeInsight.implicits

import java.awt.{Graphics, Rectangle}

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.openapi.editor.colors.{CodeInsightColors, TextAttributesKey}
import com.intellij.openapi.editor.ex.util.CaretVisualPositionKeeper
import com.intellij.openapi.editor.markup.TextAttributes
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
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.JavaConverters._

private class ImplicitHintsPass(editor: Editor, rootElement: ScalaPsiElement)
  extends EditorBoundHighlightingPass(editor, rootElement.getContainingFile, true) {

  private var hints: Seq[(String, ScExpression, String)] = Seq.empty

  override def doCollectInformation(indicator: ProgressIndicator): Unit = {
    hints = Seq.empty

    if (ImplicitHints.enabled && myDocument != null && rootElement.containingVirtualFile.isDefined) {
      collectConversionsAndParameters()
    }
  }

  private def collectConversionsAndParameters(): Unit = {
    rootElement.depthFirst().foreach {
      case e: ScExpression =>
        e.implicitConversion().foreach { conversion =>
          val name = nameOf(conversion.element)
          hints +:= (name + "(", e, if (conversion.implicitParameters.nonEmpty) ")(...)" else ")")
        }

        e match {
          case owner@(_: ImplicitParametersOwner | _: ScNewTemplateDefinition) =>
            ShowImplicitArgumentsAction.implicitParams(owner).foreach { arguments =>
              hints +:= ("", owner, arguments.map(presentationOf).mkString("(", ", ", ")"))
            }
          case _ =>
        }
      case _ =>
    }
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

      hints.foreach { case (prefix, e, suffix) =>
        if (prefix.nonEmpty) {
          inlayModel.addInlay(new InlayInfo(prefix, e.getTextRange.getStartOffset, false, true, false))
        }
        if (suffix.nonEmpty) {
          inlayModel.addInlay(new InlayInfo(suffix, e.getTextRange.getEndOffset, false, true, true))
        }
      }
    })
  }
}

private object ImplicitHintsPass {
  private final val BulkChangeThreshold = 1000
  private final val MissingImplicitArgument = "?: "

  private val ScalaImplicitHintKey = Key.create[Boolean]("SCALA_IMPLICIT_HINT")

  implicit class Model(val model: InlayModel) extends AnyVal {
    def inlaysIn(range: TextRange): Seq[Inlay] =
      model.getInlineElementsInRange(range.getStartOffset + 1, range.getEndOffset - 1)
        .asScala
        .filter(ScalaImplicitHintKey.isIn)

    def addInlay(info: InlayInfo): Unit = {
      val renderer = new TextRenderer(info.getText, error = info.getText.contains(MissingImplicitArgument))
      val inlay = model.addInlineElement(info.getOffset, info.getRelatesToPrecedingText, renderer)
      Option(inlay).foreach(_.putUserData(ScalaImplicitHintKey, true))
    }
  }

  private class TextRenderer(text: String, error: Boolean) extends HintRenderer(text) {
    override def getContextMenuGroupId: String = "ToggleImplicits"

    // TODO Fine-grained coloring
    // TODO Why the effect type / color cannot be specified via super.getTextAttributes?
    override def paint(editor: Editor, g: Graphics, r: Rectangle, textAttributes: TextAttributes): Unit = {
      if (error) {
        val errorAttributes = editor.getColorsScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)
        textAttributes.setEffectType(errorAttributes.getEffectType)
        textAttributes.setEffectColor(errorAttributes.getEffectColor)
      }

      super.paint(editor, g, r, textAttributes)
    }
  }
}

