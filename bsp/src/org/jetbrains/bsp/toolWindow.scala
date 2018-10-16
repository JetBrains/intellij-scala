package org.jetbrains.bsp

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalSystemToolWindowCondition
import com.intellij.openapi.externalSystem.service.task.ui.AbstractExternalSystemToolWindowFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry

class BspToolWindowFactory extends AbstractExternalSystemToolWindowFactory(BSP.ProjectSystemId)

class BspToolWindowFactoryCondition extends AbstractExternalSystemToolWindowCondition(BSP.ProjectSystemId) {
  override def value(project: Project): Boolean =
    Registry.get(BSP.RegistryKeyFeatureEnabled).asBoolean() &&
    super.value(project)
}