package org.jetbrains.bsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class BspStartupActivity extends StartupActivity {
    @Override
    override def runActivity(project: Project): Unit = {
        // initialize build loop only for bsp projects
        if (BspUtil.isBspProject(project))
          BspBuildLoopService.getInstance(project)
    }
}
