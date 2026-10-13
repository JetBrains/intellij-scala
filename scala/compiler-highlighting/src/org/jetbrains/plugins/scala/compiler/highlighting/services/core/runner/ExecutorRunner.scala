package org.jetbrains.plugins.scala.compiler.highlighting.services.core.runner

import com.intellij.openapi.Disposable

import java.util.concurrent.ExecutorService

/**
 *  Execute the task on every `wakeUp` call
 */
trait ExecutorRunner(executor: ExecutorService) extends TaskRunner, Disposable:
  
  override def wakeUp(): Unit = executor.execute(task())

  override def dispose(): Unit = executor.shutdown()