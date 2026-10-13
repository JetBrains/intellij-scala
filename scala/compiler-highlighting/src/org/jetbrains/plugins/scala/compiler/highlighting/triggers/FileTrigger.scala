package org.jetbrains.plugins.scala.compiler.highlighting.triggers

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.services.TriggerService

private[highlighting] object FileTrigger:
  def trigger(project: Project,  virtualFile: VirtualFile, reason: String): Unit =
    val debugReason = s"[${virtualFile.getPresentableName} content changed]: $reason"  
    TriggerService(project).schedule(virtualFile, debugReason)
