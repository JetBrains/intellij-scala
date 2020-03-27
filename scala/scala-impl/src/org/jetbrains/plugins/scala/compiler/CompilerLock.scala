package org.jetbrains.plugins.scala.compiler

import java.util.concurrent.{ConcurrentHashMap, Semaphore}

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

object CompilerLock {

  private val locks = new ConcurrentHashMap[Project, Semaphore]

  def lock(project: Project): Unit =
    getLock(project).acquire()

  def unlock(project: Project): Unit =
    getLock(project).release()

  def isLocked(project: Project): Boolean =
    getLock(project).availablePermits == 0

  def withLock[A](project: Project)
                 (action: => A): A = {
    lock(project)
    try action
    finally unlock(project)
  }

  private def getLock(project: Project): Semaphore =
    locks.computeIfAbsent(project, { _ =>
      Disposer.register(project, () => locks.remove(project))
      new Semaphore(1, true)
    })
}
