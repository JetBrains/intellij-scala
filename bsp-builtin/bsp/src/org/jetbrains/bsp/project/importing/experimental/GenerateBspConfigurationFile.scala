package org.jetbrains.bsp.project.importing.experimental

import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent}
import com.intellij.openapi.project.ProjectUtil
import org.jetbrains.bsp.{BspBundle, BspUtil}

// TODO SCL-24897
class GenerateBspConfigurationFile extends AnAction(
  BspBundle.message("generate.bsp.configuration.file")
) {

  override def update(e: AnActionEvent): Unit = {
    val project = e.getProject
    val isBspProject = project != null && BspUtil.isBspProject(project)
    if (!isBspProject) {
      e.getPresentation.setEnabledAndVisible(false)
    }
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  override def actionPerformed(e: AnActionEvent): Unit = {
    val project = e.getProject
    if (project == null)
      return

    val projectDir = ProjectUtil.guessProjectDir(project)
    if (projectDir == null)
      return

    val workspace = projectDir.toNioPath
    new GenerateBspConfig(project, workspace).runSynchronously()
  }
}
