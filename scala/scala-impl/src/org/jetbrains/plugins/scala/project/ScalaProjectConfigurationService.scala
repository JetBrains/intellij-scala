package org.jetbrains.plugins.scala.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.ScalaFileType

/**
 * A service to track project sync and suppress editor notifications/highlighlings while it is in progress.
 * A workaround while there is no unified approach between Java/Kotlin/Scala projects import in Gradle/Maven/sbt.
 *
 * @see SCL-13000
 * @see SCL-22458
 * @see [[org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurationService]]
 * @see [[org.jetbrains.sbt.project.SbtNotificationListener]]
 * @see [[org.jetbrains.plugins.scala.annotator.ScalaProblemHighlightFilter]]
 */
@Service(Array(Service.Level.PROJECT))
final class ScalaProjectConfigurationService(private val project: Project) {
  @volatile private var syncInProgress: Boolean = false

  def isSyncInProgress: Boolean = syncInProgress

  @ApiStatus.Internal
  private[jetbrains] def onSyncStarted(): Unit = syncInProgress = true

  @ApiStatus.Internal
  private[jetbrains] def onSyncEnded(): Unit = syncInProgress = false

  def refreshEditorNotifications(): Unit = {
    val openFiles = FileEditorManager.getInstance(project).getOpenFiles
    val openScalaFiles = openFiles.filter(FileTypeRegistry.getInstance.isFileOfType(_, ScalaFileType.INSTANCE))
    if (openScalaFiles.isEmpty) return

    val editorNotifications = EditorNotifications.getInstance(project)
    openScalaFiles.foreach(editorNotifications.updateNotifications)
  }
}

object ScalaProjectConfigurationService {
  def getInstance(project: Project): ScalaProjectConfigurationService =
    project.getService(classOf[ScalaProjectConfigurationService])
}
