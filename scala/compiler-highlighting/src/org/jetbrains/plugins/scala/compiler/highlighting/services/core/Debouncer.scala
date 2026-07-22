package org.jetbrains.plugins.scala.compiler.highlighting.services.core

/**
 * Coalesces notifications into a single action per key per quiet period.
 *
 * Windows are independent per key: traffic on one key neither postpones nor is postponed by traffic on another.
 */
private[highlighting] trait Debouncer[K] {

  /** Replaces whatever was pending for `key` with `action`, and restarts that key's window. */
  def queue(key: K)(action: => Unit): Unit

  /** Cancels any pending action for the given key. */
  def cancel(key: K): Unit
}
