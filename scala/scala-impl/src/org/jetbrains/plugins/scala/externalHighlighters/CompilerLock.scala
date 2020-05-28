package org.jetbrains.plugins.scala.externalHighlighters

import java.util.concurrent.Semaphore

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project

trait CompilerLock {
  def lock(): Unit

  def unlock(): Unit

  final def withLock[A](action: => A): A = {
    lock()
    try action
    finally unlock()
  }
}

object CompilerLock {

  def get(project: Project): CompilerLock =
    ServiceManager.getService(project, classOf[CompilerLock])
}

private class CompilerLockImpl(project: Project)
  extends CompilerLock {

  private val semaphore = new Semaphore(1)

  override def lock(): Unit =
    semaphore.acquire()

  override def unlock(): Unit = synchronized {
    val permits = semaphore.availablePermits()
    if (permits == 0)
      semaphore.release()
    else
      throw new IllegalStateException(s"Can't unlock compiler for $project. Available permits: $permits")
  }
}
