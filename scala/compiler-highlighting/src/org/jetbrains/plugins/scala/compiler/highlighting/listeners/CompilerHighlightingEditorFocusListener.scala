package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents
import org.jetbrains.plugins.scala.compiler.highlighting.services.DocumentCompilerAvailabilityService
import org.jetbrains.plugins.scala.compiler.highlighting.triggers.OnEditorFocusTrigger
import org.jetbrains.plugins.scala.compiler.tracing.Tracing

import java.awt.event.{FocusAdapter, FocusEvent}

private[highlighting] final class CompilerHighlightingEditorFocusListener(editor: Editor) extends FocusAdapter {
  override def focusGained(e: FocusEvent): Unit = {
    focusGained()
  }

  override def focusLost(e: FocusEvent): Unit = {
    focusLost()
  }

  def focusGained(): Unit = {
    withProjectAndVirtualFile { (project, file) =>
      val request = TriggerPhaseEvents.newRequestId()
      Tracing(project).instant(TriggerPhaseEvents.create(request, "editor focus"))
      OnEditorFocusTrigger.trigger(project = project, virtualFile = file, requestId = request)
    }
  }

  def focusLost(): Unit = {
    withProjectAndVirtualFile { (project, file) =>
      DocumentCompilerAvailabilityService(project).disable(file)
    }
  }

  private def withProjectAndVirtualFile(action: (Project, VirtualFile) => Unit): Unit = {
    for {
      project <- Option(editor.getProject) if !project.isDisposed
      file <- Option(editor.getVirtualFile) if file.isValid
    } action(project, file)
  }
}
