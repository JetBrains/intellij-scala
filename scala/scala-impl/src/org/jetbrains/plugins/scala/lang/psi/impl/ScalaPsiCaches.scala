package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.{LowMemoryWatcher, ModificationTracker, SimpleModificationTracker}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{CommonClassNames, PsiClass, PsiElement, PsiType, PsiTypeParameter}
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.{ApiStatus, Nullable, TestOnly}
import org.jetbrains.plugins.scala.caches.stats.{CacheCapabilities, CacheTracker}
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, CleanupScheduler, ModTracker, ScalaModificationTracker, ValueWrapper, cachedInUserData, cachedWithoutModificationCount}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.idToName
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiCaches._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.StableNodes.{Map => PMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.TermNodes.{Map => SMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.TypeNodes.{Map => TMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.{StableNodes, TermNodes, TypeNodes}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollectorCache
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, ParameterizedType}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq

/**
 * The project-wide caches of the type system and resolve (signatures, implicits, ...), the
 * [[TopLevelModificationTracker]], and the cleanups that invalidate them on changes.
 *
 * Unlike the class and package lookups of [[ScalaPsiManager]], they don't use indexes, so they belong with the PSI core.
 */
@ApiStatus.Internal
final class ScalaPsiCaches(implicit project: Project) extends Disposable {

  /** Fired on any change of Scala PSI */
  private[psi] val clearCacheOnChange = new CleanupScheduler

  /** Fired on a change of Scala PSI outside a block with a stable type, or a change of other PSI */
  private[psi] val clearCacheOnTopLevelChange = new CleanupScheduler

  /** Fired on a change of the project roots */
  private[psi] val clearCacheOnRootsChange = new CleanupScheduler

  val collectImplicitObjectsCache: ConcurrentMap[(ScType, GlobalSearchScope, Boolean), Seq[ScType]] =
    new ConcurrentHashMap[(ScType, GlobalSearchScope, Boolean), Seq[ScType]]()

  val implicitCollectorCache: ImplicitCollectorCache =
    CacheTracker.alwaysTrack("ScalaPsiCaches.implicitCollectorCache", "ScalaPsiCaches.implicitCollectorCache") {
      new ImplicitCollectorCache(project)
    }

  private def dontCacheCompound = ScalaProjectSettings.getInstance(project).isDontCacheCompoundTypes

  def getStableSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): PMap =
    if (dontCacheCompound) StableNodes.build(tp, compoundTypeThisType)
    else getStableSignaturesCached(tp, compoundTypeThisType)

  private val getStableSignaturesCached =
    cachedWithoutModificationCount(
      "getStableSignaturesCached",
      ValueWrapper.SofterReference,
      clearCacheOnChange,
      (tp: ScCompoundType, compoundTypeThisType: Option[ScType]) => StableNodes.build(tp, compoundTypeThisType)
    )

  def getTypes(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): TMap =
    if (dontCacheCompound) TypeNodes.build(tp, compoundTypeThisType)
    else getTypesCached(tp, compoundTypeThisType)

  private val getTypesCached =
    cachedWithoutModificationCount(
      "getTypesCached",
      ValueWrapper.SofterReference,
      clearCacheOnChange,
      (tp: ScCompoundType, compoundTypeThisType: Option[ScType]) => TypeNodes.build(tp, compoundTypeThisType)
    )

  def getSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): SMap = {
    if (dontCacheCompound) return TermNodes.build(tp, compoundTypeThisType)
    getSignaturesCached(tp, compoundTypeThisType)
  }

  private val getSignaturesCached =
    cachedWithoutModificationCount(
      "getSignaturesCached",
      ValueWrapper.SofterReference,
      clearCacheOnChange,
      (tp: ScCompoundType, compoundTypeThisType: Option[ScType]) => TermNodes.build(tp, compoundTypeThisType)
    )

  def getStableSignatures(tp: ScAndType): PMap =
    if (dontCacheCompound) StableNodes.build(tp)
    else getIntersectionStableSignaturesCached(tp)

  private val getIntersectionStableSignaturesCached =
    cachedWithoutModificationCount(
      "getIntersectionStableSignaturesCached",
      ValueWrapper.SofterReference,
      clearCacheOnChange,
      (tp: ScAndType) => StableNodes.build(tp)
    )

  def getTypes(tp: ScAndType): TMap =
    if (dontCacheCompound) TypeNodes.build(tp)
    else getIntersectionTypesCached(tp)

  private val getIntersectionTypesCached =
    cachedWithoutModificationCount(
      "getIntersectionTypesCached",
      ValueWrapper.SofterReference,
      clearCacheOnChange,
      (tp: ScAndType) => TypeNodes.build(tp)
    )

  def getSignatures(tp: ScAndType): SMap = {
    if (dontCacheCompound) return TermNodes.build(tp)
    getIntersectionSignaturesCached(tp)
  }

  private val getIntersectionSignaturesCached =
    cachedWithoutModificationCount(
      "getIntersectionSignaturesCached",
      ValueWrapper.SofterReference,
      clearCacheOnChange,
      (tp: ScAndType) => TermNodes.build(tp)
    )

  def simpleAliasProjectionCached(projection: ScProjectionType): ScType = _simpleAliasProjectionCached(projection)

  private val _simpleAliasProjectionCached =
    cachedWithoutModificationCount(
      "simpleAliasProjectionCached",
      ValueWrapper.SofterReference,
      clearCacheOnChange,
      (projection: ScProjectionType) => ScProjectionType.simpleAliasProjection(projection)
    )

  def javaPsiTypeParameterUpperType(typeParameter: PsiTypeParameter): ScType = _javaPsiTypeParameterUpperType(
    typeParameter
  )

  private val _javaPsiTypeParameterUpperType =
    cachedWithoutModificationCount(
      "javaPsiTypeParameterUpperType",
      ValueWrapper.SofterReference,
      clearCacheOnChange,
      (typeParameter: PsiTypeParameter) => {
        val types = ArraySeq.unsafeWrapArray(typeParameter.getExtendsListTypes ++ typeParameter.getImplementsListTypes)
        types match {
          case Seq()                                                           => Any
          case Seq(one) if one.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) =>
            // Generics with upper bound java.lang.Object should have the upper bound scala.Any in Scala
            Any
          case _ => andType(types)
        }
      }
    )

  private def andType(psiTypes: Seq[PsiType]): ScType =
    new ProjectContext(project).typeSystem.andType(psiTypes.map(_.toScType()))

  private def clearCaches(): Unit = {
    new ProjectContext(project).typeSystem.clearCache()
    if (!project.isDisposed) {
      ScMatchType.clearReductionCache(project)
      ParameterizedType.substitutorCache(project).clear()
    }
    PropertyMethods.clearCache()
    collectImplicitObjectsCache.clear()
    implicitCollectorCache.clear()
    idToName.clear()
  }

  private def clearOnChange(): Unit = {
    clearCacheOnChange.fireCleanup()
    clearCaches()
  }

  private def clearOnLowMemory(): Unit = clearAllCaches()

  private def clearOnTopLevelChange(): Unit = {
    clearOnChange()
    clearCacheOnTopLevelChange.fireCleanup()
  }

  /** Called by the project model listener on the backend */
  def clearOnRootsChange(): Unit = {
    clearOnTopLevelChange()
    clearCacheOnRootsChange.fireCleanup()
  }

  val TopLevelModificationTracker: SimpleModificationTracker = new ScalaModificationTracker("TopLevelModificationTracker")

  def clearOnScalaElementChange(psiElement: PsiElement): Unit = {
    clearOnChange()
    updateScalaModificationCount(psiElement)
  }

  def clearOnNonScalaChange(): Unit = {
    clearOnTopLevelChange()
    TopLevelModificationTracker.incModificationCount()
  }

  @tailrec
  private def updateScalaModificationCount(@Nullable element: PsiElement): Unit = element match {
    case null =>
      TopLevelModificationTracker.incModificationCount()
      clearOnTopLevelChange()
    case owner: ScExpression if BlockModificationTracker.hasStableType(owner) =>
      BlockModificationTracker.incrementLocalCounter(owner)
    case _ =>
      updateScalaModificationCount(element.getContext)
  }

  val rootManager: ModificationTracker = ProjectRootManager.getInstance(project)

  sealed abstract class SignatureCaches[T <: Signature](val nodes: MixinNodes[T]) {

    private val forLibraryMap: ConcurrentMap[(PsiClass, Boolean), nodes.Map] = ContainerUtil.createConcurrentWeakMap()
    private val forTopLevelMap: ConcurrentMap[(PsiClass, Boolean), nodes.Map] = ContainerUtil.createConcurrentWeakMap()

    clearCacheOnRootsChange.subscribe(() => forLibraryMap.clear())
    clearCacheOnTopLevelChange.subscribe(() => forTopLevelMap.clear())

    private def forLibraryClasses(clazz: PsiClass, withSupers: Boolean): nodes.Map =
      forLibraryMap.computeIfAbsent((clazz, withSupers), { case (cls, supers) => nodes.build(cls, supers) })

    private def forTopLevelClasses(clazz: PsiClass, withSupers: Boolean): nodes.Map =
      forTopLevelMap.computeIfAbsent((clazz, withSupers), { case (cls, supers) => nodes.build(cls, supers) })

    def cachedMap(clazz: PsiClass, withSupers: Boolean): nodes.Map =
      ModTracker.libraryAware(clazz) match {
        case `rootManager`               => forLibraryClasses(clazz, withSupers)
        case TopLevelModificationTracker => forTopLevelClasses(clazz, withSupers)
        case tracker =>
          cachedInUserData("cachedMap", clazz, tracker, (clazz, nodes, withSupers)) {
            nodes.build(clazz, withSupers)
          }
      }
  }

  object TermNodesCache extends SignatureCaches(TermNodes)
  object StableNodesCache extends SignatureCaches(StableNodes)
  object TypeNodesCache extends SignatureCaches(TypeNodes)

  def clearAllCaches(): Unit = invokeLater {
    doClearAllCaches()
  }

  def clearAllCachesAndWait(): Unit = invokeAndWait {
    doClearAllCaches()
  }

  /**
   * Should be invoked only in the [[dispose]] method, to run synchronously
   * and without checking if the project is disposed (it still isn't, it is being disposed).
   *
   * @note Same as [[doClearAllCaches]] except without a `Project#isDisposed` check.
   */
  private def clearAllCachesOnDispose(): Unit = {
    clearOnRootsChange()
    TopLevelModificationTracker.incModificationCount()
  }

  @RequiresEdt()
  private def doClearAllCaches(): Unit =
    if (!project.isDisposed) {
      clearOnRootsChange()
      TopLevelModificationTracker.incModificationCount()
    }

  locally {
    LowMemoryWatcher.register(
      () => {
        LOG.debug("Clear caches on low memory")
        clearOnLowMemory()
      },
      this
    )
  }

  override def dispose(): Unit = {
    clearAllCachesOnDispose()
  }

  @TestOnly
  def clearCachesOnChange(): Unit =
    clearOnChange()
}

object ScalaPsiCaches {
  private val LOG = Logger.getInstance(classOf[ScalaPsiCaches])

  def instance(
    implicit
    ctx: ProjectContext,
    d:   DummyImplicit
  ): ScalaPsiCaches =
    instance(ctx.getProject)

  def instance(project: Project): ScalaPsiCaches =
    project.getService(classOf[ScalaPsiCaches])

  implicit val ImplicitCollectorCacheCapabilities: CacheCapabilities[ImplicitCollectorCache] =
    new CacheCapabilities[ImplicitCollectorCache] {
      override def cachedEntitiesCount(cache: CacheType): Int = cache.size()
      override def clear(cache:               CacheType): Unit = cache.clear()
    }
}
