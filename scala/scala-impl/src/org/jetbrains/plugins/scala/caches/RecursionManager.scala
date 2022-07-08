package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.RecursionGuard.StackStamp
import com.intellij.openapi.util.{RecursionManager => PlatformRM}
import org.jetbrains.plugins.scala.util.HashBuilder._
import org.jetbrains.plugins.scala.util.UnloadableThreadLocal

import java.util
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import scala.annotation.unused
import scala.jdk.CollectionConverters._

/**
  * This class mimics [[com.intellij.openapi.util.RecursionManager]]
  *
  * It is used in macros to avoid unnecessary Computable.compute method call and thus reduce stack size.
  *
  * (it can easily be 300 calls in the stack, so gain is significant).
  * */
object RecursionManager {
  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.caches.RecursionManager")
  private type LocalCacheMap = Map[MyKey[_], (Any, Int)]

  private val ourStack: UnloadableThreadLocal[CalculationStack] = UnloadableThreadLocal(new CalculationStack)

  /** see [[com.intellij.openapi.util.RecursionGuard#markStack]]*/
  def markStack(): StackStamp = new StackStamp {
    private val stamp = ourStack.value.stamp()
    private val platformStamp = PlatformRM.markStack()

    override def mayCacheNow: Boolean = {
      val stack = ourStack.value
      !stack.isStampWithinRecursion(stamp) && !stack.isDirty && platformStamp.mayCacheNow()
    }
  }

  //invalidates all current StackStamps
  def prohibitCaching(): Unit = {
    ourStack.value.prohibitCaching()
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
      val stack = ourStack.value
      val key = createKey(data)
      val result = stack.localCache.get(key) match {
        case Some((value, stackDepthOfLocallyCachedRecord)) =>
          stack.minStackDepthInRecursion = math.min(stack.minStackDepthInRecursion, stackDepthOfLocallyCachedRecord)
          value
        case None =>
          null
      }
      result.asInstanceOf[LocalCacheValue]
    }

    @unused("used by CachedMacroUtil")
    def cacheInLocalCache(data: Data, value: LocalCacheValue): Unit = {
      val stack = ourStack.value
      val key = createKey(data, myCallEquals = true)
      if (stack.currentStackHasRecursion)
        stack.localCache += key -> (value -> stack.minStackDepthInRecursion)
    }

    def checkReentrancy(data: Data): Boolean = {
      val stack = ourStack.value
      val realKey = createKey(data, myCallEquals = true)
      stack.checkReentrancy(realKey)
    }

    def createKey(data: Data, myCallEquals: Boolean = false) = new MyKey[Data](id, data, myCallEquals)

    def beforeComputation(realKey: MyKey[Data]): (Int, Int, Int, LocalCacheMap) = {
      val stack = ourStack.value
      val sizeBefore: Int = stack.progressMap.size
      val minDepthBefore = stack.beforeComputation(realKey)
      val sizeAfter: Int = stack.progressMap.size
      (sizeBefore, sizeAfter, minDepthBefore, stack.localCache)
    }

    def afterComputation(realKey: MyKey[Data], sizeBefore: Int, sizeAfter: Int, minDepthBefore: Int, localCacheBefore: LocalCacheMap): Unit = {
      val stack = ourStack.value
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
      new ConcurrentHashMap[String, RecursionGuard[_, _]]()

    def apply[Data >: Null <: AnyRef, LocalCacheValue](id: String): RecursionGuard[Data, LocalCacheValue] =
      guards.computeIfAbsent(id, new RecursionGuard[Data, LocalCacheValue](_))
        .asInstanceOf[RecursionGuard[Data, LocalCacheValue]]

    def allGuardNames: Set[String] = guards.keySet().iterator().asScala.toSet
  }

  class MyKey[Data >: Null <: AnyRef](val guardId: String, val userObject: Data, val myCallEquals: Boolean) {
    // remember user object hashCode to ensure our internal maps consistency
    override val hashCode: Int = guardId #+ userObject

    override def equals(obj: Any): Boolean = {
      obj match {
        case key: MyKey[_] if key.guardId != guardId => false
        case key: MyKey[_] if key.userObject eq userObject => true
        case key: MyKey[_] if key.myCallEquals || myCallEquals => key.userObject == userObject
        case _ => false
      }
    }
  }

  private class CalculationStack {
    // this marks the beginning depth of a recursive calculation
    // all calls that have a greater depth should not be cached
    // the call that has an equal value can be cached because it is
    // the call that started the recursion
    var minStackDepthInRecursion: Int = Int.MaxValue
    private[this] var depth: Int = 0
    private[this] var enters: Int = 0
    private[this] var exits: Int = 0

    private[this] var _isDirty: Boolean = false

    // The local cache is an optimization and just prevents unnecessary recalculation.
    // It contains cached values that were created inside a recursion.
    // Normally we shouldn't cache them at all, but that would mean that they would
    // have to be recalculated every time they are requested in the same recursive call.
    // So instead we cache them here for as long as we are in that recursive call and
    // prevent calculating them incorrectly again.
    private[RecursionManager] var localCache: LocalCacheMap = Map.empty
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
                                                   localCacheBefore: LocalCacheMap): Unit = {
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

      exits += 1
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
        LOG.error(message + "\n" +
          progressMap +
          "\nvalue=" + progressMap.values().iterator().next() +
          "\nenters=" + enters +
          "\nexits=" + exits +
          "\ndepth=" + depth)
        return false
      }
      true
    }
  }
}

 