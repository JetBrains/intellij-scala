package org.jetbrains.bsp.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autolink.{ExternalSystemProjectLinkListener, ExternalSystemUnlinkedProjectAware}
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.JavaCoroutines
import org.jetbrains.bsp.project.importing.setup.BspSetupProvider
import org.jetbrains.bsp.{BSP, BspUtil}
import org.jetbrains.bsp.project.importing.BspOpenProjectProvider
import org.jetbrains.bsp.protocol.BspConnectionConfig
import org.jetbrains.bsp.settings.{BspProjectSettings, BspSettings}
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.autolink.UnlinkedProjectAwareSettingsListener
import org.jetbrains.sbt.settings.SbtSettings

import kotlin.coroutines.Continuation

/**
 * The unlinked project aware implementation for the BSP external system.
 * This determines whether the "link project" notification can be displayed for BSP mode but only if a `build.sbt` file is not present.
 * If a `build.sbt` file is detected, the sbt notification will be shown instead.
 */
class BspUnlinkedProjectAware extends ExternalSystemUnlinkedProjectAware {

  override def getSystemId: ProjectSystemId = BSP.ProjectSystemId

  /**
   * Determines whether the given file is a build file that belongs to the BSP external system.
   *
   * @note keep this method lightweight, as it may be called for every file/directory in the project root.
   */
  override def isBuildFile(project: Project, buildFile: VirtualFile): Boolean = {
    // The buildFile parameter represents some child file or directory inside the externalProjectPath.
    // We need to take the parent of the buildFile, as this is necessary to determine if the project
    // doesn't have a build.sbt file, as such projects should be imported with sbt.
    val parent = buildFile.getParent
    if (parent == null) return false
    val containsSbtBuildFile = parent.findChild(Sbt.BuildFile) != null
    !containsSbtBuildFile && isBspBuildFile(buildFile, project)
  }

  /**
   * Any changes to the logic of this method should also be reflected in [[BspProjectOpenProcessor.canOpenProject]],
   * as both methods are responsible for determining whether a workspace (or a file within it) belongs to the BSP.
   */
  private def isBspBuildFile(buildFile: VirtualFile, project: Project): Boolean = {
    val isBspConfigDir = BspConnectionConfig.isBspWorkspaceConfigDir(buildFile)
    val isBloopDir = BspUtil.isBloopConfigDir(buildFile)

    isBspConfigDir || isBloopDir || BspSetupProvider.isBuildFile(buildFile, project)
  }

  override def isLinkedProject(project: Project, externalProjectPath: String): Boolean = {
    val bspSetting = BspSettings.getInstance(project)
    val sbtSettings = SbtSettings.getInstance(project)
    Seq(bspSetting, sbtSettings).map(_.getLinkedProjectSettings(externalProjectPath)).exists(_ != null)
  }

  override def subscribe(project: Project, listener: ExternalSystemProjectLinkListener, disposable: Disposable): Unit = {
    val settings = BspSettings.getInstance(project)
    settings.subscribe(new UnlinkedProjectAwareSettingsListener[BspProjectSettings](listener), disposable)
  }

  override def linkAndLoadProjectAsync(project: Project, externalProjectPath: String, $completion: Continuation[? >: kotlin.Unit]): AnyRef =
    new BspOpenProjectProvider().linkToExistingProjectAsync(externalProjectPath, project, $completion)

  override def unlinkProject(project: Project, s: String, continuation: Continuation[? >: kotlin.Unit]): AnyRef =
    JavaCoroutines.suspendJava[kotlin.Unit](cont => cont.resume(kotlin.Unit.INSTANCE), continuation)
}
