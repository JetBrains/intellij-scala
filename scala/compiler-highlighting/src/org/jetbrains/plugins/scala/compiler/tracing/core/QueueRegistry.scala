package org.jetbrains.plugins.scala.compiler.tracing.core

import java.util
import java.util.concurrent.ConcurrentHashMap

final class QueueRegistry[K, V] extends Registry[K, V] {

  private val spans = new ConcurrentHashMap[K, util.ArrayDeque[V]]()

  override def add(key: K, span: V): Unit =
    // `compute` keeps add atomic with `get` for the same key, so the queue can't be removed mid-add.
    spans.compute(key, (_, queue) => {
      val q = if (queue == null) new util.ArrayDeque[V]() else queue
      q.addLast(span)
      q
    })

  override def get(key: K): Option[V] = {
    var polled = Option.empty[V]
    spans.computeIfPresent(key, (_, queue) => {
      polled = Option(queue.pollFirst())
      // Returning null drops the mapping, so emptied keys don't pile up.
      if (queue.isEmpty) null else queue
    })
    polled
  }

  override def peek(key: K): Option[V] =
    Option(spans.get(key)).flatMap(queue => Option(queue.peekFirst()))

  override def carry(from: K, to: K): Unit =
    get(from).foreach(add(to, _))
}
