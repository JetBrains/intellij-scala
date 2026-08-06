package org.jetbrains.sbt.project.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.autolink.{ExternalSystemProjectLinkListener, ExternalSystemUnlinkedProjectAware}
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.JavaCoroutines
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.{SbtOpenProjectProvider, SbtProjectSystem}
import org.jetbrains.sbt.settings.SbtSettings

import kotlin.coroutines.Continuation

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

  override def linkAndLoadProjectAsync(project: Project, externalProjectPath: String, $completion: Continuation[? >: kotlin.Unit]): AnyRef =
    new SbtOpenProjectProvider().linkToExistingProjectAsync(externalProjectPath, project, $completion)

  override def subscribe(project: Project,
                         listener: ExternalSystemProjectLinkListener,
                         parentDisposable: Disposable): Unit = {
    val settings = SbtSettings.getInstance(project)
    settings.subscribe(new UnlinkedProjectAwareSettingsListener[SbtProjectSettings](listener), parentDisposable)
  }

  override def unlinkProject(project: Project, s: String, continuation: Continuation[? >: kotlin.Unit]): AnyRef =
    JavaCoroutines.suspendJava[kotlin.Unit](cont => cont.resume(kotlin.Unit.INSTANCE), continuation)
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
