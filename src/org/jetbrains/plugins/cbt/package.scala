package org.jetbrains.plugins

import java.io.File

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.plugins.cbt.project.settings.CbtSystemSettings

package object cbt {

  implicit class XmlOps(val xml: _root_.scala.xml.NodeSeq) {
    def value: String =
      xml.text.trim
  }

  implicit class StringOps(val str: String) {
    def toFile: File =
      new File(str)
  }

  implicit class ProjectOps(val project: Project) {
    def refresh(): Unit = {
      ApplicationManager.getApplication.invokeLater(new Runnable() {
        override def run(): Unit =
          ExternalSystemUtil.refreshProjects(
            new ImportSpecBuilder(project, CbtProjectSystem.Id)
              .forceWhenUptodate()
              .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
          )
      })
    }

    def isCbtProject: Boolean =
      CbtSystemSettings.getInstance(project).getLinkedProjectSettings(project.getBasePath) != null
  }
}
