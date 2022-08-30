package org.jetbrains.plugins.scala.util

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project

object WriteCommandActionEx {
  //the method is required to workaround "can't overload method" error when using WriteCommandAction from scala
  def runWriteCommandAction(project: Project, runnable: Runnable): Unit = {
    WriteCommandAction.runWriteCommandAction(project, runnable)
  }
}
