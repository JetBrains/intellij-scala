package org.jetbrains.plugins

import java.io.File
import java.nio.file.Paths

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.plugins.cbt.project.settings.CbtSystemSettings

import _root_.scala.collection.JavaConverters._
import _root_.scala.language.implicitConversions
import _root_.scala.util.Try


package object cbt {

  implicit class RichXml(val xml: _root_.scala.xml.NodeSeq) {
    def value: String =
      xml.text.trim
  }

  implicit class RichString(val str: String) {
    def toFile: File =
      new File(str)

    def rtrim: String =
      str.replaceAll("\\s+$", "")
  }

  implicit class RichBoolean(val bool: Boolean) {
    def option[A](a: A): Option[A] =
      if (bool) Some(a) else None
  }

  implicit class FileOps(val file: File) {
    def / (path: String): File = new File(file, path)
  }

  implicit class RichProject(val project: Project) {
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
      Try {
        CbtSystemSettings.instance(project)
          .getLinkedProjectSettings(project.getBasePath)
      }
        .toOption
        .exists(_ != null)
  }

  implicit class RichModule(val module: Module) {
    def baseDir: String = {
      lazy val defaultPath =
        module.getModuleFile.getParent.getCanonicalPath
      val project = module.getProject
      val projectNodes =
        for {
          projectInfo <- Option(
            ProjectDataManager.getInstance
              .getExternalProjectData(project, CbtProjectSystem.Id, project.getBasePath))
          projectStructure <- Option(projectInfo.getExternalProjectStructure)
        } yield projectStructure.getChildren.asScala
      val pathOpt =
        for {
          nodes <- projectNodes.toSeq
          node <- nodes
          if node.getKey == ProjectKeys.MODULE &&
            node.getData(ProjectKeys.MODULE).getExternalName == module.getName
          childNode <- node.getChildren.asScala
          if childNode.getKey == ProjectKeys.CONTENT_ROOT
          contentRootData = childNode.getData(ProjectKeys.CONTENT_ROOT)
        } yield contentRootData.getRootPath
      pathOpt.headOption.getOrElse(defaultPath)
    }
  }

  def runnable(action: => Unit): Runnable = new Runnable {
    override def run(): Unit = action
  }

  def runReadAction(action: => Unit): Unit =
    ApplicationManager.getApplication.runReadAction(runnable(action))

  implicit def scalaFunction1ToIdeaOne[A, B](f: A => B): com.intellij.util.Function[A, B] = (x: A) => f(x)

  implicit def scalaFunction2ToIdeaOne[A, B, C](f: (A, B) => C): com.intellij.util.Function[com.intellij.openapi.util.Pair[A, B], C] =
    (x: com.intellij.openapi.util.Pair[A, B]) => f(x.first, x.second)
}
