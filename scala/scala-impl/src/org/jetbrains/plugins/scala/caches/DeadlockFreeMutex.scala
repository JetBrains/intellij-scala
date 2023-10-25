package org.jetbrains.plugins.scala.caches

import org.jetbrains.plugins.scala.caches.DeadlockFreeMutex.{ThreadToken, currentThreadToken}
import org.jetbrains.plugins.scala.util.RichThreadLocal

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

class DeadlockFreeMutex {
  private val currentlyExecutingToken = new AtomicReference[ThreadToken](null);

  def trySynchronized[R](f: => R, afterWait: => R): R = {
    val threadToken = currentThreadToken.value
    val present = currentlyExecutingToken.compareAndExchange(null, threadToken)
    if (present != null) {
      //someone else is currently calculating this, so wait for them
      val canWait = threadToken.startWaitingOn(present)
      try
        if (canWait) {
          // we are waiting for ourselves... so just
          this.synchronized {
            while (currentlyExecutingToken.get() != null) {
              this.wait()
            }
          }
        }
      finally threadToken.endWaiting()

      if (canWait) {
        return afterWait
      }
    }

    try f
    finally {
      this.synchronized {
        currentlyExecutingToken.compareAndExchange(threadToken, null)
        this.notifyAll()
      }
    }
  }
}

object DeadlockFreeMutex {
  private val currentThreadToken =  new RichThreadLocal(new ThreadToken)

  private class ThreadToken {
    private val waitingFor = new AtomicReference[ThreadToken](null);

    def startWaitingOn(target: ThreadToken): Boolean = {
      val prev = waitingFor.getAndSet(target)
      assert(prev == null)

      !target.isWaitingFor(this)
    }

    def endWaiting(): Unit = {
      waitingFor.set(null)
    }

    @tailrec
    private def isWaitingFor(target: ThreadToken): Boolean = {
      val waitingFor = this.waitingFor.get()
      if (waitingFor == null) {
        false
      } else if (waitingFor == target) {
        true
      } else {
        waitingFor.isWaitingFor(target)
      }
    }
  }
}