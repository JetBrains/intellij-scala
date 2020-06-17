package org.jetbrains.plugins.scala.annotator.intention

import java.awt.Point

import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInspection.HintAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportElementFix._
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions.executeUndoTransparentAction
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode

abstract class ScalaImportElementFix(val place: PsiElement) extends HintAction {

  private val modificationCount = currentModCount()

  val elements: Seq[ElementToImport]

  def createAddImportAction(editor: Editor): ScalaAddImportAction[_, _]

  def shouldShowHint(): Boolean =
    !isShowErrorsFromCompilerEnabled(place.getContainingFile)

  def isAddUnambiguous: Boolean

  override def isAvailable(project: Project,
                           editor: Editor,
                           file: PsiFile): Boolean = isAvailable && file.hasScalaPsi

  def isAvailable: Boolean = place.isValid && elements.nonEmpty && modificationCount == currentModCount()

  override def showHint(editor: Editor): Boolean = {
    if (!shouldShowHint()) return false

    elements.length match {
      case 0 => false
      case 1 if isAddUnambiguous && !editor.caretNear(place) =>
        executeUndoTransparentAction {
          createAddImportAction(editor).execute()
        }
        false
      case _ =>
        invokeLater {
          showHintWithAction(editor)
        }
        true
    }
  }

  override def invoke(project: Project,
                      editor: Editor,
                      file: PsiFile): Unit = executeUndoTransparentAction {
    if (isAvailable(project, editor, file)) {
      createAddImportAction(editor).execute()
    }
  }

  override def startInWriteAction: Boolean = true

  private def showHintWithAction(editor: Editor): Unit = {
    val hintManager = HintManagerImpl.getInstanceImpl
    val elementStart = place.startOffset
    val elementEnd = place.endOffset

    if (place.isValid &&
      elements.nonEmpty &&
      !hintManager.hasShownHintsThatWillHideByOtherHint(true) &&
      editor.visibleRange.containsOffset(elementStart) &&
      elementEnd < editor.getDocument.getTextLength) {

      val hintText = ScalaBundle.message(
        "import.hint.text",
        elements.head.qualifiedName,
        if (elements.length == 1) "" else ScalaBundle.message("import.multiple.choices")
      )

      hintManager.showQuestionHint(
        editor,
        hintText,
        elementStart,
        elementEnd,
        createAddImportAction(editor)
      )
    }
  }

  private def currentModCount(): Long =
    if (place.isValid) BlockModificationTracker(place).getModificationCount
    else ModificationTracker.EVER_CHANGED.getModificationCount
}

private object ScalaImportElementFix {

  private def isShowErrorsFromCompilerEnabled(file: PsiFile) =
    ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(file)

  implicit class EditorEx(val editor: Editor) extends AnyVal {
    def caretNear(place: PsiElement): Boolean =
      place.getTextRange.grown(1).contains(editor.getCaretModel.getOffset)

    def visibleRange: TextRange = {
      val visibleRectangle = editor.getScrollingModel.getVisibleArea

      val startPosition = editor.xyToLogicalPosition(visibleRectangle.getLocation)
      val startOffset = editor.logicalPositionToOffset(startPosition)

      val endPosition = editor.xyToLogicalPosition(new Point(visibleRectangle.x + visibleRectangle.width, visibleRectangle.y + visibleRectangle.height))
      val endOffset = editor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0))

      TextRange.create(
        startOffset,
        startOffset max endOffset
      )
    }

  }
}
