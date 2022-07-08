package org.jetbrains.plugins.scala
package project
package notification

import com.intellij.framework.FrameworkTypeEx
import com.intellij.framework.addSupport.impl.AddSupportForSingleFrameworkDialog.createDialog
import com.intellij.openapi.module.{JavaModuleType, Module, ModuleType, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import org.jetbrains.plugins.scala.ScalaBundle

final class SetupScalaSdkNotificationProvider(project: Project)
  extends AbstractNotificationProvider(ScalaBundle.message("sdk.title"), project) {

  override protected def panelText(kitTitle: String): String = ScalaBundle.message("no.kittitle.in.module", kitTitle)

  override protected def hasDeveloperKit(file: VirtualFile): Boolean =
    findModule(file).forall { module =>
      ModuleType.get(module) != JavaModuleType.getModuleType ||
        module.isBuildModule || // gen-idea doesn't use the sbt module type
        module.hasScala
    }

  override protected def setDeveloperKit(file: VirtualFile, panel: EditorNotificationPanel): Unit =
    findModule(file).foreach { module =>
      createDialog(
        module,
        FrameworkTypeEx.EP_NAME.findExtension(classOf[template.ScalaFrameworkType]).createProvider
      ).showAndGet
    }

  private def findModule(file: VirtualFile): Option[Module] =
    Option(ModuleUtilCore.findModuleForFile(file, project))

}
