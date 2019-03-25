package org.jetbrains.plugins.scala
package project
package notification

import com.intellij.framework.FrameworkTypeEx
import com.intellij.framework.addSupport.impl.AddSupportForSingleFrameworkDialog.createDialog
import com.intellij.openapi.module.{JavaModuleType, Module, ModuleType}
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications

/**
 * @author Pavel Fatin
 */
//noinspection TypeAnnotation
final class SetupScalaSdkNotificationProvider(project: Project, notifications: EditorNotifications)
  extends AbstractNotificationProvider(ScalaBundle.message("sdk.title"), project, notifications) {

  override protected def panelText(kitTitle: String): String = s"No $kitTitle in module"

  override protected def hasDeveloperKit(module: Module): Boolean =
    ModuleType.get(module) != JavaModuleType.getModuleType ||
      module.getName.endsWith("-build") || // gen-idea doesn't use the sbt module type
      module.hasScala

  override protected def setDeveloperKit(module: Module): Unit =
    createDialog(
      module,
      FrameworkTypeEx.EP_NAME.findExtension(classOf[template.ScalaFrameworkType]).createProvider
    ).showAndGet
}
