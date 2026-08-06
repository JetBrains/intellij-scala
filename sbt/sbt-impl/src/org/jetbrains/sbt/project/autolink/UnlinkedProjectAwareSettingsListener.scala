package org.jetbrains.sbt.project.autolink

import com.intellij.openapi.externalSystem.autolink.ExternalSystemProjectLinkListener
import com.intellij.openapi.externalSystem.settings.{ExternalProjectSettings, ExternalSystemSettingsListener}

import java.util
import scala.jdk.CollectionConverters.IterableHasAsScala

//noinspection UnstableApiUsage,ApiStatus
class UnlinkedProjectAwareSettingsListener[T <: ExternalProjectSettings](listener: ExternalSystemProjectLinkListener)
  extends ExternalSystemSettingsListener[T] {

  override def onProjectsLinked(settings: util.Collection[T]): Unit =
    settings.asScala.foreach(s => listener.onProjectLinked(s.getExternalProjectPath))

  override def onProjectsUnlinked(linkedProjectPaths: util.Set[String]): Unit =
    linkedProjectPaths.asScala.foreach(listener.onProjectUnlinked)

  override def onProjectRenamed(oldName: String, newName: String): Unit = {}
  override def onBulkChangeStart(): Unit = {}
  override def onBulkChangeEnd(): Unit = {}
}
