package org.jetbrains.bsp

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.startup.ProjectActivity

final class BspStartupActivity extends ProjectActivity {
  override def execute(project: Project): Unit = {
    // initialize build loop only for bsp projects
    if (BspUtil.isBspProject(project)) {
      BspBuildLoopService.getInstance(project)
    }
  }
}
