package org.jetbrains.sbt.project.versionNotifications.utils

import com.intellij.build.events.{BuildEvent, MessageEvent}
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import org.jetbrains.plugins.scala.extensions.IterableOnceExt

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

/**
 * Captures [[BuildEvent]]s from the external-system task bus via
 * [[com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager]],
 * unwrapping each [[ExternalSystemBuildEvent]]. Pairs with [[org.jetbrains.plugins.scala.build.ExternalSystemNotificationReporter]]
 * (sbt project resolution).
 *
 * Not interchangeable with [[org.jetbrains.plugins.scala.build.BuildEventsProgressCollector]], which subscribes to
 * [[com.intellij.build.BuildViewManager]] and pairs with [[org.jetbrains.plugins.scala.build.BuildToolWindowReporter]]
 * (sbt shell, compiler flows): a warning routed via one bus is invisible to the other.
 */
private[versionNotifications]
final class ExternalSystemBuildMessageCollector extends ExternalSystemTaskNotificationListener {
  private val buildEvents: ConcurrentLinkedQueue[BuildEvent] = new ConcurrentLinkedQueue[BuildEvent]

  def getWarningEvents: Seq[MessageEvent] = {
    val messages = buildEvents.asScala.toSeq.filterByType[MessageEvent]
    messages.filter(_.getKind == MessageEvent.Kind.WARNING)
  }

  override def onTaskOutput(id: ExternalSystemTaskId, text: String, outputType: ProcessOutputType): Unit = {}

  //noinspection ApiStatus,UnstableApiUsage
  override def onStatusChange(event: ExternalSystemTaskNotificationEvent): Unit = event match {
    case buildEvent: ExternalSystemBuildEvent =>
      buildEvents.add(buildEvent.getBuildEvent)
    case _ =>
  }
}
