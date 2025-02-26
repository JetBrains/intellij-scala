package org.jetbrains.plugins.scala.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

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
}

object ScalaProjectConfigurationService {
  def getInstance(project: Project): ScalaProjectConfigurationService =
    project.getService(classOf[ScalaProjectConfigurationService])
}
