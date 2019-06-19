package org.jetbrains.plugins.scala.lang.psi.implicits

import java.util.concurrent.{Callable, ConcurrentHashMap, ConcurrentMap, TimeUnit}

import com.intellij.openapi.application.{ApplicationManager, ReadAction}
import com.intellij.openapi.project.{Project, ProjectManager, ProjectManagerListener}
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.LowMemoryWatcher.LowMemoryWatcherType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.plugins.scala.caches.CachesUtil.Timestamped
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

import scala.collection.mutable
import scala.util.Try

object ImplicitConversionCache {

  type ImplicitConversionMap = collection.Map[GlobalImplicitConversion, ImplicitConversionData]

  private val implicitConversionDataCache: ConcurrentMap[GlobalSearchScope, Timestamped[ImplicitConversionMap]] =
    new ConcurrentHashMap()

  registerCleanups()

  def getOrScheduleUpdate(elementScope: ElementScope): ImplicitConversionMap = {
    val currentCount = currentTopLevelModCount(elementScope.project)

    implicitConversionDataCache.get(elementScope.scope) match {
      case Timestamped(data, modCount) =>
        if (currentCount != modCount) {
          scheduleUpdateFor(elementScope)
        }
        data
      case null =>
        Try(
          scheduleUpdateFor(elementScope)
            .blockingGet(defaultTimeoutMs, TimeUnit.MILLISECONDS)
            .data
        ).getOrElse(Map.empty)
    }
  }

  private val defaultTimeoutMs: Int =
    if (ApplicationManager.getApplication.isUnitTestMode) 10000
    else 100

  private def registerCleanups(): Unit = {
    LowMemoryWatcher.register(() => implicitConversionDataCache.clear(), LowMemoryWatcherType.ALWAYS)

    val connection: MessageBusConnection = ApplicationManager.getApplication.getMessageBus.connect
    connection.subscribe(ProjectManager.TOPIC,  new ProjectManagerListener {
      override def projectClosed(project: Project): Unit = {
        implicitConversionDataCache.clear()
      }
    })
  }

  private def scheduleUpdateFor(implicit elementScope: ElementScope): CancellablePromise[Timestamped[ImplicitConversionMap]] = {
    val callback: Callable[Timestamped[ImplicitConversionMap]] = () => {
      val currentCount = currentTopLevelModCount(elementScope.project)
      implicitConversionDataCache
        .computeIfAbsent(elementScope.scope, scope => Timestamped(computeImplicitConversionMap(elementScope), currentCount))
    }
    ReadAction.nonBlocking(callback)
      .inSmartMode(elementScope.project)
      .submit(AppExecutorUtil.getAppExecutorService)
  }

  private def currentTopLevelModCount(project: Project) =
    ScalaPsiManager.instance(project).TopLevelModificationTracker.getModificationCount

  private def computeImplicitConversionMap(elementScope: ElementScope): ImplicitConversionMap = {
    val resultMap = mutable.Map.empty[GlobalImplicitConversion, ImplicitConversionData]
    for {
      globalConversion  <- GlobalImplicitConversion.collectIn(elementScope)
      data              <- globalConversion.toImplicitConversionData
    } {
      resultMap += (globalConversion -> data)
    }
    resultMap
  }
}