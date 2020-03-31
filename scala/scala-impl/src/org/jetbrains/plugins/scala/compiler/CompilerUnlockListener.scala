package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.project.Project
import com.intellij.task.{ProjectTaskListener, ProjectTaskManager}

class CompilerUnlockListener(project: Project)
  extends ProjectTaskListener {

  override def finished(result: ProjectTaskManager.Result): Unit =
    CompilerLock.get(project).unlock()
}
