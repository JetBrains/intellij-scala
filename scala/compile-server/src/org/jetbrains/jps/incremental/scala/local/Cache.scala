package org.jetbrains.jps.incremental.scala.local

import java.lang.ref.SoftReference
import java.util.concurrent.locks.{Lock, ReadWriteLock, ReentrantReadWriteLock}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.control.NonFatal

/**
 * A cache where reads of values are not blocked by computations of unrelated values. This cache should be used in
 * cases where computing a value is computationally expensive and we don't want to block unrelated reads for the
 * duration of the computation.
 *
 * For example, this cache should be used when caching scalac compiler instances. They are very expensive to
 * instantiate, and we don't want to block the compilation of other modules which want to use a different scalac
 * instance.
 */
class Cache[K, V](capacity: Int) {
  private val lock: ReadWriteLock = new ReentrantReadWriteLock()
  private val readLock: Lock = lock.readLock()
  private val writeLock: Lock = lock.writeLock()

  /**
   * For each key in the map, its associated value can be in one of three states:
   *   1. Not present in the cache (the key cannot be found in the map).
   *   1. Computing (the key will be associated with a promise of the value).
   *   1. Already computed and represented as a soft reference (can be garbage collected).
   */
  private val underlying: java.util.LinkedHashMap[K, Either[Promise[V], SoftReference[V]]] =
    new java.util.LinkedHashMap[K, Either[Promise[V], SoftReference[V]]](capacity, 0.75f, true) {
      override protected def removeEldestEntry(eldest: java.util.Map.Entry[K, Either[Promise[V], SoftReference[V]]]): Boolean = {
        eldest.getValue.fold(_ => false, _ => size() > capacity)
      }
    }

  def getOrUpdate(key: K)(thunk: () => V): V = {
    // With a read lock, check the current state of the value associated with the key we're fetching.
    val cached = withLock(readLock)(Option(underlying.get(key)))

    cached match {
      case Some(Left(promise)) =>
        // The value is being computed. We need to wait for it.
        try Await.result(promise.future, Duration.Inf)
        catch {
          case NonFatal(_) =>
            // Another thread failed to compute the value, we need to compute it ourselves.
            compute(key)(thunk)
        }

      case Some(Right(reference)) =>
        // The value has already been computed. We need to dereference the soft reference.
        Option(reference.get()) match {
          case Some(result) =>
            // The value is available now.
            result
          case None =>
            // The value has been garbage collected, we need to compute it.
            compute(key)(thunk)
        }

      case None =>
        // The value has not been computed up to now.
        compute(key)(thunk)
    }
  }

  private def compute(key: K)(thunk: () => V): V = {
    // Create a promise, then inspect the result.
    createPromise(key) match {
      case State.PromiseCreated(promise) =>
        // We managed to create the promise. That means we need to compute the result.
        // Important, the computation is done outside of the write lock.
        try {
          val result = thunk()
          promise.success(result)
          val reference = new SoftReference(result)
          withLock(writeLock) {
            underlying.put(key, Right(reference))
          }
          // If everything went well, we have computed the value, completed the promise and also replaced the
          // promise with a soft reference to the value.
          result
        } catch {
          case NonFatal(t) =>
            // The computation failed. Notify other readers through the promise that they need to compute
            // the value for themselves.
            promise.failure(t)
            throw t
        }

      case State.Computing(promise) =>
        // The value is being computed. We need to wait for it.
        try Await.result(promise.future, Duration.Inf)
        catch {
          case NonFatal(_) =>
            // Another thread failed to compute the value, we need to compute it ourselves.
            compute(key)(thunk)
        }

      case State.Computed(result) =>
        // The value has already been computed, just return it.
        result
    }
  }

  private def createPromise(key: K): State = withLock(writeLock) {
    // Here, we're holding the write lock. We want to be as quick as possible to exit the critical region.
    // Instead of computing the value with the write lock held, which will block all reads from the cache,
    // we want to signal to the other threads that we intend to compute the value and that they should wait for us.

    // We need to read the state of the value again.
    val cached = Option(underlying.get(key))

    // Creates a promise and puts it in the map. Other readers will await on the promise, to get the value
    // that we're computing.
    def create(): State.PromiseCreated = {
      val promise = Promise[V]()
      underlying.put(key, Left(promise))
      State.PromiseCreated(promise)
    }

    cached match {
      case Some(Left(promise)) =>
        // Someone else already created a promise. We don't need to create a new one.
        State.Computing(promise)
      case Some(Right(reference)) =>
        // Someone else has already computed the value. We need to inspect the reference.
        Option(reference.get()) match {
          case Some(result) =>
            // The reference is valid. Just return the result.
            State.Computed(result)
          case None =>
            // The reference is no longer valid, create a promise.
            create()
        }
      case None =>
        // The value has not been computed yet, create a promise.
        create()
    }
  }

  private def withLock[A](lock: Lock)(thunk: => A): A = {
    lock.lock()
    try thunk
    finally lock.unlock()
  }

  private sealed trait State
  private object State {
    case class PromiseCreated(promise: Promise[V]) extends State
    case class Computing(promise: Promise[V]) extends State
    case class Computed(result: V) extends State
  }
}
