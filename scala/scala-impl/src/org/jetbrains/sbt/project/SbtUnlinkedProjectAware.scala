package org.jetbrains.sbt.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.autolink.{ExternalSystemProjectLinkListener, ExternalSystemUnlinkedProjectAware}
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtUnlinkedProjectAware.Delegate
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSettings

import java.util
import scala.jdk.CollectionConverters.IterableHasAsScala

//noinspection UnstableApiUsage,ApiStatus
class SbtUnlinkedProjectAware extends ExternalSystemUnlinkedProjectAware {
  override def getSystemId: ProjectSystemId = SbtProjectSystem.Id

  override def isBuildFile(project: Project, virtualFile: VirtualFile): Boolean =
    virtualFile.getName == Sbt.BuildFile

  override def isLinkedProject(project: Project, externalProjectPath: String): Boolean = {
    val sbtSettings = SbtSettings.getInstance(project).getLinkedProjectSettings(externalProjectPath)
    sbtSettings != null ||
      SbtUnlinkedProjectAwareHelper.isLinkedProject(project, externalProjectPath)
  }

  override def linkAndLoadProject(project: Project, externalProjectPath: String): Unit =
    new SbtOpenProjectProvider().linkToExistingProject(externalProjectPath, project)

  override def subscribe(project: Project,
                         listener: ExternalSystemProjectLinkListener,
                         parentDisposable: Disposable): Unit = {
    val settings = SbtSettings.getInstance(project)
    settings.subscribe(new Delegate(listener), parentDisposable)
  }

  override def getNotificationText: String =
    ExternalSystemBundle.message("unlinked.project.notification.load.action", getSystemId.getReadableName)
}

object SbtUnlinkedProjectAware {

  //noinspection ApiStatus,UnstableApiUsage
  private class Delegate(listener: ExternalSystemProjectLinkListener)
    extends ExternalSystemSettingsListener[SbtProjectSettings] {

    override def onProjectsLinked(settings: util.Collection[SbtProjectSettings]): Unit =
      settings.asScala.foreach(s => listener.onProjectLinked(s.getExternalProjectPath))

    override def onProjectsUnlinked(linkedProjectPaths: util.Set[String]): Unit =
      linkedProjectPaths.asScala.foreach(listener.onProjectUnlinked)

    override def onProjectRenamed(oldName: String, newName: String): Unit = {}
    override def onBulkChangeStart(): Unit = {}
    override def onBulkChangeEnd(): Unit = {}
  }
}

trait SbtUnlinkedProjectAwareHelper {

  /**
   * Has same semantics as [[ExternalSystemUnlinkedProjectAware.isLinkedProject]]
   *
   * @return true when the project which looks like sbt project (i.e. has `build.sbt` file)
   *         is already imported as some other type of project (e.g. as BSP project, which uses SBT as a server)
   */
  def isLinkedProject(project: Project, externalProjectPath: String): Boolean
}

object SbtUnlinkedProjectAwareHelper {
  private val EpName = ExtensionPointName.create[SbtUnlinkedProjectAwareHelper]("org.intellij.scala.sbtUnlinkedProjectAwareHelper")

  def isLinkedProject(project: Project, externalProjectPath: String): Boolean =
    EpName.getExtensionList.stream().anyMatch(_.isLinkedProject(project, externalProjectPath))
}