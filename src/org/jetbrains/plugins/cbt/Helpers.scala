package org.jetbrains.plugins.cbt

import java.io.File

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.sbt.project.SbtProjectSystem

import scala.xml.NodeSeq

object Helpers {

  implicit class XmlOps(val xml: NodeSeq) {
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
  }

}
