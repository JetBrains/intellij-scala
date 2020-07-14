package org.jetbrains.plugins.scala.annotator.intention

import java.awt.Point

import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.HintAction
import com.intellij.openapi.editor.{Editor, LogicalPosition}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{ModificationTracker, TextRange}
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.intention.Presentation.htmlWithBody
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportElementFix._
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiFileExt, executeUndoTransparentAction, invokeLater}
import org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScGenericCall

abstract class ScalaImportElementFix(val place: PsiElement) extends HintAction with PriorityAction {

  private val modificationCount = currentModCount()

  val elements: Seq[ElementToImport]

  final def getText: String = htmlWithBody(getTextInner)

  @Nls
  protected def getTextInner: String

  def createAddImportAction(editor: Editor): ScalaAddImportAction[_, _]

  def shouldShowHint(): Boolean =
    !isShowErrorsFromCompilerEnabled(place.getContainingFile)

  def isAddUnambiguous: Boolean

  override def getPriority: PriorityAction.Priority =
    PriorityAction.Priority.TOP

  override def isAvailable(project: Project,
                           editor: Editor,
                           file: PsiFile): Boolean = isAvailable && file.hasScalaPsi

  def isAvailable: Boolean = place.isValid && elements.nonEmpty && modificationCount == currentModCount()

  override def showHint(editor: Editor): Boolean = {
    if (elements.isEmpty || !shouldShowHint())
      false
    else if (fixSilently(editor))
      true
    else {
      invokeLater {
        showHintWithAction(editor)
      }
      true
    }

  }

  override def fixSilently(editor: Editor): Boolean = {
    if (elements.size == 1 && isAddUnambiguous && !editor.caretNear(place)) {
      executeUndoTransparentAction {
        createAddImportAction(editor).execute()
      }
      true
    }
    else false
  }

  override def invoke(project: Project,
                      editor: Editor,
                      file: PsiFile): Unit = executeUndoTransparentAction {
    if (isAvailable(project, editor, file)) {
      createAddImportAction(editor).execute()
    }
  }

  override def startInWriteAction: Boolean = true

  protected def getHintRange: (Int, Int) = hintRange(place)

  private def hintRange(element: PsiElement): (Int, Int) = {
    element match {
      case ref: ScReference     => (ref.nameId.startOffset, ref.nameId.endOffset)
      case gcall: ScGenericCall => (hintRange(gcall.referencedExpr)._1, gcall.endOffset)
      case _                    => (element.startOffset, element.endOffset)
    }
  }

  private def showHintWithAction(editor: Editor): Unit = {
    val hintManager = HintManagerImpl.getInstanceImpl
    val (elementStart, elementEnd) = getHintRange

    if (place.isValid &&
      elements.nonEmpty &&
      !hintManager.hasShownHintsThatWillHideByOtherHint(true) &&
      editor.visibleRange.containsOffset(elementStart) &&
      elementEnd < editor.getDocument.getTextLength) {

      val hintText = ScalaBundle.message(
        "import.hint.text",
        elements.head.presentationBody,
        if (elements.length == 1) "" else ScalaBundle.message("import.multiple.choices")
      )

      hintManager.showQuestionHint(
        editor,
        htmlWithBody(hintText),
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
