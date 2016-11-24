package org.jetbrains.plugins.scala
package project.notification

import com.intellij.framework.addSupport.impl.AddSupportForSingleFrameworkDialog.createDialog
import com.intellij.openapi.module.ModuleUtil.getModuleType
import com.intellij.openapi.module.{JavaModuleType, Module}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.ui.{EditorNotificationPanel, EditorNotifications}
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.project.notification.SetupScalaSdkNotificationProvider._
import org.jetbrains.plugins.scala.project.template.ScalaSupportProvider

/**
  * @author Pavel Fatin
  */
class SetupScalaSdkNotificationProvider(project: Project, notifications: EditorNotifications)
  extends AbstractNotificationProvider(project, notifications) {

  override def getKey = ProviderKey

  override protected def isSourceCode(file: PsiFile) =
    file.getLanguage.isKindOf(ScalaLanguage.INSTANCE) &&
      !file.getName.endsWith(".sbt") && // root SBT files belong to main (not *-build) modules
      file.isWritable

  override protected def hasDeveloperKit(module: Module) =
    getModuleType(module) != JavaModuleType.getModuleType ||
      module.getName.endsWith("-build") || // gen-idea doesn't use the SBT module type
      module.hasScala

  override protected def createTask(module: Module) = new Runnable {
    override def run() =
      createDialog(module, new ScalaSupportProvider).showAndGet
  }

  override protected def panelText = s"No $developerKitTitle in module"

  override protected def developerKitTitle = ScalaSDKTitle
}

object SetupScalaSdkNotificationProvider {
  private val ScalaSDKTitle = ScalaBundle.message("sdk.title")

  private val ProviderKey = Key.create[EditorNotificationPanel](ScalaSDKTitle)
}