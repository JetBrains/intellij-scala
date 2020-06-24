package org.jetbrains.plugins.scala.externalHighlighters

import java.util.concurrent.Semaphore

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project

trait CompilerLock {
  def lock(): Unit

  /**
   * @param exceptionIfNotLocked #SCL-17720
   */
  def unlock(exceptionIfNotLocked: Boolean = true): Unit

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

  private val lockSynchronizer = new Object
  private val unlockSynchronizer = new Object
  private val semaphore = new Semaphore(1, true)

  override def lock(): Unit = lockSynchronizer.synchronized {
    semaphore.acquire()
  }

  override def unlock(exceptionIfNotLocked: Boolean): Unit = unlockSynchronizer.synchronized {
    val permits = semaphore.availablePermits()
    if (permits == 0) {
      semaphore.release()
    } else if (exceptionIfNotLocked) {
      val msg = s"Can't unlock compiler for $project. Available permits: $permits."
      throw new IllegalStateException(msg)
    }
  }
}
