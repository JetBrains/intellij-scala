package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.project.Project

import java.util.concurrent.Semaphore

trait CompilerLock {

  /**
   * @param sessionId the lock source
   */
  def lock(sessionId: String): Unit

  /**
   * Unlock can be done only if it was locked with the same sessionId.
   *
   * @param exceptionIfNotLocked #SCL-17720
   */
  def unlock(sessionId: String,
             exceptionIfNotLocked: Boolean = true): Unit
}

object CompilerLock {
  
  def get(project: Project): CompilerLock =
    project.getService(classOf[CompilerLock])
}

private class CompilerLockImpl(project: Project)
  extends CompilerLock {

  private val lockSynchronizer = new Object
  private val unlockSynchronizer = new Object
  private val semaphore = new Semaphore(1, true)
  @volatile private var lockSessionId: Option[String] = None

  override def lock(sessionId: String): Unit = lockSynchronizer.synchronized {
    semaphore.acquire()
    lockSessionId = Some(sessionId)
  }

  override def unlock(sessionId: String,
                      exceptionIfNotLocked: Boolean): Unit = unlockSynchronizer.synchronized {
    val permits = semaphore.availablePermits()
    if (permits == 0) {
      if (lockSessionId.contains(sessionId)) {
        lockSessionId = None
        semaphore.release()
      } else {
        val msg = s"unlock($sessionId) for $project failed. Locked with other session id: $lockSessionId"
        throw new IllegalStateException(msg)
      }
    } else if (exceptionIfNotLocked) {
      val msg = s"unlock($sessionId) for $project failed. Available permits: $permits"
      throw new IllegalStateException(msg)
    }
  }
}
