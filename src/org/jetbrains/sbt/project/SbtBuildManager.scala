package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.build.ExternalSystemBuildManager
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import java.util.Collections
import java.util
import settings._

/**
 * @author Pavel Fatin
 */
class SbtBuildManager extends ExternalSystemBuildManager[SbtExecutionSettings] {
  def listTasks(id: ExternalSystemTaskId, projectPath: String, settings: SbtExecutionSettings) = Collections.emptyList()

  def executeTasks(id: ExternalSystemTaskId, taskNames: util.List[String], projectPath: String, settings: SbtExecutionSettings) {}
}