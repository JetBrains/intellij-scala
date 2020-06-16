package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.LowMemoryWatcher.LowMemoryWatcherType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.plugins.scala.caches.CachesUtil.Timestamped
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

import scala.util.Try

@Service
final class ImplicitConversionCache(implicit val project: Project) extends Disposable {

  import GlobalImplicitConversion._

  private val implicitConversionDataCache = new ConcurrentHashMap[GlobalSearchScope, Timestamped[ImplicitConversionMap]]

  LowMemoryWatcher.register(() => implicitConversionDataCache.clear(), LowMemoryWatcherType.ALWAYS, this)

  private val defaultTimeoutMs: Int =
    if (ApplicationManager.getApplication.isUnitTestMode) 10000
    else 100

  def getOrScheduleUpdate(scope: GlobalSearchScope): ImplicitConversionMap = {
    val currentCount = currentTopLevelModCount

    implicitConversionDataCache.get(scope) match {
      case null =>
        Try(
          scheduleUpdateFor(scope)
            .blockingGet(defaultTimeoutMs, TimeUnit.MILLISECONDS)
            .data
        ).getOrElse(Map.empty)
      case Timestamped(data, modCount) =>
        if (currentCount != modCount) {
          scheduleUpdateFor(scope)
        }
        data
    }
  }

  override def dispose(): Unit = implicitConversionDataCache.clear()

  private def scheduleUpdateFor(scope: GlobalSearchScope): CancellablePromise[Timestamped[ImplicitConversionMap]] = {
    val callback: Callable[Timestamped[ImplicitConversionMap]] = () => {
      val currentCount = currentTopLevelModCount
      implicitConversionDataCache.computeIfAbsent(
        scope,
        scope => Timestamped(computeImplicitConversionMap(scope), currentCount)
      )
    }
    ReadAction.nonBlocking(callback)
      .inSmartMode(project)
      .submit(AppExecutorUtil.getAppExecutorService)
  }

  private def currentTopLevelModCount =
    ScalaPsiManager.instance.TopLevelModificationTracker.getModificationCount
}

object ImplicitConversionCache {
  def apply(project: Project): ImplicitConversionCache = project.getService(classOf[ImplicitConversionCache])
}
