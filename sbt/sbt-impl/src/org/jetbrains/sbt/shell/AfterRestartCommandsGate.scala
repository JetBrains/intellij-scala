package org.jetbrains.sbt.shell

import java.util.concurrent.LinkedBlockingQueue

/**
 * Thread-safe buffer for commands arriving while the sbt shell is in [[communication.SbtShellLifecycle.ShellState.SoftRestarting]]
 * or [[communication.SbtShellLifecycle.ShellState.ShuttingDown]] state.
 *
 * Commands are routed here during both soft restart and hard kill (when the sbt shell is in `ShuttingDown` state) to provide consistent handling across shutdown scenarios.
 * The `closed` flag indicates whether the buffer has already been flushed.
 */
private[shell] class AfterRestartCommandsGate[T] {

  private val lock = new Object
  private var closed: Boolean = false
  private val queue = new LinkedBlockingQueue[T]()

  /**
   * Attempts to add an item to the buffer.
   *
   * @return `true` if the item was accepted, `false` if the gate is already closed.
   */
  def enqueue(item: T): Boolean = lock.synchronized {
    if !closed then
      queue.put(item)
      true
    else
      false
  }

  /**
   * Close the gate and return all buffered items.
   * After this call, [[enqueue]] will return `false` until [[reopen]] is called.
   */
  def flushAndClose(): java.util.List[T] = lock.synchronized {
    if (closed) {
      return java.util.Collections.emptyList()
    }
    closed = true
    val flushed = new java.util.ArrayList[T]()
    queue.drainTo(flushed)
    flushed
  }

  /** Terminate all buffered items and clear the queue. */
  def terminateAllAndClear(terminateFn: T => Unit): Unit = lock.synchronized {
    queue.forEach(item => terminateFn(item))
    queue.clear()
  }

  /** Reset the gate for the next shell lifecycle. */
  def reopen(): Unit = lock.synchronized {
    closed = false
    queue.clear() // the queue should be empty at this point anyway
  }

  def tryRemove(predicate: T => Boolean): Option[T] = lock.synchronized {
    val it = queue.iterator()
    while (it.hasNext) {
      val item = it.next()
      if (predicate(item)) {
        it.remove()
        return Some(item)
      }
    }
    None
  }

  def size(): Int = lock.synchronized { queue.size() }
}
