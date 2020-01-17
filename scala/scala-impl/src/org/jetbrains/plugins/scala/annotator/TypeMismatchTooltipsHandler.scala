package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.{DaemonCodeAnalyzerImpl, HighlightInfo}
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.event.{EditorMouseEvent, EditorMouseMotionListener}
import com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.project.{Project, ProjectManagerListener}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

// Annotation.setTooltip is shown both on mouse hover (EditorMouseHoverPopupManager), and on Ctrl / Cmd + F1 (ShowErrorDescriptionAction)
// But when type mismatch hints are enabled, we don't want tooltips on mouse hover (on the expression), but still want tooltips on the explicit action.
// https://youtrack.jetbrains.com/issue/SCL-15782
class TypeMismatchTooltipsHandler extends ProjectManagerListener {
  override def projectOpened(project: Project): Unit = {
    val listener = new TypeMismatchTooltipsHandler.Listener(project)
    EditorFactory.getInstance().getEventMulticaster.addEditorMouseMotionListener(listener, project)
  }
}

private object TypeMismatchTooltipsHandler {
  private val TypeMismatchTooltipPrefix = "<html><body>Type mismatch"

  private class Listener(project: Project) extends EditorMouseMotionListener {
    // See com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl
    private var popupsWereDisabled = false

    override def mouseMoved(e: EditorMouseEvent): Unit = {
      if (!e.isConsumed && project.isInitialized && !project.isDisposed) {
        val editor = e.getEditor

        if (ScalaProjectSettings.getInstance(project).isTypeMismatchHints) {
          val point = e.getMouseEvent.getPoint

          val position = editor.xyToLogicalPosition(point)
          val offset = editor.logicalPositionToOffset(position)

          val highlightInfo = Option(DaemonCodeAnalyzer.getInstance(project).asInstanceOf[DaemonCodeAnalyzerImpl]
            .findHighlightByOffset(editor.getDocument, offset, false))

          disableTooltipOnMouseHoverForTypeMismatchErrors(editor, highlightInfo)
        } else {
          if (popupsWereDisabled) {
            EditorMouseHoverPopupControl.enablePopups(editor);
            popupsWereDisabled = false
          }
        }
      }
    }

    private def disableTooltipOnMouseHoverForTypeMismatchErrors(editor: Editor, maybeInfo: Option[HighlightInfo]): Unit = {
      val isTypeMismatchError = maybeInfo.exists(info =>
        info.getSeverity == HighlightSeverity.ERROR &&
          Option(info.getToolTip).exists(_.startsWith(TypeMismatchTooltipPrefix)))

      if (isTypeMismatchError) {
        if (!popupsWereDisabled && !EditorMouseHoverPopupControl.arePopupsDisabled(editor)) {
          EditorMouseHoverPopupControl.disablePopups(editor);
          popupsWereDisabled = true
        }
      } else {
        if (popupsWereDisabled) {
          EditorMouseHoverPopupControl.enablePopups(editor);
          popupsWereDisabled = false
        }
      }
    }

  }
}
