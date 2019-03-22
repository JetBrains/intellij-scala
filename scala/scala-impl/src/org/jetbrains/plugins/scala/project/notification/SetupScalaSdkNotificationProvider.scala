package org.jetbrains.plugins.scala
package project
package notification

import com.intellij.framework.FrameworkTypeEx
import com.intellij.framework.addSupport.impl.AddSupportForSingleFrameworkDialog.createDialog
import com.intellij.openapi.module.{JavaModuleType, Module, ModuleType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.{EditorNotificationPanel, EditorNotifications}

/**
  * @author Pavel Fatin
  */
//noinspection TypeAnnotation
final class SetupScalaSdkNotificationProvider(project: Project, notifications: EditorNotifications)
  extends AbstractNotificationProvider(project, notifications) {

  override protected val developerKitTitle = ScalaBundle.message("sdk.title")

  override val getKey = Key.create[EditorNotificationPanel](developerKitTitle)

  override protected def panelText = s"No $developerKitTitle in module"

  override protected def hasDeveloperKit(module: Module): Boolean =
    ModuleType.get(module) != JavaModuleType.getModuleType ||
      module.getName.endsWith("-build") || // gen-idea doesn't use the sbt module type
      module.hasScala

  override protected def createTask(module: Module) = () => {
    createDialog(
      module,
      FrameworkTypeEx.EP_NAME.findExtension(classOf[template.ScalaFrameworkType]).createProvider
    ).showAndGet
  }
}
