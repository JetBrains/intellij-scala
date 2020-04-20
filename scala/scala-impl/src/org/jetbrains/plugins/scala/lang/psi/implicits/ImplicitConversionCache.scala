package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import java.util.concurrent.{Callable, ConcurrentHashMap, TimeUnit}

import com.intellij.openapi.application.{ApplicationManager, ReadAction}
import com.intellij.openapi.project.{Project, ProjectManager, ProjectManagerListener}
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.LowMemoryWatcher.LowMemoryWatcherType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.plugins.scala.caches.CachesUtil.Timestamped
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

import scala.util.Try

object ImplicitConversionCache {

  import GlobalImplicitConversion._

  private val implicitConversionDataCache = new ConcurrentHashMap[GlobalSearchScope, Timestamped[ImplicitConversionMap]]

  private val defaultTimeoutMs: Int =
    if (ApplicationManager.getApplication.isUnitTestMode) 10000
    else 100

  registerCleanups()

  def getOrScheduleUpdate(scope: GlobalSearchScope)
                         (implicit project: Project): ImplicitConversionMap = {
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

  private def registerCleanups(): Unit = {
    val listener = new ProjectManagerListener with Runnable {

      override def run(): Unit = {
        implicitConversionDataCache.clear()
      }

      override def projectClosed(project: Project): Unit = run()
    }

    LowMemoryWatcher.register(listener, LowMemoryWatcherType.ALWAYS)

    ApplicationManager.getApplication
      .getMessageBus
      .connect
      .subscribe(ProjectManager.TOPIC, listener)
  }

  private def scheduleUpdateFor(scope: GlobalSearchScope)
                               (implicit project: Project): CancellablePromise[Timestamped[ImplicitConversionMap]] = {
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

  private def currentTopLevelModCount(implicit project: Project) =
    ScalaPsiManager.instance.TopLevelModificationTracker.getModificationCount
}