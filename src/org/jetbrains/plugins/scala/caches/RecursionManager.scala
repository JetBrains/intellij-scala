package org.jetbrains.plugins.scala.caches

import java.util
import java.util.concurrent.ConcurrentMap

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Computable
import com.intellij.util.containers.{ContainerUtil, SoftHashMap, SoftKeySoftValueHashMap}
import gnu.trove.{THashMap, THashSet}

/**
  * Nikolay.Tropin
  * 26-Jan-17
  */

/**
  * This class mimics [[com.intellij.openapi.util.RecursionManager]]
  * Memoization and some, so it is removed.
  *
  * It going to be used in macros to avoid unnecessary Computable.compute method call and thus reduce stack size.
  *
  * (it can easily be 300 calls in the stack, so gain is significant).
  * */
object RecursionManager {
  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.caches.RecursionManager")

  private val ourStack: ThreadLocal[CalculationStack] = new ThreadLocal[CalculationStack]() {
    override protected def initialValue: CalculationStack = new CalculationStack
  }
  private var ourAssertOnPrevention: Boolean = false

  class RecursionGuard[Data >: Null <: AnyRef, Result >: Null <: AnyRef] private (id: String) {

    //see also org.jetbrains.plugins.scala.macroAnnotations.CachedMacroUtil.doPreventingRecursion
    def doPreventingRecursion(data: Data, computable: Computable[Result]): Result = {
      if (isReentrant(data)) return null

      val realKey = createKey(data)

      val (sizeBefore, sizeAfter) = beforeComputation(realKey)

      try {
        computable.compute()
      }
      finally {
        afterComputation(realKey, sizeBefore, sizeAfter)
      }
    }

    def isReentrant(data: Data): Boolean = {
      val stack = ourStack.get()
      val realKey = createKey(data, myCallEquals = true)
      if (stack.checkReentrancy(realKey)) {
        if (ourAssertOnPrevention) throw new AssertionError("Endless recursion prevention occurred")
        true
      }
      else false
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
      stack.checkDepth("4")
    }

    def currentStackContains(data: Data): Boolean = {
      val map = ourStack.get.progressMap
      val keys = map.keySet().iterator()
      while (keys.hasNext) {
        val next = keys.next()
        if (next.guardId == id && data == next.userObject) return true
      }
      false
    }
  }

  object RecursionGuard {
    private val guards: ConcurrentMap[String, RecursionGuard[_, _]] =
      ContainerUtil.newConcurrentMap[String, RecursionGuard[_, _]]()

    def apply[Data >: Null <: AnyRef, Result >: Null <: AnyRef](id: String): RecursionGuard[Data, Result] = {
      guards.get(id) match {
        case null =>
          val newGuard = new RecursionGuard[Data, Result](id)
          val race = guards.putIfAbsent(id, newGuard)

          if (race != null) race.asInstanceOf[RecursionGuard[Data, Result]]
          else newGuard
        case v => v.asInstanceOf[RecursionGuard[Data, Result]]
      }
    }
  }

  class MyKey[Data >: Null <: AnyRef](val guardId: String, val userObject: Data, val myCallEquals: Boolean) {
    // remember user object hashCode to ensure our internal maps consistency
    override val hashCode: Int = guardId.hashCode * 31 + userObject.hashCode

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
    var reentrancyCount: Int = 0
    var depth: Int = 0
    val progressMap: util.LinkedHashMap[MyKey[_], Integer] = new util.LinkedHashMap[MyKey[_], Integer]
    val key2ReentrancyDuringItsCalculation: THashMap[MyKey[_], MyKey[_]] = new THashMap[MyKey[_], MyKey[_]]
    val intermediateCache: SoftHashMap[MyKey[_], SoftKeySoftValueHashMap[MyKey[_], AnyRef]] = new SoftHashMap[MyKey[_], SoftKeySoftValueHashMap[MyKey[_], AnyRef]]
    var enters: Int = 0
    var exits: Int = 0
    
    def checkReentrancy(realKey: MyKey[_]): Boolean = {
      if (progressMap.containsKey(realKey)) {
        return true
      }
      false
    }

    def beforeComputation(realKey: MyKey[_]) {
      enters += 1
      if (progressMap.isEmpty) assert(reentrancyCount == 0, "Non-zero stamp with empty stack: " + reentrancyCount)
      checkDepth("1")
      val sizeBefore: Int = progressMap.size
      progressMap.put(realKey, reentrancyCount)
      depth += 1
      checkDepth("2")
      val sizeAfter: Int = progressMap.size
      if (sizeAfter != sizeBefore + 1) LOG.error("Key doesn't lead to the map size increase: " + sizeBefore + " " + sizeAfter + " " + realKey.userObject)
    }

    def afterComputation(realKey: MyKey[_], sizeBefore: Int, sizeAfter: Int) {
      exits += 1
      if (sizeAfter != progressMap.size) LOG.error("Map size changed: " + progressMap.size + " " + sizeAfter + " " + realKey.userObject)
      if (depth != progressMap.size) LOG.error("Inconsistent depth after computation; depth=" + depth + "; map=" + progressMap)
      val value: Integer = progressMap.remove(realKey)
      depth -= 1
      key2ReentrancyDuringItsCalculation.remove(realKey)
      if (depth == 0) {
        intermediateCache.clear()
        if (!key2ReentrancyDuringItsCalculation.isEmpty)
          LOG.error("non-empty key2ReentrancyDuringItsCalculation: " + new util.HashMap[MyKey[_], MyKey[_]](key2ReentrancyDuringItsCalculation))
      }
      if (sizeBefore != progressMap.size) LOG.error("Map size doesn't decrease: " + progressMap.size + " " + sizeBefore + " " + realKey.userObject)
      reentrancyCount = value
      checkZero
    }

    def prohibitResultCaching(realKey: MyKey[_]): util.Set[MyKey[_]] = {
      reentrancyCount += 1
      if (!checkZero) throw new AssertionError("zero1")
      val loop: util.Set[MyKey[_]] = new THashSet[MyKey[_]]
      var inLoop: Boolean = false
      import scala.collection.JavaConversions._
      for (entry <- progressMap.entrySet) {
        if (inLoop) {
          entry.setValue(reentrancyCount)
          loop.add(entry.getKey)
        }
        else if (entry.getKey == realKey) inLoop = true
      }
      if (!checkZero) throw new AssertionError("zero2")
      loop
    }

    def checkDepth(s: String) {
      val oldDepth: Int = depth
      if (oldDepth != progressMap.size) {
        depth = progressMap.size
        throw new AssertionError("_Inconsistent depth " + s + "; depth=" + oldDepth + "; enters=" + enters + "; exits=" + exits + "; map=" + progressMap)
      }
    }

    def checkZero: Boolean = {
      if (!progressMap.isEmpty && new Integer(0) != progressMap.get(progressMap.keySet.iterator.next)) {
        LOG.error("Prisoner Zero has escaped: " + progressMap + "; value=" + progressMap.get(progressMap.keySet.iterator.next))
        return false
      }
      true
    }
  }
}

 