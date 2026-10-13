package org.jetbrains.plugins.scala.compiler.highlighting.services.core.runner

import com.intellij.openapi.diagnostic.Logger

trait TaskRunner:
  
  protected val Log: Logger = Logger.getInstance(classOf[TaskRunner])
  
  def wakeUp(): Unit
  
  protected def task(): Runnable