package org.jetbrains.plugins.scala.externalHighlighters

import java.util.concurrent.Semaphore

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.externalHighlighters.CompilerLock.From

trait CompilerLock {
  def lock(from: From): Unit

  def unlock(from: From): Unit

  final def withLock[A](from: From)
                       (action: => A): A = {
    lock(from)
    try action
    finally unlock(from)
  }
}

object CompilerLock {
  
  sealed trait From
  
  object From {
    case object BuildProcess extends From
    case object JpsCompiler extends From
  }

  def get(project: Project): CompilerLock =
    ServiceManager.getService(project, classOf[CompilerLock])
}

private class CompilerLockImpl(project: Project)
  extends CompilerLock {

  private val lockSynchronizer = new Object
  private val unlockSynchronizer = new Object
  private val semaphore = new Semaphore(1, true)
  @volatile private var lastFrom: Option[From] = None
  
  override def lock(from: From): Unit = lockSynchronizer.synchronized {
    if (from == From.BuildProcess && lastFrom.contains(from) && semaphore.availablePermits() == 0) { // #SCL-17623
      unlockInternal(from)
    }
    semaphore.acquire()
    lastFrom = Some(from)
  }

  override def unlock(from: From): Unit =
    unlockInternal(from: From)
  
  private def unlockInternal(from: From): Unit = unlockSynchronizer.synchronized {
    val permits = semaphore.availablePermits()
    if (lastFrom.contains(from) && permits == 0) {
      lastFrom = None
      semaphore.release()
    } else {
      val msg = s"Can't unlock compiler for $project ($from). Available permits: $permits. Last from: $lastFrom"
      throw new IllegalStateException(msg)
    }
  }
}
