package org.jetbrains.plugins.scala
package project.notification

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil.setSdkInherited
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.util.Key
import com.intellij.ui.{EditorNotificationPanel, EditorNotifications}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.notification.SetupJdkNotificationProvider._

/**
  * @author Pavel Fatin
  */
class SetupJdkNotificationProvider(project: Project, notifications: EditorNotifications)
  extends AbstractNotificationProvider(project, notifications) {

  override def getKey = ProviderKey

  override protected def hasDeveloperKit(module: Module): Boolean = {
    module != null && ModuleRootManager.getInstance(module).getSdk != null
  }

  override protected def createTask(module: Module) = new Runnable {
    override def run(): Unit = {
      val chosenSdk = ProjectSettingsService.getInstance(project).chooseAndSetSdk()
      if (chosenSdk != null) {
        inWriteAction {
          setSdkInherited(module)
        }
      }
    }
  }

  override protected def panelText = s"Project $JDKTitle is not defined"

  override protected def developerKitTitle = JDKTitle
}

object SetupJdkNotificationProvider {
  private val JDKTitle = "JDK"

  private val ProviderKey = Key.create[EditorNotificationPanel](JDKTitle)
}