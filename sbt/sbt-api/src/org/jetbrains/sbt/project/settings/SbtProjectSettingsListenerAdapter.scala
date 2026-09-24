package org.jetbrains.sbt.project.settings

import com.intellij.openapi.externalSystem.settings.{DelegatingExternalSystemSettingsListener, ExternalSystemSettingsListener}

import java.util

/**
 * Stub to satisfy scaffolding of ExternalSystem
 */
class SbtProjectSettingsListenerAdapter(listener: ExternalSystemSettingsListener[SbtProjectSettings])
  extends DelegatingExternalSystemSettingsListener[SbtProjectSettings](listener) with SbtProjectSettingsListener {

  override def onProjectRenamed(oldName: String, newName: String): Unit =
    super[DelegatingExternalSystemSettingsListener].onProjectRenamed(oldName, newName)

  override def onProjectsLoaded(settings: util.Collection[SbtProjectSettings]): Unit =
    super[DelegatingExternalSystemSettingsListener].onProjectsLoaded(settings)

  override def onProjectsLinked(settings: util.Collection[SbtProjectSettings]): Unit =
    super[DelegatingExternalSystemSettingsListener].onProjectsLinked(settings)

  override def onProjectsUnlinked(linkedProjectPaths: util.Set[String]): Unit =
    super[DelegatingExternalSystemSettingsListener].onProjectsUnlinked(linkedProjectPaths)

  override def onBulkChangeStart(): Unit =
    super[DelegatingExternalSystemSettingsListener].onBulkChangeStart()

  override def onBulkChangeEnd(): Unit =
    super[DelegatingExternalSystemSettingsListener].onBulkChangeEnd()
}
