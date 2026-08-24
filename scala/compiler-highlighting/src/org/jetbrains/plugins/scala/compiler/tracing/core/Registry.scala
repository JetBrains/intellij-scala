package org.jetbrains.plugins.scala.compiler.tracing.core

import com.intellij.openapi.diagnostic.Logger

import java.util
import java.util.concurrent.atomic.AtomicLong

/**
 * A generic store for associating values with keys.
 *
 * Implementations dictate the exact storage and retrieval semantics. For example, an implementation
 * might map a key to a single over-writable value, or it might map a key to a collection (like a FIFO queue) 
 * to handle multiple concurrent values.
 */
trait Registry[K, V] {

  /** * Associates `value` with `key`. 
   * Depending on the implementation, this may overwrite an existing value or add it to a collection. 
   */
  def add(key: K, value: V): Unit

  /** * Retrieves AND removes a value associated with `key`, if any.
   * For queues, this removes the oldest element. For single-value stores, it clears the key.
   */
  def get(key: K): Option[V]

  /**
   * Retrieves a value associated with `key` WITHOUT removing it from the registry.
   */
  def peek(key: K): Option[V]

  /** * Moves a value associated with `from` onto `to`. No-op if `from` has no associated value. 
   */
  def carry(from: K, to: K): Unit
}

object Registry {

  private val Log: Logger = Logger.getInstance(classOf[Registry[?, ?]])

  /**
   * Creates a bounded registry: one value per key, evicting the oldest entry once `maxCapacity` is
   * exceeded. Insertion order defines "oldest"; re-adding an existing key overwrites its value without
   * refreshing its age (it is a FIFO bound, not an LRU cache). 
   * This is useful when dealing with memory leaks.
   *
   *
   * Each individual call is atomic, which covers `add` / `get` / `peek`.
   * It does '''not''' cover sequences of calls or iteration — see `carry` and `toString` below.
   *
   * @param maxCapacity The maximum number of entries to hold
   */
  def apply[K, V](maxCapacity: Int = 1000): Registry[K, V] = new Registry[K, V] {

    private val evictions = new AtomicLong(0)

    private val store: util.Map[K, V] = util.Collections.synchronizedMap(
      new util.LinkedHashMap[K, V]() {
        override def removeEldestEntry(eldest: util.Map.Entry[K, V]): Boolean = {
          val evict = size() > maxCapacity
          log(maxCapacity, eldest, evict)
          evict
        }
      }
    )

    override def add(key: K, span: V): Unit = store.put(key, span)

    // Destructive read: removes the key from the map
    override def get(key: K): Option[V] = Option(store.remove(key))

    // Non-destructive read: leaves the key in the map
    override def peek(key: K): Option[V] = Option(store.get(key))

    /**
     * The removal and the re-insertion must share one critical section: two separately-locked calls leave
     * a window in which the value is under neither key, so a concurrent `peek` sees nothing. The map
     * returned by `Collections.synchronizedMap` uses itself as its mutex, so locking on `store` takes the
     * very monitor its own methods take (and re-entering it from `get` / `add` is free).
     */
    override def carry(from: K, to: K): Unit = store.synchronized {
      get(from).foreach(add(to, _))
    }

    /**
     * `values()` is a live view, so rendering it iterates the map — which a `synchronizedMap` only permits
     * inside its monitor. Without this lock a concurrent `add` / `get` makes it throw
     * `ConcurrentModificationException`, and this runs on a timer in `DebugContextTracingOps`.
     */
    override def toString: String = store.synchronized(store.values().toString)


    private def log(maxCapacity: Int, eldest: util.Map.Entry[K, V], evict: Boolean): Unit = {
      if (evict) {
        val count = evictions.incrementAndGet()
        if (count == 1 || count % maxCapacity == 0) {
          Log.warn(s"[tracing] registry is full at $maxCapacity entries, evicting the oldest " +
            s"(${eldest.getKey}); $count evicted so far")
        }
      }
    }
  }
}