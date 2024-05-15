package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.{DaemonCodeAnalyzerImpl, HighlightInfo}
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.event.{EditorMouseEvent, EditorMouseMotionListener}
import com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.annotator.quickfix.EnableTypeMismatchHints
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.startup.ProjectActivity

// Annotation.setTooltip is shown both on mouse hover (EditorMouseHoverPopupManager), and on Ctrl / Cmd + F1 (ShowErrorDescriptionAction)
// But when type mismatch hints are enabled, we don't want tooltips on mouse hover (on the expression), but still want tooltips on the explicit action.
// https://youtrack.jetbrains.com/issue/SCL-15782
private final class TypeMismatchTooltipsHandler extends ProjectActivity {
  override def execute(project: Project): Unit = {
    val listener = new TypeMismatchTooltipsHandler.Listener(project)
    EditorFactory.getInstance().getEventMulticaster.addEditorMouseMotionListener(listener, project.unloadAwareDisposable)
  }
}

private object TypeMismatchTooltipsHandler {
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
          // previously, determining wheather a HighlightInfo is a TypeMismatchError
          // was by inspecting the tooltip message, but that didn't work anymore because of localizing.
          // Looking for this quickfix is the only way I found to determine this :/
          info.findRegisteredQuickFix {
            case (desc, _) if desc.getAction == EnableTypeMismatchHints => desc
            case _ => null
          } != null
      )

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
