package org.jetbrains.plugins.scala.runner

import com.intellij.compiler.impl.CompilerUtil
import com.intellij.execution.ExecutionListener
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.compiler.CompilerPaths
import org.jetbrains.plugins.scala.runner.ScalaApplicationConfigurationProducer.isScala3ApplicationConfiguration

import java.util

class SyncOutputExecutionListener extends ExecutionListener {
  override def processStarting(executorId: String, env: ExecutionEnvironment): Unit = {
    env.getRunProfile match {
      case app: ApplicationConfiguration if isScala3ApplicationConfiguration(app) =>
        val outputPaths = CompilerPaths.getOutputPaths(app.getModules)
        CompilerUtil.refreshOutputRoots(util.Arrays.asList(outputPaths: _*))
      case _ =>
    }
  }
}