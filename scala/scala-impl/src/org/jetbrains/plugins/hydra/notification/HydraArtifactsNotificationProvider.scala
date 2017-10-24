package org.jetbrains.plugins.hydra.notification

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.{EditorNotificationPanel, EditorNotifications}
import org.jetbrains.plugins.hydra.compiler.HydraCompilerSettings
import org.jetbrains.plugins.hydra.settings.HydraApplicationSettings
import org.jetbrains.plugins.scala.compiler.CompileServerManager
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.project.notification.AbstractNotificationProvider
import org.jetbrains.plugins.hydra.notification.HydraArtifactsNotificationProvider._

/**
  * @author Maris Alexandru
  */
class HydraArtifactsNotificationProvider(project: Project, notifications: EditorNotifications)
  extends AbstractNotificationProvider(project, notifications) {

  override def getKey: Key[EditorNotificationPanel] = ProviderKey

  override protected def hasDeveloperKit(module: Module): Boolean = {
    val downloadedScalaVersions = HydraApplicationSettings.getInstance().getDownloadedScalaVersions
    val hydraSettings = HydraCompilerSettings.getInstance(project)
    val scalaVersions = for {
      module <- project.scalaModules
      scalaVersion <- module.sdk.compilerVersion
    } yield scalaVersion

    if(hydraSettings.isHydraEnabled) {
      scalaVersions.forall(downloadedScalaVersions.contains(_))
    } else {
      true
    }
  }

  override protected def createTask(module: Module): Runnable = () => CompileServerManager.showHydraCompileSettingsDialog(project)

  override protected def panelText: String = s"No $developerKitTitle artifacts in module. Please download the artifacts."

  override protected def developerKitTitle: String = HydraTitle
}

object HydraArtifactsNotificationProvider {
  private val HydraTitle = "Hydra"

  private val ProviderKey = Key.create[EditorNotificationPanel](HydraTitle)
}