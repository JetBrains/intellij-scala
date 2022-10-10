package org.jetbrains.sbt.shell

import com.intellij.task.ProjectTaskRunner

/** Marker interface */
trait SbtProjectTaskRunner {
  self: ProjectTaskRunner =>
}
