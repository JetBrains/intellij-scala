package org.jetbrains.plugins.scala.caches

import java.util
import java.util.Objects
import java.util.concurrent.ConcurrentMap

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.RecursionGuard.StackStamp
import com.intellij.openapi.util.{RecursionManager => PlatformRM}
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.extensions.NullSafe

/**
  * Nikolay.Tropin
  * 26-Jan-17
  */

/**
  * This class mimics [[com.intellij.openapi.util.RecursionManager]]
  *
  * It is used in macros to avoid unnecessary Computable.compute method call and thus reduce stack size.
  *
  * (it can easily be 300 calls in the stack, so gain is significant).
  * */
object RecursionManager {
  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.caches.RecursionManager")

  private val ourStack: ThreadLocal[CalculationStack] = new ThreadLocal[CalculationStack]() {
    override protected def initialValue: CalculationStack = new CalculationStack
  }

  /** see [[com.intellij.openapi.util.RecursionGuard#markStack]]*/
  def markStack(): StackStamp = new StackStamp {
    private val stamp = ourStack.get.stamp()
    private val platformStamp = PlatformRM.markStack()

    override def mayCacheNow: Boolean = {
      val stack = ourStack.get
      !stack.isStampWithinRecursion(stamp) && !stack.isDirty && platformStamp.mayCacheNow()
    }
  }

  //invalidates all current StackStamps
  def prohibitCaching(): Unit = {
    ourStack.get.prohibitCaching()
  }

  class RecursionGuard[Data >: Null <: AnyRef, LocalCacheValue] private (id: String) {

    //see also org.jetbrains.plugins.scala.macroAnnotations.CachedMacroUtil.doPreventingRecursion
    def doPreventingRecursion[Result](data: Data)(computable: => Result): Option[Result] =
      if (checkReentrancy(data)) None
      else {
        val realKey = createKey(data)
        val (sizeBefore, sizeAfter, minDepthBefore, localCacheBefore) = beforeComputation(realKey)

        try Some(computable)
        finally afterComputation(realKey, sizeBefore, sizeAfter, minDepthBefore, localCacheBefore)
      }

    def getFromLocalCache(data: Data): LocalCacheValue = {
      val key = createKey(data)
      val result = ourStack.get().localCache.get(key).orNull.asInstanceOf[LocalCacheValue]
      result
    }

    def cacheInLocalCache(data: Data, value: LocalCacheValue): Unit = {
      val stack = ourStack.get()
      val key = createKey(data, myCallEquals = true)
      if (stack.currentStackHasRecursion)
        stack.localCache += key -> value
    }

    def checkReentrancy(data: Data): Boolean = {
      val stack = ourStack.get()
      val realKey = createKey(data, myCallEquals = true)
      stack.checkReentrancy(realKey)
    }

    def createKey(data: Data, myCallEquals: Boolean = false) = new MyKey[Data](id, data, myCallEquals)

    def beforeComputation(realKey: MyKey[Data]): (Int, Int, Int, Map[MyKey[_], Any]) = {
      val stack = ourStack.get()
      val sizeBefore: Int = stack.progressMap.size
      val minDepthBefore = stack.beforeComputation(realKey)
      val sizeAfter: Int = stack.progressMap.size
      (sizeBefore, sizeAfter, minDepthBefore, stack.localCache)
    }

    def afterComputation(realKey: MyKey[Data], sizeBefore: Int, sizeAfter: Int, minDepthBefore: Int, localCacheBefore: Map[MyKey[_], Any]): Unit = {
      val stack = ourStack.get()
      try stack.afterComputation(realKey, sizeBefore, sizeAfter, minDepthBefore, localCacheBefore)
      catch {
        case e: Throwable =>
          //noinspection ThrowFromFinallyBlock
          throw new RuntimeException("Throwable in afterComputation", e)
      }
      stack.checkDepth("after computation")
    }
  }

  object RecursionGuard {
    private val guards: ConcurrentMap[String, RecursionGuard[_, _]] =
      ContainerUtil.newConcurrentMap[String, RecursionGuard[_, _]]()

    def apply[Data >: Null <: AnyRef, LocalCacheValue](id: String): RecursionGuard[Data, LocalCacheValue] =
      guards.computeIfAbsent(id, new RecursionGuard[Data, LocalCacheValue](_))
        .asInstanceOf[RecursionGuard[Data, LocalCacheValue]]
  }

  class MyKey[Data >: Null <: AnyRef](val guardId: String, val userObject: Data, val myCallEquals: Boolean) {
    // remember user object hashCode to ensure our internal maps consistency
    override val hashCode: Int = Objects.hash(guardId, userObject)

    override def equals(obj: Any): Boolean = {
      obj match {
        case key: MyKey[Data] if key.guardId != guardId => false
        case key: MyKey[Data] if key.userObject eq userObject => true
        case key: MyKey[Data] if key.myCallEquals || myCallEquals => key.userObject == userObject
        case _ => false
      }
    }
  }

  private class CalculationStack {
    // this marks the beginning depth of a recursive calculation
    // all calls that have a greater depth should not be cached
    // the call that has an equal value can be cached because it is
    // the call that started the recursion
    private[this] var minStackDepthInRecursion: Int = Int.MaxValue
    private[this] var depth: Int = 0
    private[this] var enters: Int = 0
    private[this] var exits: Int = 0

    private[this] var _isDirty: Boolean = false

    private[RecursionManager] var localCache = Map.empty[MyKey[_], Any]
    private[RecursionManager] val progressMap = new util.LinkedHashMap[MyKey[_], Integer]

    private[RecursionManager] def checkReentrancy(realKey: MyKey[_]): Boolean = {
      Option(progressMap.get(realKey)) match {
        case Some(stackDepthOfPrevEnter) =>
          minStackDepthInRecursion = math.min(minStackDepthInRecursion, stackDepthOfPrevEnter)
          true
        case None =>
          false
      }
    }

    private[RecursionManager] def stamp(): Int = depth
    private[RecursionManager] def currentStackHasRecursion: Boolean = minStackDepthInRecursion < Int.MaxValue
    private[RecursionManager] def isStampWithinRecursion(stamp: Int): Boolean =
      stamp > minStackDepthInRecursion

    private[RecursionManager] def isDirty: Boolean = _isDirty

    private[RecursionManager] def prohibitCaching(): Unit = {
      if (depth > 0) {
        _isDirty = true
      }
    }

    private[RecursionManager] def beforeComputation(realKey: MyKey[_]): Int = {
      enters += 1
      if (progressMap.isEmpty) {
        assert(minStackDepthInRecursion == Int.MaxValue,
          "Empty stack should have no recursive calculation depth, but is " + minStackDepthInRecursion)
      }
      checkDepth("before computation 1")
      val sizeBefore: Int = progressMap.size
      depth += 1
      progressMap.put(realKey, depth)
      checkDepth("before computation 2")
      val sizeAfter: Int = progressMap.size
      if (sizeAfter != sizeBefore + 1) {
        LOG.error("Key doesn't lead to the map size increase: " + sizeBefore + " " + sizeAfter + " " + realKey.userObject)
      }
      val minDepthBefore = minStackDepthInRecursion
      minStackDepthInRecursion = Int.MaxValue
      minDepthBefore
    }

    private[RecursionManager] def afterComputation(realKey: MyKey[_],
                                                   sizeBefore: Int,
                                                   sizeAfter: Int,
                                                   minDepthBefore: Int,
                                                   localCacheBefore: Map[MyKey[_], Any]): Unit = {
      exits += 1
      if (sizeAfter != progressMap.size) {
        LOG.error("Map size changed: " + progressMap.size + " " + sizeAfter + " " + realKey.userObject)
      }
      if (depth != progressMap.size) {
        LOG.error("Inconsistent depth after computation; depth=" + depth + "; map=" + progressMap)
      }
      val recordedStackSize: Int = {
        val recordedStackSizeInteger = progressMap.remove(realKey)
        assert(recordedStackSizeInteger != null)
        recordedStackSizeInteger
      }

      assert(recordedStackSize == sizeAfter, "Expected recorded stack to be equal to current size")

      depth -= 1
      if (sizeBefore != progressMap.size) {
        LOG.error("Map size doesn't decrease: " + progressMap.size + " " + sizeBefore + " " + realKey.userObject)
      }
      if (depth == 0) {
        _isDirty = false
      }

      if (depth <= minStackDepthInRecursion) {
        localCache = localCacheBefore
      }
      minStackDepthInRecursion =
        math.min(minStackDepthInRecursion, minDepthBefore)
      if (depth < minStackDepthInRecursion) {
        minStackDepthInRecursion = Int.MaxValue
      }

      checkZero()
    }

    private[RecursionManager] def checkDepth(s: String): Unit = {
      val oldDepth: Int = depth
      if (oldDepth != progressMap.size) {
        depth = progressMap.size
        throw new AssertionError("_Inconsistent depth " + s + "; depth=" + oldDepth + "; enters=" + enters + "; exits=" + exits + "; map=" + progressMap)
      }
    }

    private[RecursionManager] def checkZero(): Boolean = {
      if (progressMap.size == 1 && progressMap.values().iterator().next() != 1) {
        val message = "Recursion stack: first inserted key should have a depth of 1"
        LOG.error(message + progressMap + "; value=" + progressMap.values().iterator().next())
        return false
      }
      true
    }
  }
}

 