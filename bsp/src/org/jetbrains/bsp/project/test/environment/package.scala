package org.jetbrains.bsp.project.test

import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.module.Module

package object environment {
  object ModuleBasedConfiguration {
    def unapply(configuration: RunConfiguration): Option[(ModuleBasedConfiguration[_, _], Module)] = {
      configuration match {
        case config: ModuleBasedConfiguration[_, _] =>
          val module = config.getConfigurationModule.getModule
          Some(config, module)
        case _ => None
      }
    }
  }
}
