package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.codeInsight.template.{Template, TemplateEditingAdapter, TemplateManagerListener}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.highlighting.services.TriggerService

final class TemplateListener(project: Project) extends TemplateManagerListener:
  
  override def templateStarted(state: TemplateState): Unit =
    val editor = state.getEditor
    if editor == null || state.isFinished then return

    val document = editor.getDocument
    val listener = new TemplateEditingAdapter:
      override def templateFinished(template: Template, brokenOff: Boolean): Unit =
        TriggerService(project).schedule(editor.getVirtualFile, "Template finished")

      override def templateCancelled(template: Template): Unit =
        TriggerService(project).schedule(editor.getVirtualFile, "Template canceled")
    
    state.addTemplateStateListener(listener)
