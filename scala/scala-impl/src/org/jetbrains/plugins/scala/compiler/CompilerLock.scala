package org.jetbrains.plugins.scala.compiler

import java.util.concurrent.Semaphore

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project

trait CompilerLock {
  def lock(): Unit

  def unlock(): Unit

  def isLocked: Boolean

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

private class CompilerLockImpl
  extends CompilerLock {

  private val semaphore = new Semaphore(1)

  override def lock(): Unit =
    semaphore.acquire()

  override def unlock(): Unit =
    semaphore.release()

  override def isLocked: Boolean =
    semaphore.availablePermits() == 0
}
