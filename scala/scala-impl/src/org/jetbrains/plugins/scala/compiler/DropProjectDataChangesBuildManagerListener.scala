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
      val getProjectDataMethod = classOf[BuildManager]
        .getDeclaredMethod("getProjectData", classOf[String])
      val dropChangesMethod = Class.forName("com.intellij.compiler.server.BuildManager$ProjectData")
        .getDeclaredMethod("dropChanges")

      getProjectDataMethod.setAccessible(true)
      dropChangesMethod.setAccessible(true)

      val buildManager = BuildManager.getInstance()
      val projectData = getProjectDataMethod.invoke(buildManager, project.getBasePath)
      dropChangesMethod.invoke(projectData)
    }
}
