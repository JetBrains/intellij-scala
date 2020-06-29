package org.jetbrains.bsp

import com.intellij.execution.{RunManager, RunManagerListener}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.bsp.project.test.environment.BspFetchTestEnvironmentTaskInstaller

class BspStartupActivity extends StartupActivity {
    val logger= Logger.getInstance(classOf[BspStartupActivity])

    @Override
    override def runActivity(project: Project): Unit = try {
        // initialize build loop only for bsp projects
        if (BspUtil.isBspProject(project)) {
          BspBuildLoopService.getInstance(project)
          project.getMessageBus.connect().subscribe(RunManagerListener.TOPIC, new BspFetchTestEnvironmentTaskInstaller(project))
        }
    } catch {
      case e: Throwable => logger.error(e)
    }
}
