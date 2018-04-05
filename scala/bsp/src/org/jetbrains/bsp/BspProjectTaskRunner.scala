package org.jetbrains.bsp

import java.util

import com.intellij.openapi.project.Project
import com.intellij.task.{ProjectTask, ProjectTaskContext, ProjectTaskNotification, ProjectTaskRunner}

class BspProjectTaskRunner extends ProjectTaskRunner {

  override def canRun(projectTask: ProjectTask): Boolean = ???

  override def run(project: Project,
                   projectTaskContext: ProjectTaskContext,
                   projectTaskNotification: ProjectTaskNotification,
                   collection: util.Collection[_ <: ProjectTask]): Unit = {
    ???
  }
}
