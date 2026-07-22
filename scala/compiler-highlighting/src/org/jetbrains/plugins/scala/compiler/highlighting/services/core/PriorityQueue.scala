package org.jetbrains.plugins.scala.compiler.highlighting.services.core

/**
 * A generic priority queue interface.
 *
 * @tparam T The type of elements held in this queue.
 */
trait PriorityQueue[T] {

  /**
   * Inserts the specified element into the queue based on its priority.
   *
   * @param item The element to add.
   */
  def enqueue(item: T): Unit

  /**
   * Retrieves and removes the highest priority element from the queue.
   *
   * @return `Some(element)` if the queue is not empty, otherwise `None`.
   */
  def dequeueNext(): Option[T]

  /**
   * Retrieves, but does not remove, the highest priority element from the queue.
   *
   * @return `Some(element)` if the queue is not empty, otherwise `None`.
   */
  def peekNext(): Option[T]

  /**
   * Returns an iterator over all elements in the queue that have a priority
   * equal to or lower than the specified item.
   *
   * @param item The element used as the starting bound.
   * @return An iterator containing the matching elements.
   */
  def iteratorFrom(item: T): Iterator[T]

  /**
   * Removes a single instance of the specified element from the queue, if it is present.
   *
   * @param item The element to be removed.
   * @return `true` if the queue contained the specified element, `false` otherwise.
   */
  def removeElement(item: T): Boolean

  /**
   * Applies a given disposal action to all remaining elements in the queue,
   * and then completely clears the queue.
   *
   * @param onDispose The action to execute for each element before clearing.
   */
  def clearAndDispose(onDispose: T => Unit): Unit
}