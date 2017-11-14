package org.jetbrains.plugins.cbt.runner.internal

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.runner.{CbtOutputFilter, CbtProcessListener}

class CbtImportConfigurationFactory(useDirect: Boolean,
                                    typez: ConfigurationType,
                                    listener: CbtProcessListener,
                                    cbtOuptutFilterOpt: Option[CbtOutputFilter])
  extends ConfigurationFactory(typez) {
  override def createTemplateConfiguration(project: Project): RunConfiguration =
    new CbtImportConfiguration(project, useDirect, listener, cbtOuptutFilterOpt, this)
}

