package org.jetbrains.plugins.scala.bazel

import com.intellij.execution.RunConfigurationProducerSuppressor
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.project.Project
import org.jetbrains.bazel.config.ProjectKt
import org.jetbrains.plugins.scala.runner.BaseScalaApplicationConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer

/**
 * Disables non-Bazel run config producers for Bazel projects.
 * See org.jetbrains.bazel.run.BazelRunConfigurationProducerSuppressor for more details.
 */
//noinspection ApiStatus,UnstableApiUsage
class BazelScalaRunConfigurationSuppressor extends RunConfigurationProducerSuppressor {
  override def shouldSuppress(runConfigurationProducer: RunConfigurationProducer[?], project: Project): Boolean = {
    if (!ProjectKt.isBazelProject(project)) return false
    runConfigurationProducer match {
      case _: AbstractTestConfigurationProducer[?] => true
      case _: BaseScalaApplicationConfigurationProducer[?] => true
      case _ => false
    }
  }
}
