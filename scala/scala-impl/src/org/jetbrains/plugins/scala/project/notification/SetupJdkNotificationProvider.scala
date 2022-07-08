package org.jetbrains.plugins.scala
package project
package notification

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import org.jetbrains.plugins.scala.ScalaBundle

final class SetupJdkNotificationProvider(project: Project)
  extends AbstractNotificationProvider(ScalaBundle.message("kit.title.jdk"), project) {

  override protected def panelText(kitTitle: String): String =
    ScalaBundle.message("project.jdk.is.not.defined")

  override protected def hasDeveloperKit(file: VirtualFile): Boolean =
    ProjectRootManager.getInstance(project).getProjectSdk != null

  override def setDeveloperKit(file: VirtualFile, panel: EditorNotificationPanel): Unit = {
    SdkPopupFactory.newBuilder()
      .withProject(project)
      .withSdkType(JavaSdk.getInstance())
      .updateProjectSdkFromSelection()
      .buildPopup()
      .showUnderneathToTheRightOf(panel)
  }

}
