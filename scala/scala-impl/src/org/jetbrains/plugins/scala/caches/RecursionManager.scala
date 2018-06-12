package org.jetbrains.plugins.scala.caches

import java.util
import java.util.Objects
import java.util.concurrent.ConcurrentMap

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.RecursionGuard.StackStamp
import com.intellij.util.containers.ContainerUtil

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

  private val platformGuard = com.intellij.openapi.util.RecursionManager.createGuard("scala.recursion.manager")

  private val ourStack: ThreadLocal[CalculationStack] = new ThreadLocal[CalculationStack]() {
    override protected def initialValue: CalculationStack = new CalculationStack
  }

  /** see [[com.intellij.openapi.util.RecursionGuard#markStack]]*/
  def markStack(): StackStamp = new StackStamp {
    private val stamp = ourStack.get.getReentrancyCount
    private val platformStamp = platformGuard.markStack()

    override def mayCacheNow: Boolean = {
      stamp == ourStack.get.getReentrancyCount && platformStamp.mayCacheNow()
    }
  }

  class RecursionGuard[Data >: Null <: AnyRef, Result >: Null <: AnyRef] private (id: String) {

    //see also org.jetbrains.plugins.scala.macroAnnotations.CachedMacroUtil.doPreventingRecursion
    def doPreventingRecursion(data: Data, computable: Computable[Result]): Result = {
      if (checkReentrancy(data)) return null

      val realKey = createKey(data)

      val (sizeBefore, sizeAfter) = beforeComputation(realKey)

      try {
        computable.compute()
      }
      finally {
        afterComputation(realKey, sizeBefore, sizeAfter)
      }
    }

    def checkReentrancy(data: Data): Boolean = {
      val stack = ourStack.get()
      val realKey = createKey(data, myCallEquals = true)
      stack.checkReentrancy(realKey)
    }

    def createKey(data: Data, myCallEquals: Boolean = false) = new MyKey[Data](id, data, myCallEquals)

    def beforeComputation(realKey: MyKey[Data]): (Int, Int) = {
      val stack = ourStack.get()
      val sizeBefore: Int = stack.progressMap.size
      stack.beforeComputation(realKey)
      val sizeAfter: Int = stack.progressMap.size
      (sizeBefore, sizeAfter)
    }

    def afterComputation(realKey: MyKey[Data], sizeBefore: Int, sizeAfter: Int): Unit = {
      val stack = ourStack.get()
      try stack.afterComputation(realKey, sizeBefore, sizeAfter)
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

    def apply[Data >: Null <: AnyRef, Result >: Null <: AnyRef](id: String): RecursionGuard[Data, Result] =
      guards.computeIfAbsent(id, new RecursionGuard[Data, Result](_))
        .asInstanceOf[RecursionGuard[Data, Result]]
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
    private[this] var reentrancyCount: Int = 0
    private[this] var depth: Int = 0
    private[this] var enters: Int = 0
    private[this] var exits: Int = 0

    private[RecursionManager] val progressMap = new util.LinkedHashMap[MyKey[_], Integer]

    private[RecursionManager] def checkReentrancy(realKey: MyKey[_]): Boolean = {
      val isReentrant = progressMap.containsKey(realKey)

      if (isReentrant)
        reentrancyCount += 1

      isReentrant
    }

    private[RecursionManager] def getReentrancyCount: Int = reentrancyCount

    private[RecursionManager] def beforeComputation(realKey: MyKey[_]) {
      enters += 1
      if (progressMap.isEmpty) {
        assert(reentrancyCount == 0, "Non-zero stamp with empty stack: " + reentrancyCount)
      }
      checkDepth("before computation 1")
      val sizeBefore: Int = progressMap.size
      progressMap.put(realKey, reentrancyCount)
      depth += 1
      checkDepth("before computation 2")
      val sizeAfter: Int = progressMap.size
      if (sizeAfter != sizeBefore + 1) {
        LOG.error("Key doesn't lead to the map size increase: " + sizeBefore + " " + sizeAfter + " " + realKey.userObject)
      }
    }

    private[RecursionManager] def afterComputation(realKey: MyKey[_], sizeBefore: Int, sizeAfter: Int) {
      exits += 1
      if (sizeAfter != progressMap.size) {
        LOG.error("Map size changed: " + progressMap.size + " " + sizeAfter + " " + realKey.userObject)
      }
      if (depth != progressMap.size) {
        LOG.error("Inconsistent depth after computation; depth=" + depth + "; map=" + progressMap)
      }
      val reentrancyCountBefore: Integer = progressMap.remove(realKey)
      depth -= 1
      if (sizeBefore != progressMap.size) {
        LOG.error("Map size doesn't decrease: " + progressMap.size + " " + sizeBefore + " " + realKey.userObject)
      }
      reentrancyCount = reentrancyCountBefore
      checkZero
    }

    private[RecursionManager] def checkDepth(s: String) {
      val oldDepth: Int = depth
      if (oldDepth != progressMap.size) {
        depth = progressMap.size
        throw new AssertionError("_Inconsistent depth " + s + "; depth=" + oldDepth + "; enters=" + enters + "; exits=" + exits + "; map=" + progressMap)
      }
    }

    private[RecursionManager] def checkZero: Boolean = {
      if (!progressMap.isEmpty && new Integer(0) != progressMap.get(progressMap.keySet.iterator.next)) {
        val message = "Recursion stack: first inserted key should have zero reentrancyCount "
        LOG.error(message + progressMap + "; value=" + progressMap.get(progressMap.keySet.iterator.next))
        return false
      }
      true
    }
  }
}

 