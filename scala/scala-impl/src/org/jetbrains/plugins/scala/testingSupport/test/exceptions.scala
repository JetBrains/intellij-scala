package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.{RuntimeConfigurationError, RuntimeConfigurationException}

/**
 * These special exceptions are used by the IntelliJ platform for exceptional control flow
 */
trait exceptions {

  private[test] final def configurationException(message: String) = new RuntimeConfigurationException(message)
  private[test] final def configurationError(message: String) = new RuntimeConfigurationError(message)
  private[test] final def executionException(message: String) = new ExecutionException(message)
  private[test] final def executionException(message: String, cause: Throwable) = new ExecutionException(message, cause)
}

object exceptions extends exceptions