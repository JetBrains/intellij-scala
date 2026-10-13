package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.openapi.Disposable

/**
 * A component that accumulates elements of type `R` in order to generate or 
 * provide elements of type `O`.
 *
 * The elements of type `O` yielded by this provider typically depend on the 
 * elements of type `R` that have been accumulated. Additionally, this trait 
 * supports returning a previously yielded `O` element back to the provider 
 * via [[restore]], which is useful for object pooling or retry mechanisms.
 *
 * As this extends `Disposable`, consumers must ensure that any underlying 
 * resources held by the implementation are properly cleaned up.
 *
 * @tparam I the type of elements to be accumulated (the input)
 * @tparam O the type of elements to be provided (the output)
 */
trait Provider[I, O] extends Disposable:

  /**
   * Accumulates a new element into the provider.
   *
   * The accumulated elements serve as the underlying data or state used 
   * to generate the provided elements of type `O`.
   *
   * @param v the element to accumulate
   */
  def add(v: I): Unit

  /**
   * Retrieves the next available element of type `O`, if one can be produced.
   *
   * The generated element may be derived from the accumulated `I` elements 
   * or retrieved from the pool of previously restored `O` elements.
   *
   * @return `Some` containing the next element, or `None` if the provider 
   *         cannot yield any more elements at this time.
   */
  def next(): Option[O]

  /**
   * Returns a previously retrieved element of type `O` back to the provider.
   *
   * This is used to re-accumulate or recycle an element (e.g., if processing 
   * failed and it needs to be retried). 
   * Restored elements should generally become available again for subsequent 
   * calls to [[next()]].
   *
   * @param x the element to restore to the provider
   */
  def restore(x: O): Unit