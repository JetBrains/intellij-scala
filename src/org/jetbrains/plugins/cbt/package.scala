package org.jetbrains.plugins

import java.io.File

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.plugins.cbt.project.settings.CbtSystemSettings

import _root_.scala.util.Try

package object cbt {

  implicit class ReachXml(val xml: _root_.scala.xml.NodeSeq) {
    def value: String =
      xml.text.trim
  }

  implicit class ReachString(val str: String) {
    def toFile: File =
      new File(str)

    def rtrim: String =
      str.replaceAll("\\s+$", "")
  }

  implicit class ReachBoolean(val bool: Boolean) {
    def option[A](a: A): Option[A] =
      if (bool) Some(a) else None
  }

  implicit class FileOps(val file: File) {
    def / (path: String): File = new File(file, path)
  }

  implicit class ReachProject(val project: Project) {
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
      Try{
        CbtSystemSettings.getInstance(project)
          .getLinkedProjectSettings(project.getBasePath)
      }
        .toOption
        .exists(_ != null)
  }

  implicit class ReachModule(val module: Module) {
    def baseDir: String = //TODO get from data node
      module.getModuleFile.getParent.getCanonicalPath
  }

}
