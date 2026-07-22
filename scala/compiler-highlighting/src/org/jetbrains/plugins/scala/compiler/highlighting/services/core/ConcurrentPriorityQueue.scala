package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import java.util.concurrent.ConcurrentSkipListSet
import scala.jdk.CollectionConverters.IteratorHasAsScala

/**
 * A thread-safe implementation of PriorityQueue backed by a Java ConcurrentSkipListSet.
 */
class ConcurrentPriorityQueue[T](implicit ordering: Ordering[T]) extends PriorityQueue[T] {

  private val queue: ConcurrentSkipListSet[T] = new ConcurrentSkipListSet[T](ordering)

  override def enqueue(item: T): Unit = {
    queue.add(item)
  }

  override def dequeueNext(): Option[T] = {
    Option(queue.pollFirst())
  }

  override def peekNext(): Option[T] = {
    try Some(queue.first())
    catch { case _: NoSuchElementException => None }
  }

  override def iteratorFrom(item: T): Iterator[T] = {
    queue.tailSet(item).iterator().asScala
  }

  override def removeElement(item: T): Boolean = {
    queue.remove(item)
  }

  override def clearAndDispose(onDispose: T => Unit): Unit = {
    queue.forEach(item => onDispose(item))
    queue.clear()
  }
}
