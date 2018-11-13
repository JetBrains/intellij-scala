package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListenerAdapter}
import com.intellij.openapi.util.registry.{Registry, RegistryValue, RegistryValueListener}

//temporary workaround to disable long "Resolving external annotations" background task for sbt project
//should be unnecessary after 2018.3.1
class ExternalAnnotationSearchDisabler extends ExternalSystemTaskNotificationListenerAdapter {

  private def registryValue: RegistryValue = Registry.get("external.system.import.resolve.annotations")

  private var previous: Boolean = registryValue.asBoolean()

  override def onStart(id: ExternalSystemTaskId, workingDir: String): Unit = {
    if (id.getProjectSystemId == SbtProjectSystem.Id) {
      registryValue.setValue(false)
    } else {
      registryValue.setValue(previous)
    }
  }
}

