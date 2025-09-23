package org.jetbrains.bsp.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autolink.{ExternalSystemProjectLinkListener, ExternalSystemUnlinkedProjectAware}
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.JavaCoroutines
import org.jetbrains.bsp.BSP
import org.jetbrains.bsp.project.importing.{BspOpenProjectProvider, BspProjectOpenProcessor}
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

  override def isBuildFile(project: Project, buildFile: VirtualFile): Boolean = {
    // The buildFile parameter represents some child file or directory inside the externalProjectPath.
    // We need to take the parent of the buildFile, as this is necessary to determine if the project can be imported with BSP
    // and if it doesn't have a build.sbt file.
    // For BSP, this method is called unnecessarily many times for each child, but this is due to how it's implemented on the platform side.
    val parent = buildFile.getParent
    if (parent == null) return false
    val containsSbtBuildFile = parent.findChild(Sbt.BuildFile) != null
    !containsSbtBuildFile && BspProjectOpenProcessor.canOpenProject(parent)
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

  override def linkAndLoadProjectAsync(project: Project, externalProjectPath: String, $completion: Continuation[_ >: kotlin.Unit]): AnyRef =
    new BspOpenProjectProvider().linkToExistingProjectAsync(externalProjectPath, project, $completion)

  override def unlinkProject(project: Project, s: String, continuation: Continuation[_ >: kotlin.Unit]): AnyRef =
    JavaCoroutines.suspendJava[kotlin.Unit](cont => cont.resume(kotlin.Unit.INSTANCE), continuation)
}
