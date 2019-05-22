package org.jetbrains.plugins.scala.lang.psi.implicits

import java.util.concurrent.{Callable, ConcurrentHashMap, ConcurrentMap, TimeUnit}

import com.intellij.openapi.application.{ApplicationManager, ReadAction}
import com.intellij.openapi.project.{Project, ProjectManager, ProjectManagerListener}
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.LowMemoryWatcher.LowMemoryWatcherType
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.plugins.scala.caches.CachesUtil.Timestamped
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ImplicitConversionIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaStubsUtil.inheritorOrThisObjects

import scala.collection.mutable
import scala.util.Try

object ImplicitConversionCache {

  case class GlobalImplicitConversion(containingObject: ScObject, function: ScFunction) {
    def toImplicitConversionData: Option[ImplicitConversionData] = {
      val node = TypeDefinitionMembers.getSignatures(containingObject).forName(function.name).findNode(function)
      val substitutor = node.map(_.info.substitutor)
      substitutor.flatMap {
        ImplicitConversionData(function, _)
      }
    }
  }

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

  private[this] def allImplicitConversions(elementScope: ElementScope)(): Iterable[ScMember] = {
    ImplicitConversionIndex.allElements(elementScope.scope)(elementScope.projectContext)
  }

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
      globalConversion  <- collectImplicitConversions(elementScope)
      data              <- globalConversion.toImplicitConversionData
    } {
      resultMap += (globalConversion -> data)
    }
    resultMap
  }

  private def collectImplicitConversions(elementScope: ElementScope): Iterable[GlobalImplicitConversion] = {
    val inheritorObjectsCache = mutable.Map.empty[PsiClass, Seq[ScObject]]

    def containingObjects(function: ScFunction): Seq[ScObject] =
      Option(function.containingClass)
        .map(cClass => inheritorObjectsCache.getOrElseUpdate(cClass, inheritorOrThisObjects(cClass)))
        .getOrElse(Seq.empty)

    allImplicitConversions(elementScope).flatMap { member =>
      val conversion = member match {
        case f: ScFunction => Some(f)
        case c: ScClass    => c.getSyntheticImplicitMethod
        case _             => None
      }

      for {
        function <- conversion.toSeq
        obj <- containingObjects(function)
        if obj.qualifiedName != "scala.Predef"
      } yield GlobalImplicitConversion(obj, function)
    }
  }

}