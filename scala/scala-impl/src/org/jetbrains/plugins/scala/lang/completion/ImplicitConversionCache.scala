package org.jetbrains.plugins.scala.lang.completion

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
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitConversionData
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaStubsUtil.getClassInheritors

import scala.collection.mutable
import scala.util.Try

private case class GlobalImplicitConversion(containingObject: ScObject, function: ScFunction) {
  def toImplicitConversionData: Option[ImplicitConversionData] = {
    val node = TypeDefinitionMembers.getSignatures(containingObject).forName(function.name).findNode(function)
    val substitutor = node.map(_.info.substitutor)
    substitutor.flatMap {
      ImplicitConversionData(function, _)
    }
  }
}

private object ImplicitConversionCache {

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
    import ScalaIndexKeys._

    ImplicitConversionKey.allElements(elementScope.scope)(elementScope.projectContext)
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
    allImplicitConversions(elementScope).flatMap { member =>
      val conversion = member match {
        case f: ScFunction => Some(f)
        case c: ScClass    => c.getSyntheticImplicitMethod
        case _             => None
      }
      val inheritorObjects = mutable.Map.empty[ScTypeDefinition, Seq[ScObject]]

      def inheritorObjectsFor(td: ScTypeDefinition): Seq[ScObject] =
        inheritorObjects.getOrElseUpdate(td,
          getClassInheritors(td, td.resolveScope).collect {
            case o: ScObject if o.isStatic && o.isInheritor(td, deep = false) => o
          })

      def containingObjects(function: ScFunction): Seq[ScObject] = function.containingClass match {
        case o: ScObject if o.isStatic                      => Seq(o)
        case td: ScTypeDefinition if !td.isEffectivelyFinal => inheritorObjectsFor(td)
        case _                                              => Seq.empty
      }

      for {
        function <- conversion.toSeq
        obj <- containingObjects(function)
        if obj.qualifiedName != "scala.Predef"
      } yield GlobalImplicitConversion(obj, function)
    }
  }

}