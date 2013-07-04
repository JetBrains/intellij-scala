package org.jetbrains.sbt
package project

import java.io.File
import java.util
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.externalSystem.service.project.{ProjectStructureHelper, PlatformFacade}
import com.intellij.openapi.projectRoots.{Sdk, ProjectJdkTable, JavaSdk}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil._
import collection.JavaConverters._
import SbtProjectDataService._
import org.jetbrains.plugins.scala.components.HighlightingAdvisor

/**
 * @author Pavel Fatin
 */
class SbtProjectDataService(platformFacade: PlatformFacade, helper: ProjectStructureHelper)
  extends AbstractDataService[ScalaProjectData, Project](ScalaProjectData.Key) {

  def doImportData(toImport: util.Collection[DataNode[ScalaProjectData]], project: Project) {
    toImport.asScala.foreach { node =>
      val data = node.getData

      val projectJdk = findJdkBy(data.javaHome).orElse(allJdks.headOption)

      projectJdk.foreach { jdk =>
        val rootManager: ProjectRootManager = ProjectRootManager.getInstance(project)
        rootManager.setProjectSdk(jdk)
      }
    }

    val highlightingSettings = project.getComponent(classOf[HighlightingAdvisor]).getState()
    highlightingSettings.TYPE_AWARE_HIGHLIGHTING_ENABLED = true
    highlightingSettings.SUGGEST_TYPE_AWARE_HIGHLIGHTING = false
  }

  def doRemoveData(toRemove: util.Collection[_ <: Project], project: Project) {}
}

object SbtProjectDataService {
  def findJdkBy(home: File): Option[Sdk] = {
    val homePath = toCanonicalPath(home.getAbsolutePath)
    allJdks.find(jdk => homePath.startsWith(toCanonicalPath(jdk.getHomePath)))
  }

  def allJdks: Seq[Sdk] = ProjectJdkTable.getInstance.getSdksOfType(JavaSdk.getInstance).asScala
}