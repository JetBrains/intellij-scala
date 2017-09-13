package org.jetbrains.plugins.cbt.project

import com.intellij.openapi.externalSystem.service.task.ui.AbstractExternalSystemToolWindowFactory

class CbtToolWindowFactory
  extends AbstractExternalSystemToolWindowFactory(CbtProjectSystem.Id)