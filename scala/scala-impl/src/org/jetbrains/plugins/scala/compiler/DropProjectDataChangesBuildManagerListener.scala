package org.jetbrains.plugins.scala.compiler

import java.util.UUID

import com.intellij.compiler.server.{BuildManager, BuildManagerListener}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode

/**
 * Issue: SCL-17303
 *
 * Drop changes of [[com.intellij.compiler.server.BuildManager.ProjectData]] to fix the problem.
 */
class DropProjectDataChangesBuildManagerListener
  extends BuildManagerListener {

  override def beforeBuildProcessStarted(project: Project, sessionId: UUID): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
      val getProjectPathMethod = classOf[BuildManager]
        .getDeclaredMethod("getProjectPath", classOf[Project])
      val getProjectDataMethod = classOf[BuildManager]
        .getDeclaredMethod("getProjectData", classOf[String])
      val dropChangesMethod = Class.forName("com.intellij.compiler.server.BuildManager$ProjectData")
        .getDeclaredMethod("dropChanges")

      getProjectPathMethod.setAccessible(true)
      getProjectDataMethod.setAccessible(true)
      dropChangesMethod.setAccessible(true)

      val buildManager = BuildManager.getInstance()
      val projectPath = getProjectPathMethod.invoke(buildManager, project)
      val projectData = getProjectDataMethod.invoke(buildManager, projectPath)
      dropChangesMethod.invoke(projectData)
    }
}
