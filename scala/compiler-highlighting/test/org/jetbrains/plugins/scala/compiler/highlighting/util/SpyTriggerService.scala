package org.jetbrains.plugins.scala.compiler.highlighting.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.services.TriggerService

final class SpyTriggerService(project: Project) extends TriggerService:

  var scheduled: List[(VirtualFile, String)] = List.empty
  var bypassed: List[(VirtualFile, String)] = List.empty

  override def schedule(file: VirtualFile, reason: String): Unit = scheduled :+= file -> reason

  override def bypass(file: VirtualFile, reason: String): Unit = bypassed :+= file -> reason
