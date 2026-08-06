package org.jetbrains.plugins.scala.components

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.startup.ProjectActivity

private final class ScalaPluginVersionVerifierProjectActivity extends ProjectActivity {
  override def execute(project: Project): Unit = {
    // Application-level services are initialised exactly once.
    ApplicationManager.getApplication.getService(classOf[ScalaPluginVersionVerifierProjectActivity.AppService])
  }
}

private object ScalaPluginVersionVerifierProjectActivity {
  @Service(Array(Service.Level.APP))
  private final class AppService {
    // Executed in the service constructor.
    initPluginUpdater()

    private def initPluginUpdater(): Unit = invokeLater {
      ScalaPluginUpdater.askUpdatePluginBranchIfNeeded()
      ScalaPluginUpdater.postCheckIdeaCompatibility()
    }
  }
}
