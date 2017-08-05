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
      ApplicationManager.getApplication.invokeLater(runnable {
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

  def runnable(action: => Unit): Runnable = new Runnable {
    override def run(): Unit = action
  }

  def runReadAction(action: => Unit): Unit =
    ApplicationManager.getApplication.runReadAction(runnable(action))

  implicit def scalaFunction1ToIdeaOne[A, B](f: A => B): com.intellij.util.Function[A, B] =
    new com.intellij.util.Function[A, B]() {
      override def fun(x: A): B = f(x)
    }

  implicit def scalaFunction2ToIdeaOne[A, B, C](f: (A, B) => C): com.intellij.util.Function[com.intellij.openapi.util.Pair[A, B], C] =
    new com.intellij.util.Function[com.intellij.openapi.util.Pair[A, B], C] () {
      override def fun(x: com.intellij.openapi.util.Pair[A, B]): C =
        f(x.first, x.second)
    }
}
