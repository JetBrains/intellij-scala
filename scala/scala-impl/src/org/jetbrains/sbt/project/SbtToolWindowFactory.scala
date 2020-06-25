package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.service.task.ui.AbstractExternalSystemToolWindowFactory
import com.intellij.openapi.project.Project

/**
 * @author Pavel Fatin
 */
class SbtToolWindowFactory extends AbstractExternalSystemToolWindowFactory(SbtProjectSystem.Id) {
  override def isApplicable(project: Project): Boolean = SbtUtil.isSbtProject(project)
}