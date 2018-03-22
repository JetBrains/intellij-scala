package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.hints
import com.intellij.openapi.editor.ex.util.CaretVisualPositionKeeper
import com.intellij.openapi.editor.{Editor, Inlay, InlayModel}
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.{Disposer, Key, TextRange}
import com.intellij.psi.SyntaxTraverser
import com.intellij.util.DocumentUtil
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.settings.annotations.Definition

import scala.collection.{JavaConverters, mutable}

class ScalaTypeHintsPass(editor: Editor, rootElement: ScalaPsiElement)
  extends EditorBoundHighlightingPass(editor, rootElement.getContainingFile, true) {

  import ScalaTypeHintsPass._

  private val elements = psiTraverser(rootElement)

  private val hintsByOffset = mutable.Map.empty[Int, Iterable[hints.InlayInfo]]

  override def doCollectInformation(progressIndicator: ProgressIndicator): Unit = {
    hintsByOffset.clear()
    implicit val settings: ScalaCodeInsightSettings = ScalaCodeInsightSettings.getInstance()
    if (myDocument == null || rootElement.containingVirtualFile.isEmpty || !settings.isShowTypeHints) return

    hintsByOffset ++= elements.filterNot {
      case _ if settings.isShowForObviousTypes => false
      case element => Definition(element).isTypeObvious
    }.flatMap {
      case f@TypelessFunction(anchor) if settings.isShowFunctionReturnType =>
        f.returnType.toInlayInfo(anchor)
      case v@TypelessValueOrVariable(anchor)
        //noinspection ScalaUnnecessaryParentheses
        if (if (v.isLocal) settings.isShowLocalVariableType else settings.isShowPropertyType) =>
        v.`type`().toInlayInfo(anchor)
      case _ => None
    }.groupBy(_.getOffset)
  }

  override def doApplyInformationToEditor(): Unit = {
    val keeper = new CaretVisualPositionKeeper(myEditor)

    implicit val inlayModel: InlayModel = myEditor.getInlayModel
    val inlays = inlaysInRange(rootElement.getTextRange)
    executeOnDocument(inlays)(Disposer.dispose)(addInlineElement)

    keeper.restoreOriginalLocation(false)
    if (rootElement == myFile) {
      ScalaTypeHintsPassFactory.putCurrentModificationStamp(myEditor, Some(myFile))
    }
  }

  private def executeOnDocument(inlays: Iterable[Inlay])
                               (onInlay: Inlay => Unit)
                               (onHint: hints.InlayInfo => Unit): Unit = {
    val hints = hintsByOffset.values.flatten
    DocumentUtil.executeInBulk(myEditor.getDocument, inlays.size + hints.size > BulkChangeLimit, () => {
      inlays.foreach(onInlay)
      hints.foreach(onHint)
    })
  }
}

object ScalaTypeHintsPass {

  import JavaConverters._

  private val BulkChangeLimit = 1000

  private[this] val ScalaTypeInlayKey = Key.create[Boolean]("SCALA_TYPE_INLAY_KEY")

  private def psiTraverser(rootElement: ScalaPsiElement) =
    SyntaxTraverser.psiTraverser(rootElement).asScala

  private def inlaysInRange(textRange: TextRange)
                           (implicit inlayModel: InlayModel) =
    inlayModel.getInlineElementsInRange(textRange.getStartOffset + 1, textRange.getEndOffset - 1)
      .asScala
      .filter(ScalaTypeInlayKey.isIn)

  private def addInlineElement(inlayInfo: hints.InlayInfo)
                              (implicit inlayModel: InlayModel): Unit = {
    val renderer = new HintRenderer(inlayInfo.getText) {
      override def getContextMenuGroupId: String = "TypeHintsMenu"
    }

    inlayModel.addInlineElement(inlayInfo.getOffset, inlayInfo.getRelatesToPrecedingText, renderer) match {
      case null =>
      case inlay => inlay.putUserData(ScalaTypeInlayKey, true)
    }
  }

  private object TypelessFunction {

    def unapply(definition: ScFunctionDefinition): Option[ScalaPsiElement] =
      if (definition.hasExplicitType || definition.isConstructor) None
      else Some(definition.parameterList)
  }

  private object TypelessValueOrVariable {

    def unapply(definition: ScValueOrVariable): Option[ScalaPsiElement] =
      if (definition.hasExplicitType) None
      else definition match {
        case value: ScPatternDefinition => Some(value.pList)
        case variable: ScVariableDefinition => Some(variable.pList)
        case _ => None
      }
  }

  private implicit class TypeResultExt(private val result: TypeResult) {

    def toInlayInfo(anchor: ScalaPsiElement)
                   (implicit settings: ScalaCodeInsightSettings): Option[hints.InlayInfo] =
      result.map(InlayInfo(_, anchor)).toOption
  }
}
