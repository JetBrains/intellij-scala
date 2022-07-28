package org.jetbrains.plugins.scala
package caches


import com.intellij.openapi.project.Project
import com.intellij.openapi.util._
import com.intellij.psi._
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.caches.ProjectUserDataHolder._
import org.jetbrains.plugins.scala.caches.stats.{CacheCapabilities, CacheTracker}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiManager}

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import scala.annotation.unused
import scala.util.control.ControlThrowable

object CachesUtil {
  /**
   * Do not delete this type alias, it is used by [[org.jetbrains.plugins.scala.macroAnnotations.CachedWithRecursionGuard]]
    *
    * @see [[CachesUtil.getOrCreateKey]] for more info
   */
  type CachedMap[Data, Result] = CachedValue[ConcurrentMap[Data, Result]]
  type CachedRef[Result] = CachedValue[AtomicReference[Result]]
  private val keys = new ConcurrentHashMap[String, Key[_]]()

  /**
   * IMPORTANT:
   * Cached annotations (CachedWithRecursionGuard, CachedMappedWithRecursionGuard, and CachedInUserData)
   * rely on this method, even though it shows that it is unused
   *
   * If you change this method in any way, please make sure it's consistent with the annotations
   *
   * Do not use this method directly. You should use annotations instead
   */
  def getOrCreateKey[T](id: String): Key[T] = {
    keys.get(id) match {
      case null =>
        val newKey = Key.create[T](id)
        val race = keys.putIfAbsent(id, newKey)

        if (race != null) race.asInstanceOf[Key[T]]
        else newKey
      case v => v.asInstanceOf[Key[T]]
    }
  }

  case class ProbablyRecursionException[Data](elem: PsiElement,
                                              data: Data,
                                              key: Key[_],
                                              set: Set[ScFunction]) extends ControlThrowable

  @unused("used in caching macro annotations")
  def getOrCreateCachedMap[Dom: ProjectUserDataHolder, Data, Result](elem: Dom,
                                                                     key: Key[CachedMap[Data, Result]],
                                                                     cacheTypeId: String,
                                                                     cacheTypeName: String,
                                                                     dependencyItem: () => Object): ConcurrentMap[Data, Result] = {
    import CacheCapabilties._
    val cachedValue = elem.getUserData(key) match {
      case null =>
        val manager = CachedValuesManager.getManager(elem.getProject)
        val provider = new CachedValueProvider[ConcurrentMap[Data, Result]] {
          override def compute(): CachedValueProvider.Result[ConcurrentMap[Data, Result]] =
            new CachedValueProvider.Result(new ConcurrentHashMap(), dependencyItem())
        }
        val newValue = CacheTracker.track(cacheTypeId, cacheTypeName) {
          manager.createCachedValue(provider, false)
        }
        elem.putUserDataIfAbsent(key, newValue)
      case d => d
    }
    cachedValue.getValue
  }

  @unused("used in caching macro annotations")
  def getOrCreateCachedRef[Dom: ProjectUserDataHolder, Result >: Null](elem: Dom,
                                                                       key: Key[CachedRef[Result]],
                                                                       cacheTypeId: String,
                                                                       cacheTypeName: String,
                                                                       dependencyItem: () => Object): AtomicReference[Result] = {
    import CacheCapabilties._
    val cachedValue = elem.getUserData(key) match {
      case null =>
        val manager = CachedValuesManager.getManager(elem.getProject)
        val provider = new CachedValueProvider[AtomicReference[Result]] {
          override def compute(): CachedValueProvider.Result[AtomicReference[Result]] =
            new CachedValueProvider.Result(new AtomicReference[Result](), dependencyItem())
        }
        val newValue = CacheTracker.track(cacheTypeId, cacheTypeName) {
          manager.createCachedValue(provider, false)
        }
        elem.putUserDataIfAbsent(key, newValue)
      case d => d
    }
    cachedValue.getValue
  }

  @unused("used in CachedWithRecursionGuard")
  def handleRecursiveCall[Data, Result](e: PsiElement,
                                        data: Data,
                                        key: Key[_],
                                        defaultValue: => Result): Result = {
    val function = PsiTreeUtil.getContextOfType(e, true, classOf[ScFunction])
    if (function == null || function.isProbablyRecursive) {
      defaultValue
    } else {
      function.isProbablyRecursive = true
      throw ProbablyRecursionException(e, data, key, Set(function))
    }
  }

  //Tuple2 class doesn't have half-specialized variants, so (T, Long) almost always have boxed long inside
  case class Timestamped[@specialized(Boolean, Int, AnyRef) T](data: T, modCount: Long)

  def fileModCount(file: PsiFile): Long = fileModTracker(file).getModificationCount

  def fileModTracker(file: PsiFile): ModificationTracker =
    if (file == null)
      ModificationTracker.NEVER_CHANGED
    else
      new ModificationTracker {
        private val topLevel = scalaTopLevelModTracker(file.getProject)
        override def getModificationCount: Long = topLevel.getModificationCount + file.getModificationStamp
    }

  /**
   * see [[org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl.getContextModificationStamp]]
   */
  def fileContextModTracker(file: ScalaFileImpl): ModificationTracker =
    if (file == null)
      ModificationTracker.NEVER_CHANGED
    else
      new ModificationTracker {
        private val topLevel = scalaTopLevelModTracker(file.getProject)
        override def getModificationCount: Long = topLevel.getModificationCount + file.getContextModificationStamp
      }

  def scalaTopLevelModTracker(project: Project): ModificationTracker =
    ScalaPsiManager.instance(project).TopLevelModificationTracker


  object CacheCapabilties {
    implicit def concurrentMapCacheCapabilities[Data, Result]: CacheCapabilities[CachedValue[ConcurrentMap[Data, Result]]] =
      new CacheCapabilities[CachedValue[ConcurrentMap[Data, Result]]] {
        private def realCache(cache: CacheType) = cache.getUpToDateOrNull.nullSafe.map(_.get())

        override def cachedEntitiesCount(cache: CacheType): Int = realCache(cache).fold(0)(_.size())

        override def clear(cache: CacheType): Unit = realCache(cache).foreach(_.clear())
      }

    implicit def atomicRefCacheCapabilities[Data, Result >: Null]: CacheCapabilities[CachedValue[AtomicReference[Result]]] =
      new CacheCapabilities[CachedValue[AtomicReference[Result]]] {
        private def realCache(cache: CacheType) = cache.getUpToDateOrNull.nullSafe.map(_.get())

        override def cachedEntitiesCount(cache: CacheType): Int = realCache(cache).fold(0)(c => if (c.get() == null) 0 else 1)

        override def clear(cache: CacheType): Unit = realCache(cache).foreach(_.set(null))
      }

    implicit def timestampedMapCacheCapabilities[M >: Null <: ConcurrentMap[_, _]]: CacheCapabilities[AtomicReference[Timestamped[M]]] =
      new CacheCapabilities[AtomicReference[Timestamped[M]]] {
        override def cachedEntitiesCount(cache: CacheType): Int = cache.get().data.nullSafe.fold(0)(_.size())

        override def clear(cache: CacheType): Unit = cache.set(Timestamped(null.asInstanceOf[M], -1))
      }

    implicit def timestampedSingleValueCacheCapabilities[T]: CacheCapabilities[AtomicReference[Timestamped[T]]] =
      new CacheCapabilities[AtomicReference[Timestamped[T]]] {
        override def cachedEntitiesCount(cache: CacheType): Int = if (cache.get().data == null) 0 else 1

        override def clear(cache: CacheType): Unit = cache.set(Timestamped(null.asInstanceOf[T], -1))
      }
  }
}
