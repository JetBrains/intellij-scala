package org.jetbrains.plugins.scala
package project
package notification

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.roots.{ModuleRootManager, ModuleRootModificationUtil}

/**
 * @author Pavel Fatin
 */
final class SetupJdkNotificationProvider(project: Project)
  extends AbstractNotificationProvider("JDK", project) {

  override protected def panelText(kitTitle: String) = s"Project $kitTitle is not defined"

  override protected def hasDeveloperKit(module: Module): Boolean =
    ModuleRootManager.getInstance(module).getSdk != null

  override protected def setDeveloperKit(module: Module): Unit =
    ProjectSettingsService.getInstance(project).chooseAndSetSdk() match {
      case null =>
      case _ => inWriteAction(ModuleRootModificationUtil.setSdkInherited(module))
    }
}
