package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.{DaemonCodeAnalyzerImpl, HighlightInfo}
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.event.{EditorMouseEvent, EditorMouseMotionListener}
import com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.annotator.TypeMismatchTooltipsHandler.TypeMismatchTooltipPrefix
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

// Annotation.setTooltip is shown both on mouse hover (EditorMouseHoverPopupManager), and on Ctrl / Cmd + F1 (ShowErrorDescriptionAction)
// But when type mismatch hints are enabled, we don't want tooltips on mouse hover (on the expression), but still want tooltips on the explicit action.
// https://youtrack.jetbrains.com/issue/SCL-15782
class TypeMismatchTooltipsHandler(project: Project, editorFactory: EditorFactory) extends ProjectComponent {

  // See com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl
  private var popupsWereDisabled = false;

  private val mouseMovedListener: EditorMouseMotionListener = new EditorMouseMotionListener {
    override def mouseMoved(e: EditorMouseEvent): Unit = {
      if (!e.isConsumed && project.isInitialized && !project.isDisposed && ScalaProjectSettings.getInstance(project).isTypeMismatchHints) {
        val editor = e.getEditor
        val point = e.getMouseEvent.getPoint

        val position = editor.xyToLogicalPosition(point)
        val offset = editor.logicalPositionToOffset(position)

        val highlightInfo = DaemonCodeAnalyzer.getInstance(project).asInstanceOf[DaemonCodeAnalyzerImpl]
          .findHighlightByOffset(editor.getDocument, offset, false)

        disableTooltipOnMouseHoverForTypeMismatchErrors(editor, highlightInfo)
      }
    }
  }

  private def disableTooltipOnMouseHoverForTypeMismatchErrors(editor: Editor, info: HighlightInfo): Unit = {
    val isTypeMismatchError = info.getSeverity == HighlightSeverity.ERROR && info.getToolTip != null && info.getToolTip.startsWith(TypeMismatchTooltipPrefix)

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

  override def projectOpened(): Unit = {
    editorFactory.getEventMulticaster.addEditorMouseMotionListener(mouseMovedListener, project)
  }

  override def projectClosed(): Unit = {
    editorFactory.getEventMulticaster.removeEditorMouseMotionListener(mouseMovedListener)
  }
}

private object TypeMismatchTooltipsHandler {
  private val TypeMismatchTooltipPrefix = "<html><body>Type mismatch"
}
